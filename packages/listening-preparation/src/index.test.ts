/* eslint-disable functional/immutable-data -- The fakes stand in for OPFS and record each call so tests can inspect them. */
import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import type {
  DistributionClient,
  DistributionError,
  DistributionResult,
} from 'abservice-distribution-client';
import { parseManifest } from 'abservice-installation';
import type { AppVersion, InstallationManifest } from 'abservice-installation';
import type {
  AssetStore,
  PackageSlot,
  PackageStore,
  StorageError,
  StorageResult,
} from 'abservice-offline-storage';
import { prepare } from './index';
import type { PreparationResult } from './index';

/** backend の配布応答と同じ見本。クロスフェードのみで、収録曲は音源を持たない。 */
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
const manifest = parse(example);
const newer = parse({ ...example, packageVersion: '1'.repeat(64) });
const audio = '0192f8a0-0000-7000-8000-000000000001';
const artwork = '0192f8a0-0000-7000-8000-000000000010.png';
const appVersion: AppVersion = [1, 10, 0];
const ok = <T>(value: T) => ({ kind: 'ok', value }) as const;
const storageError = (error: StorageError) =>
  ({ kind: 'error', error }) as const;

type Observation = Readonly<{
  byteLength: number;
  checksum: string;
}>;
const expected = (assetId: string): Observation => {
  const asset = manifest.assets.find((item) => item.assetId === assetId);
  return asset === undefined
    ? { byteLength: 0, checksum: '' }
    : { byteLength: asset.byteLength, checksum: asset.checksum.value };
};

/** 保存済みの実体を assetId ごとに持つ。save は検証に通った実体だけを残す AssetStore と同じ観測を返す。 */
const assetDisk = (
  initial: Readonly<Record<string, Observation>> = {},
  availableBytes = 1_000_000,
) => {
  const files = new Map(Object.entries(initial));
  const saveFailures = new Map<string, StorageError>();
  const saved: string[] = [];
  const open = (): StorageResult<AssetStore> =>
    ok({
      save: (assetId) => {
        const failure = saveFailures.get(assetId);
        return Promise.resolve(
          failure === undefined
            ? ((): StorageResult<void> => {
                files.set(assetId, expected(assetId));
                saved.push(assetId);
                return ok(undefined);
              })()
            : storageError(failure),
        );
      },
      read: () => Promise.resolve(storageError('missing')),
      inspect: () =>
        Promise.resolve(
          ok({
            inventory: [...files].map(([assetId, observed]) => ({
              assetId,
              byteLength: observed.byteLength,
              checksum: { algorithm: 'sha256', value: observed.checksum },
            })),
            availableBytes,
          }),
        ),
      assess: () => Promise.resolve(storageError('storage-unavailable')),
    });
  return { files, saveFailures, saved, open };
};

/** active / pending の 2 つの slot と、保存済みの Manifest。 */
const packageDisk = (
  slots: Partial<Record<PackageSlot, InstallationManifest>> = {},
) => {
  const state: Partial<Record<PackageSlot, InstallationManifest>> = {
    ...slots,
  };
  const staged: string[] = [];
  const store: PackageStore = {
    stage: (input) => {
      const staging = parse(input);
      state.pending = staging;
      staged.push(staging.packageVersion);
      return Promise.resolve(ok(undefined));
    },
    read: (slot) => Promise.resolve(ok(state[slot])),
    promote: () => Promise.resolve(storageError('missing')),
    discardPending: () => {
      delete state.pending;
      return Promise.resolve(ok(undefined));
    },
    list: () => Promise.resolve(ok({ packages: [], unreadable: 0 })),
  };
  return { state, staged, store };
};

/** 配布元の偽物。asset の取得失敗を assetId ごとに差し込める。 */
const distribution = (
  served: DistributionResult<InstallationManifest> = ok(manifest),
) => {
  const assetFailures = new Map<
    string,
    Readonly<{ error: DistributionError; status?: number }>
  >();
  const requested: string[] = [];
  const client: DistributionClient = {
    package: () => Promise.resolve(served),
    asset: (_, assetId) => {
      requested.push(assetId);
      const failure = assetFailures.get(assetId);
      return Promise.resolve(
        failure === undefined
          ? ok(new Blob([assetId]))
          : { kind: 'error', ...failure },
      );
    },
  };
  return { assetFailures, requested, client };
};

