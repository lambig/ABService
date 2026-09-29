import type {
  DistributionClient,
  DistributionError,
} from 'abservice-distribution-client';
import type { AppVersion, InstallationManifest } from 'abservice-installation';
import {
  collectAssets,
  createAssetStore,
  createPackageStore,
} from 'abservice-offline-storage';
import type {
  AssetStore,
  PackageStore,
  StorageError,
  StorageResult,
} from 'abservice-offline-storage';
import { runStartup } from './startup';
import { runPreparation } from './transaction';

/**
 * 準備に使う取得元と保存先。取得は配布 client、Manifest の世代は package store、asset 実体は
 * Manifest ごとに開く asset store が受け持つ。shell の完全性は Service Worker を持つ呼び出し側が答える。
 */
export type PreparationDependencies = Readonly<{
  client: DistributionClient;
  appVersion: AppVersion;
  appShellAvailable: (signal: AbortSignal) => Promise<boolean>;
  packages?: PackageStore;
  openAssets?: (manifest: InstallationManifest) => StorageResult<AssetStore>;
}>;

/**
 * 準備済みの package が、いつから再生に使われるか。
 * - current: 配布中の版が既に active。不足していた asset を補っただけで、切り替えは起きない
 * - on-restart: pending として準備済み。全タブを閉じた後の起動で昇格する
 */
export type Activation = 'current' | 'on-restart';

/** 準備が済んだ package。取得した asset の ID を順に返し、保存済みで取り直さなかったものは含めない。 */
export type Prepared = Readonly<{
  packageVersion: string;
  activation: Activation;
  fetchedAssetIds: readonly string[];
}>;

/**
 * 準備が止まった理由。呼び出し側が次の操作（再認証・再試行・容量の確保・アプリの更新）を選べる粒度にする。
 * 診断に載せるのは段階・assetId・mediaType・分類・HTTP ステータス・byte 数だけで、token と署名 URL は載せない。
 * - package: 配布 package を取得できない
 * - incompatible: 配布中の package がこのアプリの版に対応しない。保存もしない
 * - capacity: 不足・破損した required asset を取り直す空きが無い。取得を始めない
 * - asset: required asset の取得か保存に失敗した。それまでに保存した asset は残り、再試行で取り直さない
 * - storage: 保存領域の観測・世代の保存に失敗した。active と同じ版で内容が違う場合は conflict
 * - shell: asset は揃ったが、同じ世代の shell が完全でない
 * - incomplete: 取得を終えた後の観測で、まだ不足・破損した required asset がある
 */
export type PreparationFailure =
  | Readonly<{ stage: 'package'; error: DistributionError; status?: number }>
  | Readonly<{
      stage: 'incompatible';
      packageVersion: string;
      compatibleAppVersion: InstallationManifest['compatibleAppVersion'];
    }>
  | Readonly<{
      stage: 'capacity';
      requiredBytes: number;
      availableBytes: number;
    }>
  | Readonly<{
      stage: 'asset';
      assetId: string;
      mediaType: string;
      error: DistributionError | StorageError;
      status?: number;
    }>
  | Readonly<{ stage: 'storage'; error: StorageError }>
  | Readonly<{ stage: 'shell' }>
  | Readonly<{
      stage: 'incomplete';
      missingAssetIds: readonly string[];
      corruptAssetIds: readonly string[];
    }>;

/** 準備の結果。失敗しても active は変えない。 */
export type PreparationResult =
  | Readonly<{ kind: 'prepared'; value: Prepared }>
  | Readonly<{ kind: 'failed'; failure: PreparationFailure }>;

/**
 * 配布中の package を取得し、不足・破損した required asset だけを直列に取り直して、再起動待ちの pending にする。
 * active は置き換えない。途中で止まっても、保存済みの asset は次の実行で取り直さない。
 */
export const prepare = (
  dependencies: PreparationDependencies,
  signal: AbortSignal,
): Promise<PreparationResult> =>
  runPreparation(
    {
      packages: dependencies.packages ?? createPackageStore(),
      openAssets: dependencies.openAssets ?? createAssetStore,
      client: dependencies.client,
      appVersion: dependencies.appVersion,
      appShellAvailable: dependencies.appShellAvailable,
    },
    signal,
  );

