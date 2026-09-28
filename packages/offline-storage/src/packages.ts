import { parseManifest } from "abservice-installation";
import type { InstallationManifest } from "abservice-installation";
import { commit, hash, locked, optional, reject, requireValue } from "./common";
import type {
  PackageSlot,
  PackageStore,
  StoredPackage,
  StoredPackages,
} from "./index";

type Pointer = Readonly<Partial<Record<PackageSlot, string>>>;
/* Declared here so that consumers need not enable the DOM.AsyncIterable lib. */
type Listing = FileSystemDirectoryHandle & {
  values: () => AsyncIterator<FileSystemDirectoryHandle | FileSystemFileHandle>;
};
const namespace = "abservice-packages-v1";
const slots: readonly PackageSlot[] = ["active", "pending"];
const pointerName = "pointer.json";
const directory = (): Promise<FileSystemDirectoryHandle> =>
  navigator.storage
    .getDirectory()
    .then((root) => root.getDirectoryHandle(namespace, { create: true }));
const manifests = (
  root: FileSystemDirectoryHandle,
): Promise<FileSystemDirectoryHandle> =>
  root.getDirectoryHandle("manifests", { create: true });
/* Paths never interpret the version: slashes, dots and Japanese stay data. */
const filename = (packageVersion: string): Promise<string> =>
  hash(new Blob([packageVersion]), new AbortController().signal);
const parseJson = (text: string): unknown => {
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return undefined;
  }
};
const serialize = (manifest: InstallationManifest): string =>
  JSON.stringify(manifest);
/* The stored bytes are trusted only after the same strict parse as the distribution response. */
const decoded = (text: string): InstallationManifest | undefined => {
  const parsed = parseManifest(parseJson(text));
  return parsed.kind === "manifest" ? parsed.manifest : undefined;
};
const pointerOf = (active?: string, pending?: string): Pointer => ({
  ...(active === undefined ? {} : { active }),
  ...(pending === undefined ? {} : { pending }),
});
const isPointer = (value: unknown): value is Pointer =>
  typeof value === "object" &&
  value !== null &&
  (Array.isArray(value)
    ? false
    : Object.entries(value).every(
        ([key, version]) =>
          slots.some((slot) => slot === key) &&
          typeof version === "string" &&
          version !== "",
      ));
const readPointer = async (
  root: FileSystemDirectoryHandle,
): Promise<Pointer> => {
  /* A committed pointer is never empty; zero bytes is a first creation that never committed. */
  const text =
    (await optional(
      root
        .getFileHandle(pointerName)
        .then((handle) => handle.getFile())
        .then((file) => file.text()),
    )) ?? "";
  const value = text === "" ? {} : parseJson(text);
  return isPointer(value) ? value : reject("corrupt");
};
/* One file holds both slots, so a single close commits or keeps both. */
const writePointer = async (
  root: FileSystemDirectoryHandle,
  pointer: Pointer,
  signal: AbortSignal,
): Promise<void> =>
  commit(
    await root.getFileHandle(pointerName, { create: true }),
    JSON.stringify(pointer),
    signal,
  );
const storedText = async (
  folder: FileSystemDirectoryHandle,
  packageVersion: string,
): Promise<string | undefined> =>
  optional(
    folder
      .getFileHandle(await filename(packageVersion))
      .then((handle) => handle.getFile())
      .then((file) => file.text()),
  );
const readManifest = async (
  root: FileSystemDirectoryHandle,
  packageVersion: string,
): Promise<InstallationManifest> => {
  const text =
    (await storedText(await manifests(root), packageVersion)) ??
    reject("missing");
  const manifest = decoded(text);
  return manifest?.packageVersion === packageVersion
    ? manifest
    : reject("corrupt");
};
const stage = async (
  manifest: InstallationManifest,
  signal: AbortSignal,
): Promise<void> => {
  const root = await directory();
  const pointer = await readPointer(root);
  const folder = await manifests(root);
  const version = manifest.packageVersion;
  const text = serialize(manifest);
  const existing = await storedText(folder, version);
  /* A referenced generation keeps its verified content; a same-version rewrite would change it silently. */
  const conflicting =
    slots.some((slot) => pointer[slot] === version) &&
    existing !== undefined &&
    existing !== text &&
    decoded(existing)?.packageVersion === version;
  requireValue(conflicting ? false : true, "conflict");
  /* The manifest is committed before the pointer names it; an interrupted stage leaves only an unreferenced file. */
  await (existing === text
    ? Promise.resolve()
    : commit(
        await folder.getFileHandle(await filename(version), { create: true }),
        text,
        signal,
      ));
  await writePointer(root, pointerOf(pointer.active, version), signal);
};
const promote = async (signal: AbortSignal): Promise<void> => {
  const root = await directory();
  const pointer = await readPointer(root);
  const pending = pointer.pending ?? reject("missing");
  await readManifest(root, pending);
  await writePointer(root, pointerOf(pending), signal);
};
const discardPending = async (signal: AbortSignal): Promise<void> => {
  const root = await directory();
  const pointer = await readPointer(root);
  await (pointer.pending === undefined
    ? Promise.resolve()
    : writePointer(root, pointerOf(pointer.active), signal));
};
const entries = async <T>(
  iterator: AsyncIterator<T>,
  collected: readonly T[] = [],
): Promise<readonly T[]> => {
  const next = await iterator.next();
  return next.done === true
    ? collected
    : entries(iterator, [...collected, next.value]);
};
const describe = async (
  handle: FileSystemFileHandle,
  pointer: Pointer,
): Promise<StoredPackage | undefined> => {
  const manifest = decoded(await (await handle.getFile()).text());
  /* A file whose name does not match its version is not the generation it claims to be. */
  return manifest !== undefined &&
    (await filename(manifest.packageVersion)) === handle.name
    ? {
        packageVersion: manifest.packageVersion,
        slots: slots.filter(
          (slot) => pointer[slot] === manifest.packageVersion,
        ),
      }
    : undefined;
};
const list = async (): Promise<StoredPackages> => {
  const root = await directory();
  const pointer = await readPointer(root);
  const folder = (await manifests(root)) as Listing;
  const handles = await entries(folder.values());
  const described = await Promise.all(
    handles.flatMap((handle) =>
      handle.kind === "file" ? [describe(handle, pointer)] : [],
    ),
  );
  const packages = described.filter((item) => item !== undefined);
  return { packages, unreadable: described.length - packages.length };
};
export const buildPackageStore = (): PackageStore => ({
  stage: (input, signal) => {
    const parsed = parseManifest(input);
    return parsed.kind === "manifest"
      ? locked(namespace, signal, () => stage(parsed.manifest, signal))
      : Promise.resolve({ kind: "error", error: "invalid-manifest" });
  },
  read: (slot, signal) =>
    locked(namespace, signal, async () => {
      const root = await directory();
      const version = (await readPointer(root))[slot];
      return version === undefined ? undefined : readManifest(root, version);
    }),
  promote: (signal) => locked(namespace, signal, () => promote(signal)),
  discardPending: (signal) =>
    locked(namespace, signal, () => discardPending(signal)),
  list: (signal) => locked(namespace, signal, list),
});
