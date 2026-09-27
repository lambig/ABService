import { describe, expect, it } from 'vitest';
import {
  assessReadiness,
  getPlaybackItems,
  parseManifest,
  projectManifestV2,
} from './index';
import type { ManifestProjectionV2Input } from './index';

const audio = {
  assetId: 'demo-audio',
  mediaType: 'audio/flac',
  byteLength: 30_000_000,
  checksum: { algorithm: 'sha256' as const, value: 'a'.repeat(64) },
  required: true,
};
const font = {
  ...audio,
  assetId: 'font',
  mediaType: 'font/woff2',
  byteLength: 100,
};
const crossfade = {
  playbackItemId: 'demo-selection',
  kind: 'album-crossfade' as const,
  albumId: 'album',
  title: 'Album crossfade',
  audioAssetId: audio.assetId,
};
const song = {
  playbackItemId: 'song-selection',
  kind: 'track' as const,
  albumId: 'album',
  trackId: 'track-1',
  title: 'First song',
  audioAssetId: audio.assetId,
};
const input: ManifestProjectionV2Input = {
  packageVersion: 'fixture-v2',
  compatibleAppVersion: {
    minInclusive: [1, 10, 0],
    maxExclusive: [2, 0, 0],
  },
  albums: [
    {
      albumId: 'album',
      title: 'Album',
      tracks: [
        { trackId: 'track-2', trackNo: 2, title: 'Second song' },
        { trackId: 'track-1', trackNo: 1, title: 'First song' },
      ],
    },
    {
      albumId: 'other',
      title: 'Other album',
      tracks: [{ trackId: 'other-track', trackNo: 1, title: 'Other song' }],
    },
  ],
  playbackItems: [crossfade],
  albumArtworkBindings: [],
  assets: [audio, font],
  presentationAssetIds: [font.assetId],
};
const projected = (source: unknown = input) => {
  const result = projectManifestV2(source);
  expect(result.kind).toBe('manifest');
  return result.kind === 'manifest' ? result.manifest : undefined;
};
const local = {
  appVersion: [1, 10, 0],
  appShellAvailable: true,
  availableBytes: 0,
  inventory: [audio, font],
};

