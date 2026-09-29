import { assessReadiness } from 'abservice-installation';
import type {
  AppVersion,
  InstallationManifest,
  Readiness,
} from 'abservice-installation';
import type { AssetStore, StorageResult } from 'abservice-offline-storage';
import type { PreparationFailure } from './index';

export type Observed = Readonly<{
  readiness: Readiness;
  availableBytes: number;
}>;

/* The payload is the whole diagnosis, so an escaped Stop cannot carry a token or signed URL. */
class Stop extends Error {
  constructor(readonly failure: PreparationFailure) {
    super(failure.stage);
  }
}
export const stop = (failure: PreparationFailure): never => {
  throw new Stop(failure);
};
export const requireThat = (
  condition: boolean,
  failure: PreparationFailure,
): void => (condition ? undefined : stop(failure));
export const stored = <T>(result: StorageResult<T>): T =>
  result.kind === 'ok'
    ? result.value
    : stop({ stage: 'storage', error: result.error });
/** Runs the steps and turns a stop into its diagnosis; anything else is a defect and stays a rejection. */
export const settle = <T, R>(
  steps: () => Promise<T>,
  done: (value: T) => R,
  stopped: (failure: PreparationFailure) => R,
): Promise<R> =>
  steps().then(done, (error: unknown) =>
    error instanceof Stop
      ? stopped(error.failure)
      : Promise.reject(
          error instanceof Error ? error : new Error('preparation failed'),
        ),
  );

/* Readiness is always computed from the bytes actually stored, never from what a run believes it saved. */
export const observe = async (
  manifest: InstallationManifest,
  assets: AssetStore,
  appVersion: AppVersion,
  appShellAvailable: boolean,
  signal: AbortSignal,
): Promise<Observed> => {
  const inventory = stored(await assets.inspect(signal));
  const readiness = assessReadiness(manifest, {
    ...inventory,
    appVersion,
    appShellAvailable,
  });
  return readiness.kind === 'assessed'
    ? { readiness, availableBytes: inventory.availableBytes }
    : stop({ stage: 'storage', error: 'invalid-manifest' });
};

/** Fails with the first reason the observed package cannot be played: app compatibility, then assets, then shell. */
export const requirePlayable = (
  manifest: InstallationManifest,
  { readiness }: Observed,
): void => {
  requireThat(readiness.appCompatible, {
    stage: 'incompatible',
    packageVersion: manifest.packageVersion,
    compatibleAppVersion: manifest.compatibleAppVersion,
  });
  requireThat(readiness.packageComplete, {
    stage: 'incomplete',
    missingAssetIds: readiness.missingAssetIds,
    corruptAssetIds: readiness.corruptAssetIds,
  });
  requireThat(readiness.appShellAvailable, { stage: 'shell' });
};
