import { createAssetStore } from "../src/index";
import type { AssetStore } from "../src/index";
import type { InstallationManifest } from "abservice-installation";
import toneUrl from "./fixtures/tone.flac?url";

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
const manifest = async (blob: Blob = first): Promise<InstallationManifest> => ({
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
const harness = { first, second, digest, manifest, open, createAssetStore };
declare global {
  interface Window {
    storageHarness: typeof harness;
  }
}
/* This entry point exists only to exercise the bundled library in a real browser. */
/* eslint-disable-next-line functional/immutable-data -- The test fixture exposes the library at the browser boundary only. */
window.storageHarness = harness;