describe('schema v2 playback items', () => {
  it.each([[crossfade], [song], [song, crossfade]])(
    'supports the explicit selection order: %j',
    (...items) => {
      const manifest = projected({ ...input, playbackItems: items });
      expect(manifest?.playbackItems).toEqual(items);
      expect(manifest?.albums[0]?.tracks).toEqual([
        { trackId: 'track-1', trackNo: 1, title: 'First song' },
        { trackId: 'track-2', trackNo: 2, title: 'Second song' },
      ]);
      expect(manifest?.albums.map((a) => a.albumId)).toEqual([
        'album',
        'other',
      ]);
      expect(
        manifest?.albums
          .flatMap((a) => a.tracks)
          .some((t) => 'audioAssetId' in t),
      ).toBe(false);
      expect(assessReadiness(manifest, local)).toMatchObject({
        offlineReady: true,
      });
      expect(input.albums[0]?.tracks.map((t) => t.trackNo)).toEqual([2, 1]);
    },
  );

  it('adding a song preserves the existing demo and canonical identities', () => {
    const before = projected();
    const after = projected({
      ...input,
      playbackItems: [crossfade, song],
    });
    expect(after?.albums).toEqual(before?.albums);
    expect(after?.playbackItems[0]).toEqual(before?.playbackItems[0]);
    expect(after === undefined ? [] : getPlaybackItems(after)).toEqual([
      crossfade,
      song,
    ]);
    expect(before?.playbackItems[0]).not.toHaveProperty('trackId');
  });

  it('returns an independent, deeply immutable snapshot and strips source-only data', () => {
    const manifest = projected({
      ...input,
      token: 'invalid-fixture-token',
      compatibleAppVersion: {
        ...input.compatibleAppVersion,
        sourceOnly: true,
      },
      albums: input.albums.map((a) => ({
        ...a,
        externalUrl: 'https://example.invalid/demo',
        tracks: a.tracks.map((t) => ({ ...t, sourceOnly: true })),
      })),
      assets: input.assets.map((a) => ({
        ...a,
        downloadUrl: 'https://example.invalid/signed',
        checksum: { ...a.checksum, sourceOnly: true },
      })),
    });
    expect(manifest).not.toBe(input);
    expect(manifest?.playbackItems[0]).not.toBe(crossfade);
    expect(manifest?.assets[0]?.checksum).not.toBe(audio.checksum);
    expect(Object.isFrozen(manifest?.playbackItems)).toBe(true);
    expect(Object.isFrozen(manifest?.playbackItems[0])).toBe(true);
    expect(Object.isFrozen(manifest?.albums[0]?.tracks[0])).toBe(true);
    expect(Object.isFrozen(manifest?.assets[0]?.checksum)).toBe(true);
    expect(Object.isFrozen(audio.checksum)).toBe(false);
    expect(JSON.stringify(manifest)).not.toContain('example.invalid');
    expect(JSON.stringify(manifest)).not.toContain('token');
    expect(JSON.stringify(manifest)).not.toContain('sourceOnly');
    expect(parseManifest(manifest)).toMatchObject({
      kind: 'manifest',
      manifest,
    });
  });

  it.each([
    { playbackItems: [{ ...crossfade, albumId: 'missing' }] },
    { playbackItems: [{ ...song, trackId: 'missing' }] },
    { playbackItems: [{ ...song, trackId: 'other-track' }] },
    { playbackItems: [crossfade, crossfade] },
    { playbackItems: [{ ...crossfade, audioAssetId: 'missing' }] },
    { assets: [font] },
    { assets: [{ ...audio, required: false }, font] },
    { assets: [{ ...audio, mediaType: 'image/png' }, font] },
    { assets: [{ ...audio, mediaType: 'audio/' }, font] },
    { assets: [audio, audio, font] },
    { albums: [input.albums[0], input.albums[0]] },
    {
      albums: [
        ...input.albums,
        { ...input.albums[0], albumId: 'duplicate-tracks' },
      ],
    },
    {
      albums: [
        {
          ...input.albums[0],
          tracks: [
            { trackId: 'track-1', title: 'First', trackNo: 1 },
            { trackId: 'track-2', title: 'Second', trackNo: 1 },
          ],
        },
      ],
    },
    { presentationAssetIds: ['missing'] },
    { albumArtworkBindings: [{ albumId: 'album', assetId: 'missing' }] },
    {
      compatibleAppVersion: {
        minInclusive: [1, 1, 0],
        maxExclusive: [2, 0, 0],
      },
    },
    {
      compatibleAppVersion: {
        minInclusive: [2, 0, 0],
        maxExclusive: [1, 10, 0],
      },
    },
    { assets: [{ ...audio, byteLength: Number.MAX_SAFE_INTEGER }, font] },
  ])('rejects invalid completed packages: %j', (patch) => {
    expect(projectManifestV2({ ...input, ...patch })).toMatchObject({
      kind: 'invalid-manifest',
    });
  });

  it.each([
    { playbackItems: [{ ...crossfade, trackId: 'track-1' }] },
    { playbackItems: [{ ...crossfade, kind: 'track' }] },
    { playbackItems: [{ ...crossfade, kind: 'unknown' }] },
    { playbackItems: [{ ...crossfade, audioAssetId: undefined }] },
    { playbackItems: [{ ...crossfade, playbackItemId: '' }] },
    { playbackItems: [{ ...crossfade, durationSeconds: Infinity }] },
    { playbackItems: [{ ...crossfade, durationSeconds: -1 }] },
    {
      albumArtworkBindings: [{ albumId: 'missing', assetId: audio.assetId }],
    },
    {
      albumArtworkBindings: [
        { albumId: 'album', assetId: audio.assetId },
        { albumId: 'album', assetId: audio.assetId },
      ],
    },
    { assets: [{ ...audio, byteLength: 0 }, font] },
    { assets: [{ ...audio, byteLength: -1 }, font] },
    {
      assets: [{ ...audio, byteLength: Number.MAX_SAFE_INTEGER + 1 }, font],
    },
    {
      assets: [
        {
          ...audio,
          checksum: { algorithm: 'md5', value: 'a'.repeat(64) },
        },
        font,
      ],
    },
    {
      assets: [
        {
          ...audio,
          checksum: { algorithm: 'sha256', value: 'not-a-digest' },
        },
        font,
      ],
    },
  ])('rejects incomplete or inconsistent projection input: %j', (patch) => {
    expect(projectManifestV2({ ...input, ...patch })).toMatchObject({
      kind: 'invalid-projection',
    });
  });

  it('validates downloaded JSON as strictly as generated packages', () => {
    const manifest = projected();
    expect(
      parseManifest({
        ...manifest,
        playbackItems: [{ ...song, trackId: 'other-track' }],
      }),
    ).toMatchObject({ kind: 'invalid-manifest' });
    expect(
      parseManifest({
        ...manifest,
        playbackItems: [{ ...crossfade, trackId: 'track-1' }],
      }),
    ).toMatchObject({ kind: 'invalid-manifest' });
    expect(
      parseManifest({
        ...manifest,
        albums: [
          {
            albumId: 'album',
            title: 'Album',
            tracks: [
              {
                trackId: 'track-1',
                trackNo: 1,
                title: 'Song',
                audioAssetId: audio.assetId,
              },
            ],
          },
        ],
      }),
    ).toMatchObject({ kind: 'invalid-manifest' });
    expect(parseManifest({ ...manifest, schemaVersion: 3 })).toEqual({
      kind: 'unsupported-schema',
    });
    expect(
      assessReadiness(manifest, { ...local, appVersion: [1, 9, 9] }),
    ).toMatchObject({ appCompatible: false, offlineReady: false });
  });

  it.each([
    { token: 'invalid-fixture-token' },
    { albumArtworkBindings: [] },
    {
      compatibleAppVersion: {
        ...input.compatibleAppVersion,
        sourceOnly: true,
      },
    },
    {
      assets: [
        { ...audio, downloadUrl: 'https://example.invalid/signed' },
        font,
      ],
    },
    {
      assets: [
        { ...audio, checksum: { ...audio.checksum, sourceOnly: true } },
        font,
      ],
    },
    { albums: input.albums.map((a) => ({ ...a, sourceOnly: true })) },
    {
      albums: input.albums.map((a) => ({
        ...a,
        tracks: a.tracks.map((t) => ({ ...t, sourceOnly: true })),
      })),
    },
    { playbackItems: [{ ...crossfade, sourceOnly: true }] },
    { playbackItems: [{ ...song, sourceOnly: true }] },
  ])('rejects unknown fields in distributed v2 JSON: %j', (patch) => {
    const downloaded: unknown = JSON.parse(
      JSON.stringify({ ...projected(), ...patch }),
    );
    expect(parseManifest(downloaded)).toMatchObject({
      kind: 'invalid-manifest',
    });
    expect(assessReadiness(downloaded, local)).toMatchObject({
      kind: 'invalid-manifest',
    });
  });

  it('counts shared audio once, includes required presentation, and ignores optional missing/corrupt assets', () => {
    const optional = { ...font, assetId: 'optional', required: false };
    const manifest = projected({
      ...input,
      playbackItems: [crossfade, song],
      assets: [audio, font, optional],
    });
    expect(assessReadiness(manifest, local)).toMatchObject({
      offlineReady: true,
      requiredDownloadBytes: 0,
    });
    expect(
      assessReadiness(manifest, {
        ...local,
        inventory: [audio, font, { ...optional, byteLength: 1 }],
      }),
    ).toMatchObject({ offlineReady: true });
    expect(
      assessReadiness(manifest, { ...local, inventory: [audio] }),
    ).toMatchObject({ offlineReady: false, missingAssetIds: ['font'] });
    expect(
      assessReadiness(manifest, {
        ...local,
        inventory: [font],
        availableBytes: 30_000_000,
      }),
    ).toMatchObject({
      requiredDownloadBytes: 30_000_000,
      storageCapacitySufficient: true,
      missingAssetIds: [audio.assetId],
    });
    expect(
      assessReadiness(manifest, {
        ...local,
        inventory: [font],
        availableBytes: 29_999_999,
      }),
    ).toMatchObject({ storageCapacitySufficient: false });
    expect(
      assessReadiness(manifest, {
        ...local,
        inventory: [
          {
            ...audio,
            checksum: { ...audio.checksum, value: 'b'.repeat(64) },
          },
          font,
        ],
      }),
    ).toMatchObject({
      requiredDownloadBytes: 30_000_000,
      corruptAssetIds: [audio.assetId],
      offlineReady: false,
    });
  });
});
