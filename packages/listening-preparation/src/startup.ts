import type { InstallationManifest } from 'abservice-installation';
import type { StorageError, StorageResult } from 'abservice-offline-storage';
import type {
  ActivePackage,
  Collection,
  GenerationLocks,
  Maintenance,
  PreparationFailure,
  Promotion,
  StartupDependencies,
  StartupResult,
} from './index';
import { observe, requirePlayable, settle, stored } from './steps';

type Wiring = Required<StartupDependencies>;
type Verdict =
  | Readonly<{ kind: 'playable' }>
  | Readonly<{ kind: 'unplayable'; failure: PreparationFailure }>;

/* Taken before the package and asset locks, never inside them: the order is generation, package, asset. */
const generations = 'abservice-generations-v1';

const verify = (
  wiring: Wiring,
  manifest: InstallationManifest,
  signal: AbortSignal,
): Promise<Verdict> =>
  settle<Verdict, Verdict>(
    async () => {
      const assets = stored(wiring.openAssets(manifest));
      const shell = await wiring.appShellAvailable(signal).catch(() => false);
      requirePlayable(
        manifest,
        await observe(manifest, assets, wiring.appVersion, shell, signal),
      );
      return { kind: 'playable' };
    },
    (verdict) => verdict,
    (failure) => ({ kind: 'unplayable', failure }),
  );

/* An unplayable or unreadable pending stays pending: preparing again resumes it instead of starting over. */
const promote = async (
  wiring: Wiring,
  signal: AbortSignal,
): Promise<Promotion> => {
  const pending = await wiring.packages.read('pending', signal);
  const candidate = pending.kind === 'ok' ? pending.value : undefined;
  const verdict =
    candidate === undefined
      ? undefined
      : await verify(wiring, candidate, signal);
  const promoted =
    verdict?.kind === 'playable'
      ? await wiring.packages.promote(signal)
      : undefined;
  return pending.kind === 'error'
    ? { kind: 'deferred', failure: { stage: 'storage', error: pending.error } }
    : candidate === undefined
      ? { kind: 'none' }
      : verdict?.kind === 'unplayable'
        ? { kind: 'deferred', failure: verdict.failure }
        : promoted?.kind === 'error'
          ? {
              kind: 'deferred',
              failure: { stage: 'storage', error: promoted.error },
            }
          : { kind: 'promoted', packageVersion: candidate.packageVersion };
};

const firstError = (
  results: readonly StorageResult<unknown>[],
): StorageError | undefined =>
  results.flatMap((result) =>
    result.kind === 'error' ? [result.error] : [],
  )[0];

/* What to keep is known only when both slots read; otherwise no asset is removed. */
const collect = async (
  wiring: Wiring,
  signal: AbortSignal,
): Promise<Collection> => {
  const manifests = await wiring.packages.collect(signal);
  const active = await wiring.packages.read('active', signal);
  const pending = await wiring.packages.read('pending', signal);
  const blocked = firstError([manifests, active, pending]);
  const kept = [active, pending].flatMap((result) =>
    result.kind === 'ok' && result.value !== undefined ? [result.value] : [],
  );
  const assets =
    blocked === undefined
      ? await wiring.collectAssets(kept, signal)
      : undefined;
  return manifests.kind === 'ok' && assets?.kind === 'ok'
    ? { kind: 'collected', manifests: manifests.value, assets: assets.value }
    : {
        kind: 'failed',
        error:
          blocked ??
          (assets?.kind === 'error' ? assets.error : 'storage-unavailable'),
      };
};

const maintain = (wiring: Wiring, signal: AbortSignal): Promise<Maintenance> =>
  wiring.locks.request(
    generations,
    { mode: 'exclusive', ifAvailable: true },
    async (lock) =>
      lock === null
        ? { kind: 'skipped' }
        : {
            kind: 'maintained',
            promotion: await promote(wiring, signal),
            collection: await collect(wiring, signal),
          },
  );

/* Resolves once the shared hold is granted; the hold itself lasts until the page's lifetime ends. */
const hold = (locks: GenerationLocks, lifetime: AbortSignal): Promise<boolean> =>
  new Promise((resolve) => {
    void locks
      .request(generations, { mode: 'shared', signal: lifetime }, () => {
        const released = new Promise<void>((release) => {
          lifetime.addEventListener(
            'abort',
            () => {
              release();
            },
            { once: true },
          );
        });
        resolve(lifetime.aborted ? false : true);
        return lifetime.aborted ? Promise.resolve() : released;
      })
      .catch(() => {
        resolve(false);
      });
  });

/* A package promoted by this startup was verified moments ago under the exclusive lock; it is not hashed twice. */
const judge = async (
  wiring: Wiring,
  promotion: Promotion,
  signal: AbortSignal,
): Promise<ActivePackage> => {
  const read = await wiring.packages.read('active', signal);
  const manifest = read.kind === 'ok' ? read.value : undefined;
  const fresh =
    promotion.kind === 'promoted' &&
    promotion.packageVersion === manifest?.packageVersion;
  const verdict =
    manifest === undefined
      ? undefined
      : fresh
        ? ({ kind: 'playable' } as const)
        : await verify(wiring, manifest, signal);
  return read.kind === 'error'
    ? { kind: 'unreadable', error: read.error }
    : manifest === undefined
      ? { kind: 'none' }
      : verdict?.kind === 'unplayable'
        ? { kind: 'not-ready', manifest, failure: verdict.failure }
        : { kind: 'ready', manifest };
};

const begin = async (
  wiring: Wiring,
  lifetime: AbortSignal,
): Promise<StartupResult> => {
  const maintenance = await maintain(wiring, lifetime);
  const held = await hold(wiring.locks, lifetime);
  return held
    ? {
        kind: 'started',
        maintenance,
        active: await judge(
          wiring,
          maintenance.kind === 'maintained'
            ? maintenance.promotion
            : { kind: 'none' },
          lifetime,
        ),
      }
    : { kind: 'aborted' };
};

export const runStartup = (
  wiring: Wiring,
  lifetime: AbortSignal,
): Promise<StartupResult> =>
  lifetime.aborted
    ? Promise.resolve({ kind: 'aborted' })
    : begin(wiring, lifetime);
