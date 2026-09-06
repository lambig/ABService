import { describe, expect, it } from 'vitest';
import { assessReadiness, parseManifest } from './index';

const checksum = { algorithm: 'fixture-hash', value: 'abc' };
const audio = {
  assetId: 'audio-1',
  mediaType: 'audio/flac',
  byteLength: 100,
  checksum,
  required: true,
};
const manifest = {
  schemaVersion: 1,
  packageVersion: 'event-2026-09',
  compatibleAppVersion: { minInclusive: [1, 1, 0], maxExclusive: [2, 0, 0] },
  albums: [
    {
      albumId: 'album-1',
      title: 'Album',
      tracks: [{ trackId: 'track-1', title: 'Track', audioAssetId: 'audio-1' }],
    },
  ],
  presentationAssetIds: [],
  assets: [audio],
};
const local = {
  appVersion: [1, 1, 0],
  appShellAvailable: true,
  availableBytes: 0,
  inventory: [{ assetId: 'audio-1', byteLength: 100, checksum }],
};

describe('installation readiness', () => {
  it('全required検証済みなら追加容量0でもready', () => {
    expect(assessReadiness(manifest, local)).toMatchObject({
      kind: 'assessed',
      offlineReady: true,
      packageComplete: true,
      requiredDownloadBytes: 0,
    });
  });
  it('不足と容量不足を独立に返す', () => {
    expect(
      assessReadiness(manifest, { ...local, inventory: [] }),
    ).toMatchObject({
      missingAssetIds: ['audio-1'],
      corruptAssetIds: [],
      storageCapacitySufficient: false,
      requiredAssetsPresent: false,
      offlineReady: false,
      requiredDownloadBytes: 100,
    });
  });
  it('容量が足りても未ダウンロードならreadyにならない', () => {
    expect(
      assessReadiness(manifest, {
        ...local,
        inventory: [],
        availableBytes: 100,
      }),
    ).toMatchObject({ storageCapacitySufficient: true, offlineReady: false });
  });
  it.each([
    { byteLength: 99, checksum },
    { byteLength: 100, checksum: { ...checksum, value: 'corrupt' } },
    { byteLength: 100, checksum: { ...checksum, algorithm: 'other' } },
  ])('サイズ・digest・algorithmの不一致はcorrupt: %j', (observation) => {
    expect(
      assessReadiness(manifest, {
        ...local,
        inventory: [{ assetId: 'audio-1', ...observation }],
      }),
    ).toMatchObject({
      corruptAssetIds: ['audio-1'],
      checksumsValid: false,
      offlineReady: false,
    });
  });
  it('optionalの欠落はreadyを妨げない', () => {
    expect(
      assessReadiness(
        {
          ...manifest,
          assets: [
            audio,
            {
              ...audio,
              assetId: 'art',
              mediaType: 'image/png',
              required: false,
            },
          ],
        },
        local,
      ),
    ).toMatchObject({ offlineReady: true });
  });
  it.each([
    [1, 0, 9],
    [2, 0, 0],
  ])('互換範囲外: %j', (...appVersion) => {
    expect(assessReadiness(manifest, { ...local, appVersion })).toMatchObject({
      appCompatible: false,
      offlineReady: false,
      packageComplete: true,
    });
  });
  it('app shellがなければassets完備でもreadyにならない', () => {
    expect(
      assessReadiness(manifest, { ...local, appShellAvailable: false }),
    ).toMatchObject({ offlineReady: false, packageComplete: true });
  });
  it('未対応schemaと不正manifestを分ける', () => {
    expect(parseManifest({ ...manifest, schemaVersion: 2 })).toEqual({
      kind: 'unsupported-schema',
    });
    expect(parseManifest(null)).toMatchObject({ kind: 'invalid-manifest' });
  });
  it.each([
    { assets: [] },
    { assets: [audio, audio] },
    { assets: [{ ...audio, required: false }] },
    { assets: [{ ...audio, byteLength: -1 }] },
    { presentationAssetIds: ['missing'] },
    { albums: [manifest.albums[0], manifest.albums[0]] },
    {
      compatibleAppVersion: {
        minInclusive: [2, 0, 0],
        maxExclusive: [1, 0, 0],
      },
    },
  ])('参照切れ・重複・不正値を拒否: %j', (change) => {
    expect(parseManifest({ ...manifest, ...change })).toMatchObject({
      kind: 'invalid-manifest',
    });
  });
  it.each([-1, NaN, Infinity])('不正容量を拒否: %s', (availableBytes) => {
    expect(assessReadiness(manifest, { ...local, availableBytes })).toEqual({
      kind: 'invalid-environment',
    });
  });
  it('inventory重複で都合のよい観測値を選ばない', () => {
    expect(
      assessReadiness(manifest, {
        ...local,
        inventory: [...local.inventory, ...local.inventory],
      }),
    ).toEqual({ kind: 'invalid-environment' });
  });
  it('検証済みmanifestは入れ子も凍結した独立snapshot', () => {
    const result = parseManifest(manifest);
    expect(result.kind).toBe('manifest');
    expect(
      result.kind === 'manifest' &&
        Object.isFrozen(result.manifest.assets[0]?.checksum),
    ).toBe(true);
    expect(result.kind === 'manifest' ? result.manifest : null).not.toBe(manifest);
  });
});
