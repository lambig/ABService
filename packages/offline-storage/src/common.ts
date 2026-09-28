import type { StorageError, StorageResult } from "./index";

export class Failure extends Error {
  constructor(readonly code: StorageError) {
    super(code);
  }
}
export const reject = (code: StorageError): never => {
  throw new Failure(code);
};
export const requireValue = (condition: boolean, code: StorageError): void =>
  condition ? undefined : reject(code);
export const checkAbort = (signal: AbortSignal): void => {
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
export const hash = async (
  blob: Blob,
  signal: AbortSignal,
): Promise<string> => {
  checkAbort(signal);
  const buffer = await blob.arrayBuffer();
  checkAbort(signal);
  const digest = await crypto.subtle.digest("SHA-256", buffer);
  checkAbort(signal);
  return Array.from(new Uint8Array(digest), (byte) =>
    byte.toString(16).padStart(2, "0"),
  ).join("");
};
/** Resolves an absent entry to undefined; every other failure stays a failure. */
export const optional = <T>(lookup: Promise<T>): Promise<T | undefined> =>
  lookup.catch((error: unknown) =>
    error instanceof DOMException && error.name === "NotFoundError"
      ? undefined
      : Promise.reject(
          error instanceof Error ? error : new Failure("storage-unavailable"),
        ),
  );
export const commit = async (
  file: FileSystemFileHandle,
  data: Blob | string,
  signal: AbortSignal,
): Promise<void> => {
  checkAbort(signal);
  const stream = await file.createWritable();
  /* close is the commit boundary. An abort after close starts cannot promise rollback. */
  try {
    checkAbort(signal);
    await stream.write(data);
    checkAbort(signal);
    await stream.close();
  } catch (error) {
    await stream.abort().catch(() => undefined);
    throw error;
  }
};
export const locked = <T>(
  name: string,
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
    /* A common lock also protects reads from concurrent writes by another adapter/tab. */
    return navigator.locks
      .request(name, { signal }, () => {
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
