import { assessReadiness, parseManifest } from "abservice-installation";
import type {
  InstallationManifest,
  LocalEnvironment,
} from "abservice-installation";
import {
  checkAbort,
  Failure,
  commit,
  hash,
  locked,
  optional,
  reject,
  removeExcept,
  requireValue,
} from "./common";
import type { AssetStore, StorageResult, StoredInventory } from "./index";
import {
  recordVerification,
  verificationDirectory,
  verificationState,
} from "./verification";

type Asset = InstallationManifest["assets"][number];
type Observation = Extract<
  LocalEnvironment["inventory"][number],
  { checksum: unknown }
>;
const namespace = "abservice-assets-v1";
const supported = (asset: Asset): Asset => {
  requireValue(asset.checksum.algorithm === "sha256", "unsupported-checksum");
  requireValue(
    /^[a-f0-9]{64}$/.test(asset.checksum.value),
    "unsupported-checksum",
  );
  return asset;
};
const filename = (asset: Asset, signal: AbortSignal): Promise<string> =>
  hash(
    new Blob([
      JSON.stringify([
        asset.assetId,
        asset.byteLength,
        asset.checksum.algorithm,
        asset.checksum.value,
      ]),
    ]),
    signal,
  );
const directory = (): Promise<FileSystemDirectoryHandle> =>
  navigator.storage
    .getDirectory()
    .then((root) => root.getDirectoryHandle(namespace, { create: true }));
const availableBytes = async (): Promise<number> => {
  const estimate = await navigator.storage.estimate();
  const quota = estimate.quota ?? NaN;
  const usage = estimate.usage ?? NaN;
  requireValue(
    [quota, usage].every((value) => Number.isSafeInteger(value) && value >= 0),
    "storage-unavailable",
  );
  return Math.max(0, quota - usage);
};
const observe = async (
  asset: Asset,
  blob: Blob,
  signal: AbortSignal,
): Promise<Observation> => ({
  assetId: asset.assetId,
  byteLength: blob.size,
  checksum: { algorithm: "sha256", value: await hash(blob, signal) },
});
const valid = (asset: Asset, observation: Observation): boolean =>
  asset.byteLength === observation.byteLength &&
  asset.checksum.value === observation.checksum.value;
const verify = async (
  asset: Asset,
  blob: Blob,
  signal: AbortSignal,
): Promise<void> => {
  requireValue(asset.byteLength === blob.size, "corrupt");
  requireValue(valid(asset, await observe(asset, blob, signal)), "corrupt");
};
const storedFile = async (
  root: FileSystemDirectoryHandle,
  asset: Asset,
  signal: AbortSignal,
): Promise<File | undefined> => {
  const name = await filename(supported(asset), signal);
  return optional(root.getFileHandle(name).then((handle) => handle.getFile()));
};
/** Observe one content-addressed asset without reading its body or estimating quota. */
export const inspectAsset = (
  asset: Asset,
  signal: AbortSignal,
): Promise<StorageResult<StoredInventory["inventory"][number]>> =>
  locked(namespace, signal, async () => {
    const file =
      (await storedFile(await directory(), asset, signal)) ?? reject("missing");
    checkAbort(signal);
    return {
      assetId: asset.assetId,
      byteLength: file.size,
      contentIdentity: asset.checksum,
    };
  });
