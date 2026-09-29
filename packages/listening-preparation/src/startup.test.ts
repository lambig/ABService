/* eslint-disable functional/immutable-data -- The fakes stand in for OPFS and Web Locks and record each call so tests can inspect them. */
import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { parseManifest } from 'abservice-installation';
import type { AppVersion, InstallationManifest } from 'abservice-installation';
import type {
  AssetStore,
  PackageSlot,
  PackageStore,
  StorageError,
  StorageResult,
} from 'abservice-offline-storage';
import { startup } from './index';
import type { GenerationLocks, StartupResult } from './index';

/** backend の配布応答と同じ見本。 */
const example = JSON.parse(
  readFileSync(
    new URL(
      '../../installation/fixtures/manifest-v3.example.json',
      import.meta.url,
    ),
    'utf8',
  ),
) as Readonly<Record<string, unknown>>;
const parse = (input: unknown): InstallationManifest => {
  const parsed = parseManifest(input);
  return parsed.kind === 'manifest'
    ? parsed.manifest
    : ((): never => {
        throw new Error('the example must be a valid manifest');
      })();
};
const [audioAsset, artworkAsset] = example.assets as readonly Readonly<
  Record<string, unknown>
>[];
const [crossfade] = example.playbackItems as readonly Readonly<
  Record<string, unknown>
>[];
const audio = '0192f8a0-0000-7000-8000-000000000001';
const artwork = '0192f8a0-0000-7000-8000-000000000010.png';
const replacedAudio = '0192f8a0-0000-7000-8000-000000000002';
const old = parse(example);
/** 音源だけを差し替えた新しい版。artwork は旧版と共有する。 */
const next = parse({
  ...example,
  packageVersion: '1'.repeat(64),
  assets: [{ ...audioAsset, assetId: replacedAudio }, artworkAsset],
  playbackItems: [{ ...crossfade, audioAssetId: replacedAudio }],
});
/** この app（1.10.0）では動かない版。 */
const future = parse({
  ...example,
  packageVersion: '2'.repeat(64),
  compatibleAppVersion: { minInclusive: [1, 11, 0], maxExclusive: [2, 0, 0] },
});
const appVersion: AppVersion = [1, 10, 0];
const ok = <T>(value: T) => ({ kind: 'ok', value }) as const;
const storageError = (error: StorageError) =>
  ({ kind: 'error', error }) as const;
const assetsOf = (manifest: InstallationManifest): readonly string[] =>
  manifest.assets.map((asset) => asset.assetId);

/** 保存済みの実体。検証済みとして観測される assetId の集合で表す。 */
const assetDisk = (initial: readonly string[]) => {
  const files = new Set(initial);
  const inspected: string[] = [];
  const kept: string[][] = [];
  const open = (manifest: InstallationManifest): StorageResult<AssetStore> =>
    ok({
      save: () => Promise.resolve(storageError('storage-unavailable')),
      read: () => Promise.resolve(storageError('missing')),
      inspect: () => {
        inspected.push(manifest.packageVersion);
        return Promise.resolve(
          ok({
            inventory: manifest.assets
              .filter((asset) => files.has(asset.assetId))
              .map((asset) => ({
                assetId: asset.assetId,
                byteLength: asset.byteLength,
                checksum: asset.checksum,
              })),
            availableBytes: 0,
          }),
        );
      },
      assess: () => Promise.resolve(storageError('storage-unavailable')),
    });
  const collect = (
    keep: readonly InstallationManifest[],
  ): Promise<StorageResult<number>> => {
    kept.push(keep.map((manifest) => manifest.packageVersion));
    const referenced = new Set(keep.flatMap(assetsOf));
    const removed = [...files].filter((assetId) =>
      referenced.has(assetId) ? false : true,
    );
    removed.forEach((assetId) => files.delete(assetId));
    return Promise.resolve(ok(removed.length));
  };
  return { files, inspected, kept, open, collect };
};

/** active / pending の 2 つの slot。読み出しと昇格の失敗を差し込める。 */
const packageDisk = (
  slots: Partial<Record<PackageSlot, InstallationManifest>>,
) => {
  const state: Partial<Record<PackageSlot, InstallationManifest>> = {
    ...slots,
  };
  const readFailures = new Map<PackageSlot, StorageError>();
  const failures: { promote?: StorageError } = {};
  const calls: string[] = [];
  const store: PackageStore = {
    stage: () => Promise.resolve(storageError('storage-unavailable')),
    read: (slot) => {
      const failure = readFailures.get(slot);
      return Promise.resolve(
        failure === undefined ? ok(state[slot]) : storageError(failure),
      );
    },
    promote: () => {
      calls.push('promote');
      return Promise.resolve(
        failures.promote === undefined
          ? ((): StorageResult<void> => {
              const promoted = state.pending;
              delete state.pending;
              delete state.active;
              Object.assign(
                state,
                promoted === undefined ? {} : { active: promoted },
              );
              return ok(undefined);
            })()
          : storageError(failures.promote),
      );
    },
    discardPending: () => Promise.resolve(ok(undefined)),
    list: () => Promise.resolve(ok({ packages: [], unreadable: 0 })),
    collect: () => {
      calls.push('collect');
      return Promise.resolve(ok(0));
    },
  };
  return { state, readFailures, failures, calls, store };
};

