import { describe, expect, it } from 'vitest';
import { assessReadiness, projectManifest } from './index';
import type { ManifestProjectionInput } from './index';

const firstTrack = {
  trackId: '01900000-0000-7000-8000-000000000001',
  trackNo: 1,
  title: 'First set',
  artistDisplayName: null,
  artistSortKey: null,
  tunes: [
    {
      seq: 1,
      tuneTitle: 'Traditional',
      composerCreditOverride: null,
      arrangerCreditOverride: null,
      linkUrl: null,
    },
  ],
};
const secondTrack = {
  ...firstTrack,
  trackId: '01900000-0000-7000-8000-000000000002',
  trackNo: 3,
  title: 'Last air',
  tunes: [],
};
const sourceAlbum = {
  albumId: '01900000-0000-7000-8000-000000000010',
  title: 'Dance suite',
  coverImageKey: 'private/storage/key',
  coverImageUrl: '/assets/public-cover',
  externalAudios: [
    {
      externalAudioId: 'external-1',
      displayOrder: 1,
      url: 'https://soundcloud.com/example/album',
    },
  ],
  tracks: [secondTrack, firstTrack],
};
const firstAudio = {
  assetId: 'flac-first',
  mediaType: 'audio/flac',
  byteLength: 100,
  checksum: { algorithm: 'fixture-hash', value: 'first' },
  required: true,
};
const secondAudio = {
  ...firstAudio,
  assetId: 'flac-last',
  byteLength: 200,
  checksum: { algorithm: 'fixture-hash', value: 'last' },
};
const artwork = {
  ...firstAudio,
  assetId: 'cover',
  mediaType: 'image/png',
  required: false,
};
const input: ManifestProjectionInput = {
  packageVersion: 'event-2026-09',
  compatibleAppVersion: { minInclusive: [1, 1, 0], maxExclusive: [2, 0, 0] },
  albums: [sourceAlbum],
  trackAudioBindings: [
    { trackId: secondTrack.trackId, assetId: secondAudio.assetId },
    {
      trackId: firstTrack.trackId,
      assetId: firstAudio.assetId,
      durationSeconds: 123.5,
    },
  ],
  albumArtworkBindings: [
    { albumId: sourceAlbum.albumId, assetId: artwork.assetId },
  ],
  presentationAssetIds: [],
  assets: [firstAudio, secondAudio, artwork],
};

const projectedManifest = (source: unknown) => {
  const result = projectManifest(source);
  expect(result.kind).toBe('manifest');
  return result.kind === 'manifest' ? result.manifest : undefined;
};

