import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { assessReadiness, getPlaybackItems, parseManifest } from './index';

type Json = Record<string, unknown>;

/** backend の配布応答と同じ形の見本。backend の統合試験も同じファイルとキー構成を照合する。 */
const example = JSON.parse(
  readFileSync(
    new URL('../fixtures/manifest-v3.example.json', import.meta.url),
    'utf8',
  ),
) as Json;
const exampleAlbum = (example.albums as Json[])[0] as Json;
const optionalKeys = [
  'releaseDate',
  'catalogNumber',
  'description',
  'descriptionFormat',
  'originalWorkNote',
  'artworkAssetId',
];
const v2Keys = ['albumId', 'title', 'artworkAssetId', 'tracks'];
const withAlbum = (album: Json) => ({ ...example, albums: [album] });
const pick = (album: Json, keep: (key: string) => boolean) =>
  Object.fromEntries(Object.entries(album).filter(([key]) => keep(key)));
/** 配布される JSON と同じく往復させ、undefined の項目を「キーが無い」状態にする。 */
const downloaded = (manifest: Json): unknown =>
  JSON.parse(JSON.stringify(manifest));
const local = {
  appVersion: [1, 10, 0],
  appShellAvailable: true,
  availableBytes: 0,
  inventory: example.assets,
};

describe('schema v3 presentation metadata', () => {
  it('accepts the backend-shaped example with every presentation field', () => {
    const result = parseManifest(example);
    expect(result).toMatchObject({
      kind: 'manifest',
      manifest: {
        schemaVersion: 3,
        albums: [
          {
            artistDisplayName: '見本の名義',
            releaseDate: '2026-01-01',
            catalogNumber: 'AB-001',
            descriptionFormat: 'MARKDOWN',
            originalWorkNote: '見本の原作の出典',
          },
        ],
      },
    });
    expect(assessReadiness(example, local)).toMatchObject({
      offlineReady: true,
    });
  });

  it('keeps playback items separate from metadata tracks and keeps the crossfade identity', () => {
    const result = parseManifest(example);
    const items =
      result.kind === 'manifest' ? getPlaybackItems(result.manifest) : [];
    expect(items).toEqual(example.playbackItems);
    expect(items.map((item) => item.kind)).toEqual(['album-crossfade']);
  });

  it('accepts an album whose optional presentation fields are omitted', () => {
    const minimal = pick(exampleAlbum, (key) => optionalKeys.every((k) => k !== key));
    expect(
      parseManifest({ ...withAlbum(minimal), presentationAssetIds: [] }),
    ).toMatchObject({ kind: 'manifest' });
  });

  it('accepts empty strings as canonical values', () => {
    expect(
      parseManifest(
        withAlbum({
          ...exampleAlbum,
          catalogNumber: '',
          description: '',
          originalWorkNote: '',
        }),
      ),
    ).toMatchObject({ kind: 'manifest' });
  });

  it.each([
    { artistDisplayName: undefined },
    { artistDisplayName: '' },
    { releaseDate: null },
    { releaseDate: '2026/01/01' },
    { catalogNumber: null },
    { description: null },
    { descriptionFormat: 'HTML' },
    { descriptionFormat: undefined },
    { description: undefined },
    { originalWorkNote: null },
    { artworkAssetId: null },
    { layout: 'hero' },
    { descriptionHtml: '<p>baked</p>' },
  ])('rejects invalid or unknown presentation fields: %j', (patch) => {
    expect(
      parseManifest(downloaded(withAlbum({ ...exampleAlbum, ...patch }))),
    ).toMatchObject({ kind: 'invalid-manifest' });
  });

  it('rejects v2-shaped albums under schema v3 and v3 fields under schema v2', () => {
    const v2Album = pick(exampleAlbum, (key) => v2Keys.includes(key));
    expect(parseManifest(withAlbum(v2Album))).toMatchObject({
      kind: 'invalid-manifest',
    });
    expect(parseManifest({ ...example, schemaVersion: 2 })).toMatchObject({
      kind: 'invalid-manifest',
    });
    expect(parseManifest({ ...example, schemaVersion: 4 })).toEqual({
      kind: 'unsupported-schema',
    });
  });
});