/**
 * 1 つの名前だけを扱う Web Locks の偽物。ifAvailable の exclusive は、他の保持があれば null で呼ぶ。
 * 他のタブの shared の保持は othersHolding で表す。
 */
const lockManager = (othersHolding = 0) => {
  const state = { exclusive: false, shared: othersHolding };
  const granted: string[] = [];
  const locks: GenerationLocks = {
    request: async (name, options, callback) => {
      const busy = [state.exclusive, state.shared > 0].some(Boolean);
      const denied = options.mode === 'exclusive' && busy;
      granted.push(denied ? `${options.mode}:denied` : options.mode);
      const counter = options.mode === 'exclusive' ? 'exclusive' : 'shared';
      const acquire = (): void => {
        state.exclusive = counter === 'exclusive' ? true : state.exclusive;
        state.shared = counter === 'shared' ? state.shared + 1 : state.shared;
      };
      const release = (): void => {
        state.exclusive = counter === 'exclusive' ? false : state.exclusive;
        state.shared = counter === 'shared' ? state.shared - 1 : state.shared;
      };
      return denied
        ? callback(null)
        : (() => {
            acquire();
            return callback({ name, mode: options.mode }).finally(
              release,
            );
          })();
    },
  };
  return { state, granted, locks };
};

const setup = (
  options: Readonly<{
    slots: Partial<Record<PackageSlot, InstallationManifest>>;
    files: readonly string[];
    othersHolding?: number;
    shell?: () => Promise<boolean>;
  }>,
) => {
  const assets = assetDisk(options.files);
  const packages = packageDisk(options.slots);
  const locking = lockManager(options.othersHolding);
  const lifetime = new AbortController();
  const run = (signal: AbortSignal = lifetime.signal): Promise<StartupResult> =>
    startup(
      {
        appVersion,
        appShellAvailable: options.shell ?? (() => Promise.resolve(true)),
        packages: packages.store,
        openAssets: assets.open,
        collectAssets: assets.collect,
        locks: locking.locks,
      },
      signal,
    );
  return { assets, packages, locking, lifetime, run };
};

