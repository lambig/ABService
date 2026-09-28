import type {
  AppVersion,
  InstallationManifest,
  LocalEnvironment,
  ReadinessResult,
} from "abservice-installation";
import { buildPackageStore } from "./packages";
import { buildStore } from "./store";

/**
 * 保存方式を外へ漏らさない、回復理由の分類。容量見積り失敗と容量不足は区別する。
 * conflictは、参照中の世代と同じpackageVersionで内容の異なるManifestを保存しようとしたこと。
 */
export type StorageError =
  | "invalid-manifest"
  | "unknown-asset"
  | "unsupported-checksum"
  | "corrupt"
  | "missing"
  | "aborted"
  | "quota-exceeded"
  | "conflict"
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

/** 準備済みpackageの参照先。activeは再生が読む世代、pendingは昇格を待つ候補。 */
export type PackageSlot = "active" | "pending";

/** 保存済みの世代と、それを参照しているslot。どこからも参照されない世代のslotsは空。 */
export type StoredPackage = Readonly<{
  packageVersion: string;
  slots: readonly PackageSlot[];
}>;

/** 世代の列挙。読めない保存物は数だけを返し、保存パスを外へ渡さない。順序は保証しない。 */
export type StoredPackages = Readonly<{
  packages: readonly StoredPackage[];
  unreadable: number;
}>;

/**
 * ManifestをpackageVersionごとに保存し、active/pendingの参照を1つのpointerで切り替える。
 * asset実体・配布APIからの取得・昇格の時機・不要世代の削除は扱わない。
 */
export type PackageStore = Readonly<{
  /** strictに検証したManifestを保存してpendingにする。activeは変えない。 */
  stage: (
    manifest: unknown,
    signal: AbortSignal,
  ) => Promise<StorageResult<void>>;
  /** slotが指す世代を読むたびにstrictに検証する。slotが空ならundefined。 */
  read: (
    slot: PackageSlot,
    signal: AbortSignal,
  ) => Promise<StorageResult<InstallationManifest | undefined>>;
  /** 読める状態のpendingをactiveにし、pendingを空にする。 */
  promote: (signal: AbortSignal) => Promise<StorageResult<void>>;
  /** pendingの参照だけを外す。保存物は残す。 */
  discardPending: (signal: AbortSignal) => Promise<StorageResult<void>>;
  /** 保存済みの世代を、参照しているslotとともに返す。 */
  list: (signal: AbortSignal) => Promise<StorageResult<StoredPackages>>;
}>;

/** OPFS/Web Locksへのアクセスは操作時のみ。同一originの全タブで1つの排他ロックを共有する。 */
export const createPackageStore = (): PackageStore => buildPackageStore();
