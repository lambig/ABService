import {
  analysisByteLimit,
  getPlaybackItems,
  parseManifest,
  parseMusicalAnalysis,
} from "abservice-installation";
import type {
  InstallationManifest,
  MusicalAnalysis,
} from "abservice-installation";
import {
  checkAbort,
  commit,
  hash,
  locked,
  optional,
  reject,
  requireValue,
} from "./common";
import { buildStore } from "./store";
import type { StorageResult } from "./index";

const namespace = "abservice-musical-analysis-v1";
const folder = (): Promise<FileSystemDirectoryHandle> =>
  navigator.storage
    .getDirectory()
    .then((root) => root.getDirectoryHandle(namespace, { create: true }));
const parse = async (blob: Blob): Promise<MusicalAnalysis> => {
  requireValue(blob.size <= analysisByteLimit, "corrupt");
  try {
    return (
      parseMusicalAnalysis(JSON.parse(await blob.text())) ?? reject("corrupt")
    );
  } catch {
    return reject("corrupt");
  }
};

/** Supplementary interpretation is stored by package and encoded audio identity; a failed replacement keeps the old result. */
export const buildAnalysisStore = (input: InstallationManifest) => {
  const parsed = parseManifest(input);
  const manifest = parsed.kind === "manifest" ? parsed.manifest : undefined;
  const key = async (
    audioSha256: string,
    signal: AbortSignal,
  ): Promise<string> => {
    requireValue(manifest !== undefined, "invalid-manifest");
    requireValue(/^[a-f0-9]{64}$/.test(audioSha256), "corrupt");
    return hash(
      new Blob([JSON.stringify([manifest?.packageVersion, audioSha256])]),
      signal,
    );
  };
  const read = (
    audioSha256: string,
    signal: AbortSignal,
  ): Promise<StorageResult<MusicalAnalysis | undefined>> =>
    locked(namespace, signal, async () => {
      const name = await key(audioSha256, signal);
      const file = await optional((await folder()).getFileHandle(name));
      const load = async (
        stored: FileSystemFileHandle,
      ): Promise<MusicalAnalysis> => {
        const blob = await stored.getFile();
        requireValue(blob.size <= analysisByteLimit + 128, "corrupt");
        const text = await blob.text();
        const separator = text.indexOf("\n");
        requireValue(separator === 64, "corrupt");
        const body = text.slice(separator + 1);
        requireValue(
          (await hash(new Blob([body]), signal)) === text.slice(0, separator),
          "corrupt",
        );
        const analysis = await parse(new Blob([body]));
        requireValue(analysis.audioSha256 === audioSha256, "corrupt");
        checkAbort(signal);
        return analysis;
      };
      return file === undefined ? undefined : load(file);
    });
  const save = (
    blob: Blob,
    signal: AbortSignal,
  ): Promise<StorageResult<MusicalAnalysis>> =>
    locked(namespace, signal, async () => {
      requireValue(manifest !== undefined, "invalid-manifest");
      const contract = manifest as InstallationManifest;
      const analysis = await parse(blob);
      const item = getPlaybackItems(contract).find((item) =>
        contract.assets.some(
          (asset) =>
            asset.assetId === item.audioAssetId &&
            asset.checksum.algorithm === "sha256" &&
            asset.checksum.value === analysis.audioSha256,
        ),
      );
      requireValue(item !== undefined, "unknown-asset");
      const duration = item?.durationSeconds;
      requireValue(
        duration === undefined
          ? true
          : Math.abs(duration - analysis.durationSeconds) <= 0.05,
        "corrupt",
      );
      /* Installation requires the source bytes to be present and verified. */
      const assets = buildStore(contract);
      const source =
        assets.kind === "ok"
          ? await assets.value.read(item?.audioAssetId ?? "", signal)
          : assets;
      requireValue(
        source.kind === "ok",
        source.kind === "error" ? source.error : "missing",
      );
      const body = JSON.stringify(analysis);
      const digest = await hash(new Blob([body]), signal);
      const file = await (
        await folder()
      ).getFileHandle(await key(analysis.audioSha256, signal), {
        create: true,
      });
      await commit(file, `${digest}\n${body}`, signal);
      return analysis;
    });
  return Object.freeze({ read, save });
};