describe('v1.0 Album/Track projection', () => {
  it('canonical IDとタイトルを保ち、trackNo順に配布音源を対応付ける', () => {
    expect(projectedManifest(input)).toEqual({
      schemaVersion: 1,
      packageVersion: 'event-2026-09',
      compatibleAppVersion: input.compatibleAppVersion,
      albums: [
        {
          albumId: sourceAlbum.albumId,
          title: 'Dance suite',
          artworkAssetId: 'cover',
          tracks: [
            {
              trackId: firstTrack.trackId,
              title: 'First set',
              audioAssetId: 'flac-first',
              durationSeconds: 123.5,
            },
            {
              trackId: secondTrack.trackId,
              title: 'Last air',
              audioAssetId: 'flac-last',
            },
          ],
        },
      ],
      presentationAssetIds: [],
      assets: [firstAudio, secondAudio, artwork],
    });
    expect(sourceAlbum.tracks.map((track) => track.trackNo)).toEqual([3, 1]);
  });

  it('独立したreadonly snapshotを返す', () => {
    const manifest = projectedManifest(input);
    expect(manifest?.assets).not.toBe(input.assets);
    expect(manifest?.assets[0]?.checksum).not.toBe(firstAudio.checksum);
    expect(Object.isFrozen(manifest)).toBe(true);
    expect(Object.isFrozen(manifest?.albums[0]?.tracks)).toBe(true);
    expect(Object.isFrozen(manifest?.albums[0]?.tracks[0])).toBe(true);
    expect(Object.isFrozen(manifest?.assets[0]?.checksum)).toBe(true);
    expect(Object.isFrozen(firstAudio.checksum)).toBe(false);
  });

  it('アルバム選択順を保ち、trackNoはアルバムごとに扱う', () => {
    const otherAlbum = {
      albumId: 'other',
      title: 'Other',
      tracks: [{ trackId: 'other-track', trackNo: 1, title: 'Other track' }],
    };
    const manifest = projectedManifest({
      ...input,
      albums: [otherAlbum, sourceAlbum],
      trackAudioBindings: [
        ...input.trackAudioBindings,
        { trackId: 'other-track', assetId: firstAudio.assetId },
      ],
    });
    expect(manifest?.albums.map((album) => album.albumId)).toEqual([
      'other',
      sourceAlbum.albumId,
    ]);
  });

  it('artwork対応表なしでも生成し、URLやstorage keyをIDに転用しない', () => {
    const manifest = projectedManifest({ ...input, albumArtworkBindings: [] });
    expect(manifest?.albums[0]?.artworkAssetId).toBeUndefined();
    expect(JSON.stringify(manifest)).not.toContain('storage/key');
    expect(JSON.stringify(manifest)).not.toContain('soundcloud');
    expect(JSON.stringify(manifest)).not.toContain('tunes');
  });

  it('生成物で実体inventoryを照合し、optional欠落とrequired破損を区別できる', () => {
    const manifest = projectedManifest(input);
    const local = {
      appVersion: [1, 1, 0],
      appShellAvailable: true,
      availableBytes: 0,
      inventory: [firstAudio, secondAudio],
    };
    expect(assessReadiness(manifest, local)).toMatchObject({
      kind: 'assessed',
      offlineReady: true,
    });
    expect(
      assessReadiness(manifest, {
        ...local,
        inventory: [
          firstAudio,
          {
            ...secondAudio,
            checksum: { algorithm: 'fixture-hash', value: 'corrupt' },
          },
        ],
      }),
    ).toMatchObject({
      kind: 'assessed',
      offlineReady: false,
      corruptAssetIds: ['flac-last'],
      requiredDownloadBytes: 200,
      storageCapacitySufficient: false,
    });
  });

  it('不足した音源対応を曲ごと黙って除外しない', () => {
    const result = projectManifest({
      ...input,
      trackAudioBindings: input.trackAudioBindings.slice(1),
    });
    expect(result.kind).toBe('invalid-projection');
    expect(result.kind === 'invalid-projection' ? result.errors : []).toContain(
      ': missing track audio binding',
    );
  });

  it.each([
    {
      trackAudioBindings: [
        ...input.trackAudioBindings,
        input.trackAudioBindings[0],
      ],
    },
    {
      albumArtworkBindings: [
        ...input.albumArtworkBindings,
        input.albumArtworkBindings[0],
      ],
    },
    {
      trackAudioBindings: [
        ...input.trackAudioBindings,
        { trackId: 'unknown', assetId: 'flac-first' },
      ],
    },
    { albumArtworkBindings: [{ albumId: 'unknown', assetId: 'cover' }] },
    {
      albums: [
        {
          ...sourceAlbum,
          tracks: [firstTrack, { ...secondTrack, trackNo: 1 }],
        },
      ],
    },
    {
      albums: [
        {
          ...sourceAlbum,
          tracks: [{ trackNo: 1, title: 'Public API track without ID' }],
        },
      ],
    },
  ])('曖昧または参照先不明な入力を拒否する: %j', (patch) => {
    expect(projectManifest({ ...input, ...patch })).toMatchObject({
      kind: 'invalid-projection',
    });
  });

  it.each([0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1])(
    '不正なtrackNoを拒否する: %s',
    (trackNo) => {
      expect(
        projectManifest({
          ...input,
          albums: [
            {
              ...sourceAlbum,
              tracks: [{ ...firstTrack, trackNo }, secondTrack],
            },
          ],
        }),
      ).toMatchObject({ kind: 'invalid-projection' });
    },
  );

  it.each([
    { assets: [firstAudio, artwork] },
    { assets: [{ ...firstAudio, required: false }, secondAudio, artwork] },
    {
      assets: [{ ...firstAudio, mediaType: 'image/png' }, secondAudio, artwork],
    },
    { assets: [firstAudio, secondAudio] },
    { assets: [...input.assets, firstAudio] },
    { presentationAssetIds: ['unknown'] },
    {
      compatibleAppVersion: {
        minInclusive: [2, 0, 0],
        maxExclusive: [1, 1, 0],
      },
    },
    { albums: [sourceAlbum, sourceAlbum] },
    { albums: [sourceAlbum, { ...sourceAlbum, albumId: 'other' }] },
  ])('完成Manifestの制約も通し、不完全な配布物を成功にしない: %j', (patch) => {
    expect(projectManifest({ ...input, ...patch })).toMatchObject({
      kind: 'invalid-manifest',
    });
  });

  it.each([null, {}, { ...input, albums: null }])(
    '外部入力の不正を例外でなく結果にする: %j',
    (source) => {
      expect(projectManifest(source)).toMatchObject({
        kind: 'invalid-projection',
      });
    },
  );
});