/**
 * 世代の利用を排他するロック。起動時の昇格・GC は exclusive、各ページの利用期間は shared で取る。
 * 既定は navigator.locks。
 */
export type GenerationLocks = Readonly<{
  request: <T>(
    name: string,
    options: Readonly<{
      mode: 'exclusive' | 'shared';
      ifAvailable?: boolean;
      signal?: AbortSignal;
    }>,
    callback: (lock: Lock | null) => Promise<T>,
  ) => Promise<T>;
}>;

/** 起動時の処理に使う保存先とロック。shell の完全性は Service Worker を持つ呼び出し側が答える。 */
export type StartupDependencies = Readonly<{
  appVersion: AppVersion;
  appShellAvailable: (signal: AbortSignal) => Promise<boolean>;
  packages?: PackageStore;
  openAssets?: (manifest: InstallationManifest) => StorageResult<AssetStore>;
  collectAssets?: (
    keep: readonly InstallationManifest[],
    signal: AbortSignal,
  ) => Promise<StorageResult<number>>;
  locks?: GenerationLocks;
}>;

/**
 * pending の扱い。
 * - none: pending が無い
 * - promoted: 検証を通り active になった
 * - deferred: 再生できないため昇格しなかった。pending は残り、準備を実行し直せば続きから取り直す
 */
export type Promotion =
  | Readonly<{ kind: 'none' }>
  | Readonly<{ kind: 'promoted'; packageVersion: string }>
  | Readonly<{ kind: 'deferred'; failure: PreparationFailure }>;

/** 不要世代の削除の結果。active か pending の Manifest が読めない場合は、残すべき実体が分からないため asset を削除しない。 */
export type Collection =
  | Readonly<{ kind: 'collected'; manifests: number; assets: number }>
  | Readonly<{ kind: 'failed'; error: StorageError }>;

/** 起動時の保守。他のタブが世代を使っている間は、昇格も削除も行わない（skipped）。 */
export type Maintenance =
  | Readonly<{ kind: 'skipped' }>
  | Readonly<{
      kind: 'maintained';
      promotion: Promotion;
      collection: Collection;
    }>;

/**
 * このページが再生に使う active package。
 * - none: 準備済みの package が無い
 * - ready: 互換・required asset・shell が揃っている
 * - not-ready: Manifest は読めるが再生できない。理由は準備の失敗と同じ分類で返す
 * - unreadable: active の Manifest が読めない（欠落・破損・保存領域の消去）
 */
export type ActivePackage =
  | Readonly<{ kind: 'none' }>
  | Readonly<{ kind: 'ready'; manifest: InstallationManifest }>
  | Readonly<{
      kind: 'not-ready';
      manifest: InstallationManifest;
      failure: PreparationFailure;
    }>
  | Readonly<{ kind: 'unreadable'; error: StorageError }>;

/** 起動の結果。aborted は、世代の利用を始める前にページの終了で止まったもの。 */
export type StartupResult =
  | Readonly<{
      kind: 'started';
      maintenance: Maintenance;
      active: ActivePackage;
    }>
  | Readonly<{ kind: 'aborted' }>;

/**
 * ページの起動時に呼ぶ。他のタブが世代を使っていなければ、pending を検証して active へ昇格し、
 * どこからも参照されない Manifest と asset 実体を削除する。その後、lifetime が中止されるまで世代の利用を
 * shared で保持する。準備（prepare）はこの保持の間に行う。昇格や削除が他のタブの利用と重ならないのは、
 * 世代を使う全ページがこの保持を持つ場合に限る。
 */
export const startup = (
  dependencies: StartupDependencies,
  lifetime: AbortSignal,
): Promise<StartupResult> =>
  runStartup(
    {
      appVersion: dependencies.appVersion,
      appShellAvailable: dependencies.appShellAvailable,
      packages: dependencies.packages ?? createPackageStore(),
      openAssets: dependencies.openAssets ?? createAssetStore,
      collectAssets: dependencies.collectAssets ?? collectAssets,
      locks: dependencies.locks ?? {
        /* The DOM signature types a promise-returning callback as a nested promise; awaiting flattens it. */
        request: async (name, options, callback) =>
          navigator.locks.request(name, options, callback).then(
            (granted) => granted,
          ),
      },
    },
    lifetime,
  );
