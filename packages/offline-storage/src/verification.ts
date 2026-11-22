import { commit, optional, requireValue } from "./common";

/* Separate from asset bodies so collection counts and existing content keys stay stable. */
export const verificationDirectory = (): Promise<FileSystemDirectoryHandle> =>
  navigator.storage
    .getDirectory()
    .then((root) =>
      root.getDirectoryHandle("abservice-asset-verifications-v1", {
        create: true,
      }),
    );
const stamp = (file: File): string => {
  requireValue(Number.isFinite(file.lastModified), "storage-unavailable");
  return JSON.stringify({
    version: 1,
    size: file.size,
    modified: file.lastModified,
  });
};
export const recordVerification = async (
  name: string,
  file: File | undefined,
  signal: AbortSignal,
): Promise<void> => {
  const folder = await verificationDirectory();
  await commit(
    await folder.getFileHandle(name, { create: true }),
    file === undefined ? "null" : stamp(file),
    signal,
  );
};
export const verificationState = async (
  name: string,
  file: File,
): Promise<"legacy" | "unchanged" | "changed"> => {
  const folder = await verificationDirectory();
  const record = await optional(
    folder.getFileHandle(name).then((handle) => handle.getFile()),
  );
  return record === undefined
    ? "legacy"
    : record.size <= 256 && (await record.text()) === stamp(file)
      ? "unchanged"
      : "changed";
};
