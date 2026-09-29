import type { DistributionResult } from 'abservice-distribution-client';
import { assessReadiness } from 'abservice-installation';
import type { InstallationManifest, Readiness } from 'abservice-installation';
import type {
  AssetStore,
  PackageStore,
  StorageResult,
} from 'abservice-offline-storage';
import type {
  PreparationDependencies,
  PreparationFailure,
  PreparationResult,
  Prepared,
} from './index';

type Asset = InstallationManifest['assets'][number];
type Wiring = Required<PreparationDependencies>;
type Observed = Readonly<{ readiness: Readiness; availableBytes: number }>;

/* The payload is the whole diagnosis, so an escaped Stop cannot carry a token or signed URL. */
class Stop extends Error {
  constructor(readonly failure: PreparationFailure) {
    super(failure.stage);
  }
}
const stop = (failure: PreparationFailure): never => {
  throw new Stop(failure);
};
const requireThat = (condition: boolean, failure: PreparationFailure): void =>
  condition ? undefined : stop(failure);
const stored = <T>(result: StorageResult<T>): T =>
  result.kind === 'ok'
    ? result.value
    : stop({ stage: 'storage', error: result.error });
const withStatus = (status: number | undefined) =>
  status === undefined ? {} : { status };

/* Readiness is always computed from the bytes actually stored, never from what this run believes it saved. */
const observe = async (
  manifest: InstallationManifest,
  assets: AssetStore,
  wiring: Wiring,
  appShellAvailable: boolean,
  signal: AbortSignal,
): Promise<Observed> => {
  const inventory = stored(await assets.inspect(signal));
  const readiness = assessReadiness(manifest, {
    ...inventory,
    appVersion: wiring.appVersion,
    appShellAvailable,
  });
  return readiness.kind === 'assessed'
    ? { readiness, availableBytes: inventory.availableBytes }
    : stop({ stage: 'storage', error: 'invalid-manifest' });
};

/* The fetched blob lives only until it is saved; assets are fetched one at a time to bound peak memory. */
const fetchOne = async (
  manifest: InstallationManifest,
  assets: AssetStore,
  wiring: Wiring,
  asset: Asset,
  signal: AbortSignal,
): Promise<void> => {
  const failed = (
    result: Exclude<DistributionResult<Blob>, { kind: 'ok' }>,
  ): never =>
    stop({
      stage: 'asset',
      assetId: asset.assetId,
      mediaType: asset.mediaType,
      error: result.error,
      ...withStatus(result.status),
    });
  const fetched = await wiring.client.asset(manifest, asset.assetId, signal);
  const blob = fetched.kind === 'ok' ? fetched.value : failed(fetched);
  const saved = await assets.save(asset.assetId, blob, signal);
  requireThat(saved.kind === 'ok', {
    stage: 'asset',
    assetId: asset.assetId,
    mediaType: asset.mediaType,
    error: saved.kind === 'error' ? saved.error : 'storage-unavailable',
  });
};

/*
 * A candidate equal to active needs no switch; any older pending would otherwise be promoted over it.
 * An unreadable active is not treated as current, so staging the candidate becomes its repair.
 */
const record = async (
  packages: PackageStore,
  manifest: InstallationManifest,
  signal: AbortSignal,
): Promise<Prepared['activation']> => {
  const active = await packages.read('active', signal);
  const current =
    active.kind === 'ok' &&
    active.value?.packageVersion === manifest.packageVersion;
  stored(
    await (current
      ? packages.discardPending(signal)
      : packages.stage(manifest, signal)),
  );
  return current ? 'current' : 'on-restart';
};

const transaction = async (
  wiring: Wiring,
  signal: AbortSignal,
): Promise<Prepared> => {
  const fetched = await wiring.client.package(signal);
  const manifest =
    fetched.kind === 'ok'
      ? fetched.value
      : stop({
          stage: 'package',
          error: fetched.error,
          ...withStatus(fetched.status),
        });
  const assets = stored(wiring.openAssets(manifest));
  const before = await observe(manifest, assets, wiring, false, signal);
  /* Checked before staging: an incompatible or unfittable candidate is never recorded as pending. */
  requireThat(before.readiness.appCompatible, {
    stage: 'incompatible',
    packageVersion: manifest.packageVersion,
    compatibleAppVersion: manifest.compatibleAppVersion,
  });
  requireThat(before.readiness.storageCapacitySufficient, {
    stage: 'capacity',
    requiredBytes: before.readiness.requiredDownloadBytes,
    availableBytes: before.availableBytes,
  });
  /* Staged before fetching, so that assets saved by an interrupted run stay referenced by a generation. */
  const activation = await record(wiring.packages, manifest, signal);
  const needed = new Set([
    ...before.readiness.missingAssetIds,
    ...before.readiness.corruptAssetIds,
  ]);
  const fetchedAssetIds = await manifest.assets
    .filter((asset) => needed.has(asset.assetId))
    .reduce<Promise<readonly string[]>>(async (previous, asset) => {
      const done = await previous;
      await fetchOne(manifest, assets, wiring, asset, signal);
      return [...done, asset.assetId];
    }, Promise.resolve([]));
  const shell = await wiring.appShellAvailable(signal).catch(() => false);
  const after = await observe(manifest, assets, wiring, shell, signal);
  requireThat(after.readiness.packageComplete, {
    stage: 'incomplete',
    missingAssetIds: after.readiness.missingAssetIds,
    corruptAssetIds: after.readiness.corruptAssetIds,
  });
  requireThat(after.readiness.appShellAvailable, { stage: 'shell' });
  return {
    packageVersion: manifest.packageVersion,
    activation,
    fetchedAssetIds,
  };
};

export const runPreparation = (
  wiring: Wiring,
  signal: AbortSignal,
): Promise<PreparationResult> =>
  transaction(wiring, signal).then(
    (value) => ({ kind: 'prepared', value }),
    (error: unknown) =>
      error instanceof Stop
        ? { kind: 'failed', failure: error.failure }
        : Promise.reject(
            error instanceof Error ? error : new Error('preparation failed'),
          ),
  );
