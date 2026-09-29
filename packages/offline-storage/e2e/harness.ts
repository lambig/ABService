import {
  collectAssets,
  createAssetStore,
  createPackageStore,
} from "../src/index";
import type { AssetStore } from "../src/index";
import type {
  InstallationManifestV1,
  InstallationManifestV3,
} from "abservice-installation";
import toneUrl from "./fixtures/tone.flac?url";
import distributed from "abservice-installation/fixtures/manifest-v3.example.json?raw";

const first = await fetch(toneUrl).then((response) => response.blob());
/* A distinct byte fixture tests version isolation; decoding is the player's responsibility. */
const second = new Blob([first, new Uint8Array([1])], { type: "audio/flac" });
const digest = async (blob: Blob): Promise<string> =>
  Array.from(
    new Uint8Array(
      await crypto.subtle.digest("SHA-256", await blob.arrayBuffer()),
    ),
    (byte) => byte.toString(16).padStart(2, "0"),
  ).join("");
const manifest = async (
  blob: Blob = first,
): Promise<InstallationManifestV1> => ({
  schemaVersion: 1,
  packageVersion: "storage-test",
  compatibleAppVersion: { minInclusive: [1, 0, 0], maxExclusive: [2, 0, 0] },
  albums: [
    {
      albumId: "album",
      title: "Album",
      tracks: [
        { trackId: "track", title: "Tone", audioAssetId: "../音源/one" },
      ],
    },
  ],
  presentationAssetIds: [],
  assets: [
    {
      assetId: "../音源/one",
      mediaType: "audio/flac",
      byteLength: blob.size,
      checksum: { algorithm: "sha256", value: await digest(blob) },
      required: true,
    },
  ],
});
const open = async (blob: Blob = first): Promise<AssetStore> => {
  const result = createAssetStore(await manifest(blob));
  return result.kind === "ok"
    ? result.value
    : Promise.reject(new Error(result.error));
};
/* The same example the backend response is compared with, read as the distribution JSON. */
const v3 = JSON.parse(distributed) as InstallationManifestV3;
const generation = (
  packageVersion: string,
  title = "見本の作品",
): InstallationManifestV3 => ({
  ...v3,
  packageVersion,
  albums: v3.albums.map((album) => ({ ...album, title })),
});
/* The layout below mirrors src/packages.ts so tests can damage what the store wrote. */
const packages = (): Promise<FileSystemDirectoryHandle> =>
  navigator.storage
    .getDirectory()
    .then((root) => root.getDirectoryHandle("abservice-packages-v1"));
const manifestFile = async (
  packageVersion: string,
): Promise<FileSystemFileHandle> =>
  (await packages())
    .getDirectoryHandle("manifests")
    .then(async (folder) =>
      folder.getFileHandle(await digest(new Blob([packageVersion]))),
    );
const overwrite = async (
  file: FileSystemFileHandle,
  text: string,
): Promise<void> => {
  const writer = await file.createWritable();
  await writer.write(text);
  await writer.close();
};
const tamper = async (packageVersion: string, text: string): Promise<void> =>
  overwrite(await manifestFile(packageVersion), text);
const erase = async (packageVersion: string): Promise<void> =>
  (await packages())
    .getDirectoryHandle("manifests")
    .then(async (folder) =>
      folder.removeEntry(await digest(new Blob([packageVersion]))),
    );
const tamperPointer = async (text: string): Promise<void> =>
  overwrite(await (await packages()).getFileHandle("pointer.json"), text);
/* Counts what is on disk, independently of the store's own listing. */
const stored = async (namespace: string, folder?: string): Promise<number> => {
  const root = await (
    await navigator.storage.getDirectory()
  ).getDirectoryHandle(namespace, { create: true });
  const target =
    folder === undefined
      ? root
      : await root.getDirectoryHandle(folder, { create: true });
  const iterator = (
    target as FileSystemDirectoryHandle & {
      values: () => AsyncIterator<FileSystemHandle>;
    }
  ).values();
  const count = async (seen: number): Promise<number> =>
    (await iterator.next()).done === true ? seen : count(seen + 1);
  return count(0);
};
const storedAssets = (): Promise<number> => stored("abservice-assets-v1");
const storedManifests = (): Promise<number> =>
  stored("abservice-packages-v1", "manifests");
const harness = {
  first,
  second,
  digest,
  manifest,
  open,
  createAssetStore,
  createPackageStore,
  collectAssets,
  storedAssets,
  storedManifests,
  v3,
  generation,
  tamper,
  erase,
  tamperPointer,
};
declare global {
  interface Window {
    storageHarness: typeof harness;
  }
}
/* This entry point exists only to exercise the bundled library in a real browser. */
/* eslint-disable-next-line functional/immutable-data -- The test fixture exposes the library at the browser boundary only. */
window.storageHarness = harness;