const setup = (
  options: Readonly<{
    served?: DistributionResult<InstallationManifest>;
    files?: Readonly<Record<string, Observation>>;
    availableBytes?: number;
    slots?: Partial<Record<PackageSlot, InstallationManifest>>;
    appVersion?: AppVersion;
    shell?: () => Promise<boolean>;
  }> = {},
) => {
  const assets = assetDisk(options.files, options.availableBytes);
  const packages = packageDisk(options.slots);
  const remote = distribution(options.served);
  const run = (): Promise<PreparationResult> =>
    prepare(
      {
        client: remote.client,
        appVersion: options.appVersion ?? appVersion,
        appShellAvailable: options.shell ?? (() => Promise.resolve(true)),
        packages: packages.store,
        openAssets: assets.open,
      },
      new AbortController().signal,
    );
  return { assets, packages, remote, run };
};

describe('prepare', () => {
  it('fetches every required asset of a fresh package in manifest order and leaves it pending', async () => {
    const { assets, packages, remote, run } = setup({
      slots: { active: newer },
    });

    await expect(run()).resolves.toEqual({
      kind: 'prepared',
      value: {
        packageVersion: manifest.packageVersion,
        activation: 'on-restart',
        fetchedAssetIds: [audio, artwork],
      },
    });
    expect(remote.requested).toEqual([audio, artwork]);
    expect(assets.saved).toEqual([audio, artwork]);
    expect(packages.state.active).toBe(newer);
    expect(packages.state.pending?.packageVersion).toBe(
      manifest.packageVersion,
    );
  });

  it('does not count catalogue tracks without audio as assets to fetch', async () => {
    const { remote, run } = setup();

    await run();

    expect(remote.requested).toEqual(
      manifest.assets.filter((asset) => asset.required).map((a) => a.assetId),
    );
  });

  it('reuses a verified asset and refetches only the missing and the corrupt ones', async () => {
    const intact = setup({ files: { [audio]: expected(audio) } });
    await intact.run();
    expect(intact.remote.requested).toEqual([artwork]);

    const damaged = setup({
      files: {
        [audio]: expected(audio),
        [artwork]: { ...expected(artwork), checksum: 'c'.repeat(64) },
      },
    });
    await damaged.run();
    expect(damaged.remote.requested).toEqual([artwork]);
  });

  it('keeps assets saved before an interruption and does not fetch them again on retry', async () => {
    const { assets, remote, run } = setup();
    remote.assetFailures.set(artwork, { error: 'network' });

    await expect(run()).resolves.toEqual({
      kind: 'failed',
      failure: {
        stage: 'asset',
        assetId: artwork,
        mediaType: 'image/png',
        error: 'network',
      },
    });
    expect(assets.saved).toEqual([audio]);

    remote.assetFailures.clear();
    remote.requested.length = 0;
    await expect(run()).resolves.toMatchObject({
      kind: 'prepared',
      value: { fetchedAssetIds: [artwork] },
    });
    expect(remote.requested).toEqual([artwork]);
  });

  it('stops for re-authentication when the token expires mid-preparation, keeping what was saved', async () => {
    const { assets, packages, remote, run } = setup({
      slots: { active: newer },
    });
    remote.assetFailures.set(artwork, { error: 'unauthorized', status: 401 });

    await expect(run()).resolves.toMatchObject({
      kind: 'failed',
      failure: { stage: 'asset', error: 'unauthorized', status: 401 },
    });
    expect(assets.saved).toEqual([audio]);
    expect(packages.state.active).toBe(newer);
  });

  it('reports an expired signed URL for that asset only', async () => {
    const { remote, run } = setup();
    remote.assetFailures.set(audio, { error: 'source-rejected', status: 403 });

    await expect(run()).resolves.toEqual({
      kind: 'failed',
      failure: {
        stage: 'asset',
        assetId: audio,
        mediaType: 'audio/flac',
        error: 'source-rejected',
        status: 403,
      },
    });
    expect(remote.requested).toEqual([audio]);
  });

  it('does not treat a required asset the client cannot source as prepared', async () => {
    const { remote, run } = setup();
    remote.assetFailures.set(artwork, { error: 'unsupported-asset' });

    await expect(run()).resolves.toMatchObject({
      kind: 'failed',
      failure: { stage: 'asset', assetId: artwork, error: 'unsupported-asset' },
    });
  });

  it('reports a quota failure while saving as a failure of that asset', async () => {
    const { assets, run } = setup();
    assets.saveFailures.set(audio, 'quota-exceeded');

    await expect(run()).resolves.toMatchObject({
      kind: 'failed',
      failure: { stage: 'asset', assetId: audio, error: 'quota-exceeded' },
    });
  });

  it('refuses to start when the missing assets do not fit, without staging or fetching', async () => {
    const { packages, remote, run } = setup({
      files: { [audio]: expected(audio) },
      availableBytes: 255,
    });

    await expect(run()).resolves.toEqual({
      kind: 'failed',
      failure: { stage: 'capacity', requiredBytes: 256, availableBytes: 255 },
    });
    expect(packages.staged).toEqual([]);
    expect(remote.requested).toEqual([]);
  });

  it('needs no free space when every required asset is already verified', async () => {
    const { run } = setup({
      files: { [audio]: expected(audio), [artwork]: expected(artwork) },
      availableBytes: 0,
    });

    await expect(run()).resolves.toMatchObject({
      kind: 'prepared',
      value: { fetchedAssetIds: [] },
    });
  });

  it('never stages a package this app version cannot run', async () => {
    const { packages, remote, run } = setup({ appVersion: [2, 0, 0] });

    await expect(run()).resolves.toEqual({
      kind: 'failed',
      failure: {
        stage: 'incompatible',
        packageVersion: manifest.packageVersion,
        compatibleAppVersion: manifest.compatibleAppVersion,
      },
    });
    expect(packages.staged).toEqual([]);
    expect(remote.requested).toEqual([]);
  });

  it('stops before touching storage when the package cannot be fetched', async () => {
    const { packages, run } = setup({
      served: { kind: 'error', error: 'unauthorized', status: 401 },
    });

    await expect(run()).resolves.toEqual({
      kind: 'failed',
      failure: { stage: 'package', error: 'unauthorized', status: 401 },
    });
    expect(packages.staged).toEqual([]);
  });

  it('repairs the active package in place and drops an older pending that would replace it', async () => {
    const { packages, run } = setup({
      slots: { active: manifest, pending: newer },
      files: { [audio]: expected(audio) },
    });

    await expect(run()).resolves.toEqual({
      kind: 'prepared',
      value: {
        packageVersion: manifest.packageVersion,
        activation: 'current',
        fetchedAssetIds: [artwork],
      },
    });
    expect(packages.staged).toEqual([]);
    expect(packages.state.pending).toBeUndefined();
    expect(packages.state.active).toBe(manifest);
  });

  it('is not ready while the shell of the same generation is incomplete', async () => {
    const unavailable = setup({ shell: () => Promise.resolve(false) });
    await expect(unavailable.run()).resolves.toEqual({
      kind: 'failed',
      failure: { stage: 'shell' },
    });

    const unanswered = setup({
      shell: () => Promise.reject(new Error('no worker')),
    });
    await expect(unanswered.run()).resolves.toEqual({
      kind: 'failed',
      failure: { stage: 'shell' },
    });
  });

  it('judges completion from a fresh observation, not from what it believes it saved', async () => {
    const { assets, packages, remote } = setup();
    const open = assets.open;
    const vanishing = (): StorageResult<AssetStore> => {
      const opened = open();
      return opened.kind === 'ok'
        ? ok({
            ...opened.value,
            save: async (assetId, blob, signal) => {
              const result = await opened.value.save(assetId, blob, signal);
              assets.files.delete(artwork);
              return result;
            },
          })
        : opened;
    };

    await expect(
      prepare(
        {
          client: remote.client,
          appVersion,
          appShellAvailable: () => Promise.resolve(true),
          packages: packages.store,
          openAssets: vanishing,
        },
        new AbortController().signal,
      ),
    ).resolves.toEqual({
      kind: 'failed',
      failure: {
        stage: 'incomplete',
        missingAssetIds: [artwork],
        corruptAssetIds: [],
      },
    });
  });

  it('stops without fetching when the candidate cannot be staged', async () => {
    const failing = setup();
    const broken: PackageStore = {
      ...failing.packages.store,
      stage: () => Promise.resolve(storageError('storage-unavailable')),
    };

    await expect(
      prepare(
        {
          client: failing.remote.client,
          appVersion,
          appShellAvailable: () => Promise.resolve(true),
          packages: broken,
          openAssets: failing.assets.open,
        },
        new AbortController().signal,
      ),
    ).resolves.toEqual({
      kind: 'failed',
      failure: { stage: 'storage', error: 'storage-unavailable' },
    });
    expect(failing.remote.requested).toEqual([]);
  });
});