const inventory = async (
  manifest: InstallationManifest,
  signal: AbortSignal,
): Promise<StoredInventory> => {
  const root = await directory();
  /* The content-addressed file was verified on save. Preparing another package
   * only checks presence and length; it must not read unchanged asset bodies. */
  const observations = await manifest.assets.reduce<
    Promise<StoredInventory["inventory"]>
  >(async (previous, asset) => {
    const entries = await previous;
    checkAbort(signal);
    const file = await storedFile(root, asset, signal);
    return file === undefined
      ? entries
      : [
          ...entries,
          {
            assetId: asset.assetId,
            byteLength: file.size,
            contentIdentity: asset.checksum,
          },
        ];
  }, Promise.resolve([]));
  const free = await availableBytes();
  checkAbort(signal);
  return { inventory: observations, availableBytes: free };
};
const write = async (
  asset: Asset,
  blob: Blob,
  signal: AbortSignal,
): Promise<void> => {
  await verify(asset, blob, signal);
  requireValue((await availableBytes()) >= blob.size, "quota-exceeded");
  const root = await directory();
  const name = await filename(asset, signal);
  /* A pending receipt distinguishes an interrupted new write from a legacy asset. */
  await recordVerification(name, undefined, signal);
  const file = await root.getFileHandle(name, { create: true });
  await commit(file, blob, signal);
  /* Once committed, verify the stored bytes even when the caller subsequently cancels. */
  const committed = await file.getFile();
  const completion = new AbortController().signal;
  await verify(asset, committed, completion);
  await recordVerification(name, committed, completion);
};
const verifyRead = async (
  asset: Asset,
  name: string,
  file: File,
  signal: AbortSignal,
): Promise<void> => {
  requireValue(file.size === asset.byteLength, "corrupt");
  const state = await verificationState(name, file);
  checkAbort(signal);
  /* Older content-addressed files were verified on save. Adopt their metadata
   * without downloading, reanalysing or rescanning the audio during upgrade. */
  await (state === "changed" ? verify(asset, file, signal) : Promise.resolve());
  await (state === "unchanged"
    ? Promise.resolve()
    : recordVerification(name, file, signal));
};
const store = (manifest: InstallationManifest): AssetStore => {
  const assetFor = (assetId: string): Asset =>
    supported(
      manifest.assets.find((asset) => asset.assetId === assetId) ??
        reject("unknown-asset"),
    );
  return {
    save: (assetId, blob, signal) =>
      locked(namespace, signal, () => write(assetFor(assetId), blob, signal)),
    read: (assetId, signal) =>
      locked(namespace, signal, async () => {
        const asset = assetFor(assetId);
        const root = await directory();
        const name = await filename(asset, signal);
        const file =
          (await storedFile(root, asset, signal)) ?? reject("missing");
        await verifyRead(asset, name, file, signal).catch(
          async (error: unknown) => {
            /* Only positively detected corruption is evicted, under the same asset lock.
             * The next normal preparation then fetches this content again. Cancellation
             * and unavailable storage must not evict valid bytes. */
            await (error instanceof Failure && error.code === "corrupt"
              ? root.removeEntry(name)
              : Promise.resolve());
            throw error;
          },
        );
        checkAbort(signal);
        return file.slice(0, file.size, asset.mediaType);
      }),
    inspect: (signal) =>
      locked(namespace, signal, () => inventory(manifest, signal)),
    assess: (appVersion, signal) =>
      locked(namespace, signal, async () =>
        assessReadiness(manifest, {
          ...(await inventory(manifest, signal)),
          appVersion,
          appShellAvailable: false,
        }),
      ),
  };
};
/* Content identity, not generation, decides what stays: an asset shared by the kept manifests is kept once. */
export const collectUnreferenced = (
  inputs: readonly InstallationManifest[],
  signal: AbortSignal,
): Promise<StorageResult<number>> =>
  locked(namespace, signal, async () => {
    const kept = await inputs.reduce<Promise<ReadonlySet<string>>>(
      async (previous, input) => {
        const names = await previous;
        const parsed = parseManifest(input);
        const manifest =
          parsed.kind === "manifest"
            ? parsed.manifest
            : reject("invalid-manifest");
        const added = await Promise.all(
          manifest.assets.map((asset) => filename(asset, signal)),
        );
        return new Set([...names, ...added]);
      },
      Promise.resolve(new Set<string>()),
    );
    const removed = await removeExcept(await directory(), kept, signal);
    await removeExcept(await verificationDirectory(), kept, signal);
    return removed;
  });
export const buildStore = (
  input: InstallationManifest,
): StorageResult<AssetStore> => {
  const parsed = parseManifest(input);
  return parsed.kind === "manifest"
    ? { kind: "ok", value: store(parsed.manifest) }
    : { kind: "error", error: "invalid-manifest" };
};
