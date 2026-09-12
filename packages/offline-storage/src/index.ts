import type {
  AppVersion,
  InstallationManifest,
  LocalEnvironment,
  ReadinessResult,
} from "abservice-installation";
import { buildStore } from "./store";

/** 保存方式を外へ漏らさない、回復理由の分類。容量見積り失敗と容量不足は区別する。 */
export type StorageError =
  | "invalid-manifest"
  | "unknown-asset"
  | "unsupported-checksum"
  | "corrupt"
  | "missing"
  | "aborted"
  | "quota-exceeded"
  | "storage-unavailable";

/** I/O失敗を成功値と区別する。AbortSignalの任意のreasonもabortedに統一する。 */
export type StorageResult<T> =
  | Readonly<{ kind: "ok"; value: T }>
  | Readonly<{ kind: "error"; error: StorageError }>;

/** 観測値はOPFS実体から算出する。app shellの可用性をこのadapterでは主張しない。 */
export type StoredInventory = Readonly<{
  inventory: LocalEnvironment["inventory"];
  availableBytes: number;
}>;

/** Manifestで選んだ世代だけを扱う。readも毎回実体検証し、不正な音源を返さない。 */
export type AssetStore = Readonly<{
  save: (
    assetId: string,
    blob: Blob,
    signal: AbortSignal,
  ) => Promise<StorageResult<void>>;
  read: (assetId: string, signal: AbortSignal) => Promise<StorageResult<Blob>>;
  inspect: (signal: AbortSignal) => Promise<StorageResult<StoredInventory>>;
  assess: (
    appVersion: AppVersion,
    signal: AbortSignal,
  ) => Promise<StorageResult<ReadinessResult>>;
}>;

/** 検証済みManifestも再パースしてsnapshot化する。OPFS/Web Locksへのアクセスは操作時のみ。 */
export const createAssetStore = (
  manifest: InstallationManifest,
): StorageResult<AssetStore> => buildStore(manifest);