describe('startup', () => {
  it('promotes a complete pending, then removes what only the old generation used', async () => {
    const { assets, packages, locking, run } = setup({
      slots: { active: old, pending: next },
      files: [audio, artwork, replacedAudio],
    });

    await expect(run()).resolves.toEqual({
      kind: 'started',
      maintenance: {
        kind: 'maintained',
        promotion: { kind: 'promoted', packageVersion: next.packageVersion },
        collection: { kind: 'collected', manifests: 0, assets: 1 },
      },
      active: { kind: 'ready', manifest: next },
    });
    expect(packages.state.active).toBe(next);
    expect(packages.calls).toEqual(['promote', 'collect']);
    expect([...assets.files].sort()).toEqual([artwork, replacedAudio].sort());
    expect(assets.inspected).toEqual([next.packageVersion]);
    expect(locking.granted).toEqual(['exclusive', 'shared']);
  });

  it('keeps the old active and the pending when the pending is incomplete, removing nothing either uses', async () => {
    const { assets, packages, run } = setup({
      slots: { active: old, pending: next },
      files: [audio, artwork],
    });

    await expect(run()).resolves.toEqual({
      kind: 'started',
      maintenance: {
        kind: 'maintained',
        promotion: {
          kind: 'deferred',
          failure: {
            stage: 'incomplete',
            missingAssetIds: [replacedAudio],
            corruptAssetIds: [],
          },
        },
        collection: { kind: 'collected', manifests: 0, assets: 0 },
      },
      active: { kind: 'ready', manifest: old },
    });
    expect(packages.state).toEqual({ active: old, pending: next });
    expect(assets.kept).toEqual([[old.packageVersion, next.packageVersion]]);
  });

  it('does not promote a package this app version cannot run', async () => {
    const { packages, run } = setup({
      slots: { active: old, pending: future },
      files: [audio, artwork],
    });

    await expect(run()).resolves.toMatchObject({
      maintenance: {
        promotion: { kind: 'deferred', failure: { stage: 'incompatible' } },
      },
      active: { kind: 'ready', manifest: old },
    });
    expect(packages.state.active).toBe(old);
  });

  it('does not promote while the shell of the same generation is incomplete, and does not call active ready', async () => {
    const { packages, run } = setup({
      slots: { active: old, pending: next },
      files: [audio, artwork, replacedAudio],
      shell: () => Promise.resolve(false),
    });

    await expect(run()).resolves.toMatchObject({
      maintenance: {
        promotion: { kind: 'deferred', failure: { stage: 'shell' } },
      },
      active: { kind: 'not-ready', failure: { stage: 'shell' } },
    });
    expect(packages.state.active).toBe(old);
  });

  it('keeps the old active when the promotion itself fails', async () => {
    const { packages, run } = setup({
      slots: { active: old, pending: next },
      files: [audio, artwork, replacedAudio],
    });
    packages.failures.promote = 'quota-exceeded';

    await expect(run()).resolves.toMatchObject({
      maintenance: {
        promotion: {
          kind: 'deferred',
          failure: { stage: 'storage', error: 'quota-exceeded' },
        },
      },
      active: { kind: 'ready', manifest: old },
    });
  });

  it('neither promotes nor removes anything while another tab uses a generation', async () => {
    const { assets, packages, locking, run } = setup({
      slots: { active: old, pending: next },
      files: [audio, artwork, replacedAudio],
      othersHolding: 1,
    });

    await expect(run()).resolves.toEqual({
      kind: 'started',
      maintenance: { kind: 'skipped' },
      active: { kind: 'ready', manifest: old },
    });
    expect(packages.calls).toEqual([]);
    expect(assets.kept).toEqual([]);
    expect(locking.granted).toEqual(['exclusive:denied', 'shared']);
  });

  it('holds the generation shared until the page lifetime ends', async () => {
    const { locking, lifetime, run } = setup({
      slots: { active: old },
      files: [audio, artwork],
    });

    await run();
    expect(locking.state).toEqual({ exclusive: false, shared: 1 });

    lifetime.abort();
    await expect.poll(() => locking.state.shared).toBe(0);
  });

  it('removes no asset when a slot cannot be read, and reports the active as unreadable', async () => {
    const { assets, packages, run } = setup({
      slots: { active: old },
      files: [audio, artwork],
    });
    packages.readFailures.set('active', 'corrupt');

    await expect(run()).resolves.toEqual({
      kind: 'started',
      maintenance: {
        kind: 'maintained',
        promotion: { kind: 'none' },
        collection: { kind: 'failed', error: 'corrupt' },
      },
      active: { kind: 'unreadable', error: 'corrupt' },
    });
    expect(assets.kept).toEqual([]);
    expect(assets.files.size).toBe(2);
  });

  it('keeps an unreadable pending referenced so that preparing again can repair it', async () => {
    const { assets, packages, run } = setup({
      slots: { active: old, pending: next },
      files: [audio, artwork, replacedAudio],
    });
    packages.readFailures.set('pending', 'corrupt');

    await expect(run()).resolves.toMatchObject({
      maintenance: {
        promotion: {
          kind: 'deferred',
          failure: { stage: 'storage', error: 'corrupt' },
        },
        collection: { kind: 'failed', error: 'corrupt' },
      },
      active: { kind: 'ready', manifest: old },
    });
    expect(assets.files.size).toBe(3);
  });

  it('detects missing or damaged active assets at startup instead of calling them ready', async () => {
    const { run } = setup({ slots: { active: old }, files: [artwork] });

    await expect(run()).resolves.toMatchObject({
      active: {
        kind: 'not-ready',
        manifest: old,
        failure: { stage: 'incomplete', missingAssetIds: [audio] },
      },
    });
  });

  it('reports no package when nothing has been prepared, removing leftovers', async () => {
    const { assets, run } = setup({ slots: {}, files: [audio] });

    await expect(run()).resolves.toEqual({
      kind: 'started',
      maintenance: {
        kind: 'maintained',
        promotion: { kind: 'none' },
        collection: { kind: 'collected', manifests: 0, assets: 1 },
      },
      active: { kind: 'none' },
    });
    expect(assets.files.size).toBe(0);
  });

  it('does nothing when the page is already gone', async () => {
    const { packages, locking, run } = setup({
      slots: { active: old, pending: next },
      files: [audio, artwork, replacedAudio],
    });
    const gone = new AbortController();
    gone.abort();

    await expect(run(gone.signal)).resolves.toEqual({ kind: 'aborted' });
    expect(packages.calls).toEqual([]);
    expect(locking.granted).toEqual([]);
  });
});
