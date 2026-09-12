import { assessReadiness, parseManifest } from "abservice-installation";
import type {
  InstallationManifest,
  LocalEnvironment,
} from "abservice-installation";
import type {
  AssetStore,
  StorageError,
  StorageResult,
  StoredInventory,
} from "./index";

type Asset = InstallationManifest["assets"][number];
type Observation = LocalEnvironment["inventory"][number];
const namespace = "abservice-assets-v1";
class Failure extends Error {
  constructor(readonly code: StorageError) {
    super(code);
  }
}
const reject = (code: StorageError): never => {
  throw new Failure(code);
};
const requireValue = (condition: boolean, code: StorageError): void =>
  condition ? undefined : reject(code);
const checkAbort = (signal: AbortSignal): void => {
  requireValue(signal.aborted ? false : true, "aborted");
};
const errorCode = (error: unknown): StorageError =>
  error instanceof Failure
    ? error.code
    : error instanceof DOMException && error.name === "QuotaExceededError"
      ? "quota-exceeded"
      : error instanceof DOMException && error.name === "AbortError"
        ? "aborted"
        : "storage-unavailable";
const capture = async <T>(
  action: () => Promise<T>,
): Promise<StorageResult<T>> =>
  action().then(
    (value) => ({ kind: "ok", value }),
    (error: unknown) => ({ kind: "error", error: errorCode(error) }),
  );
const hash = async (blob: Blob, signal: AbortSignal): Promise<string> => {
  checkAbort(signal);
  const buffer = await blob.arrayBuffer();
  checkAbort(signal);
  const digest = await crypto.subtle.digest("SHA-256", buffer);
  checkAbort(signal);
  return Array.from(new Uint8Array(digest), (byte) =>
    byte.toString(16).padStart(2, "0"),
  ).join("");
};
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
  return root
    .getFileHandle(name)
    .then((handle) => handle.getFile())
    .catch((error: unknown) =>
      error instanceof DOMException && error.name === "NotFoundError"
        ? undefined
        : Promise.reject(
            error instanceof Error ? error : new Failure("storage-unavailable"),
          ),
    );
};
const inventory = async (
  manifest: InstallationManifest,
  signal: AbortSignal,
): Promise<StoredInventory> => {
  const root = await directory();
  /* Hash one asset at a time: Web Crypto takes a complete buffer; parallel hashes multiply peak memory. */
  const observations = await manifest.assets.reduce<
    Promise<readonly Observation[]>
  >(async (previous, asset) => {
    const entries = await previous;
    checkAbort(signal);
    const file = await storedFile(root, asset, signal);
    return file === undefined
      ? entries
      : [...entries, await observe(asset, file, signal)];
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
  const file = await root.getFileHandle(name, { create: true });
  checkAbort(signal);
  const stream = await file.createWritable();
  /* close is the commit boundary. An abort after close starts cannot promise rollback. */
  try {
    checkAbort(signal);
    await stream.write(blob);
    checkAbort(signal);
    await stream.close();
  } catch (error) {
    await stream.abort().catch(() => undefined);
    throw error;
  }
  /* Once committed, verify the stored bytes even when the caller subsequently cancels. */
  await verify(asset, await file.getFile(), new AbortController().signal);
};
const locked = <T>(
  signal: AbortSignal,
  action: () => Promise<T>,
): Promise<StorageResult<T>> =>
  capture(async () => {
    checkAbort(signal);
    /* Browser capabilities can be absent despite the DOM declaration. */
    /* eslint-disable @typescript-eslint/no-unnecessary-condition -- DOM types assume capabilities that older browsers may lack. */
    requireValue(
      typeof navigator.storage?.getDirectory === "function" &&
        typeof navigator.locks?.request === "function",
      "storage-unavailable",
    );
    /* A common lock also protects inspect from concurrent writes by another adapter/tab. */
    return navigator.locks
      .request(namespace, { signal }, () => {
        checkAbort(signal);
        return action();
      })
      .catch((error: unknown) =>
        signal.aborted
          ? reject("aborted")
          : Promise.reject(
              error instanceof Error
                ? error
                : new Failure("storage-unavailable"),
            ),
      );
  });
const store = (manifest: InstallationManifest): AssetStore => {
  const assetFor = (assetId: string): Asset =>
    supported(
      manifest.assets.find((asset) => asset.assetId === assetId) ??
        reject("unknown-asset"),
    );
  return {
    save: (assetId, blob, signal) =>
      locked(signal, () => write(assetFor(assetId), blob, signal)),
    read: (assetId, signal) =>
      locked(signal, async () => {
        const asset = assetFor(assetId);
        const file =
          (await storedFile(await directory(), asset, signal)) ??
          reject("missing");
        await verify(asset, file, signal);
        return file.slice(0, file.size, asset.mediaType);
      }),
    inspect: (signal) => locked(signal, () => inventory(manifest, signal)),
    assess: (appVersion, signal) =>
      locked(signal, async () =>
        assessReadiness(manifest, {
          ...(await inventory(manifest, signal)),
          appVersion,
          appShellAvailable: false,
        }),
      ),
  };
};
export const buildStore = (
  input: InstallationManifest,
): StorageResult<AssetStore> => {
  const parsed = parseManifest(input);
  return parsed.kind === "manifest"
    ? { kind: "ok", value: store(parsed.manifest) }
    : { kind: "error", error: "invalid-manifest" };
};
