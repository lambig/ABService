import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { parseManifest } from 'abservice-installation';
import type { InstallationManifest } from 'abservice-installation';
import { artworkAssetIds, toPresentationData } from './index';

/** backend の配布応答と同じ見本。作品クロスフェードだけを再生項目に持ち、収録曲は音源を持たない。 */
const example = JSON.parse(
  readFileSync(
    new URL(
      '../../installation/fixtures/manifest-v3.example.json',
      import.meta.url,
    ),
    'utf8',
  ),
) as Readonly<Record<string, unknown>>;
const [album] = example.albums as readonly Readonly<Record<string, unknown>>[];
const parse = (input: unknown): InstallationManifest => {
  const parsed = parseManifest(input);
  return parsed.kind === 'manifest'
    ? parsed.manifest
    : ((): never => {
        throw new Error(`invalid manifest: ${JSON.stringify(parsed)}`);
      })();
};
const withAlbum = (changes: Readonly<Record<string, unknown>>) =>
  parse({ ...example, albums: [{ ...album, ...changes }] });
const withoutKeys = (...keys: readonly string[]) =>
  parse({
    ...example,
    albums: [
      Object.fromEntries(
        Object.entries(album ?? {}).filter(([key]) =>
          keys.some((omitted) => omitted === key) ? false : true,
        ),
      ),
    ],
  });
const artworkId = '0192f8a0-0000-7000-8000-000000000010.png';
const artwork = new Blob(['png'], { type: 'image/png' });

describe('toPresentationData', () => {
  it('copies the album facts, renders the description and keeps tracks apart from playable items', () => {
    const data = toPresentationData(parse(example), new Map([[artworkId, artwork]]));

    expect(data.albums).toHaveLength(1);
    expect(data.albums[0]).toMatchObject({
      albumId: '0192f8a0-0000-7000-8000-000000000100',
      title: '見本の作品',
      artistDisplayName: '見本の名義',
      releaseDate: '2026-01-01',
      catalogNumber: 'AB-001',
      originalWorkNote: '見本の原作の出典',
      artwork,
      tracks: [{ trackId: '0192f8a0-0000-7000-8000-000000000200', title: '1曲目' }],
    });
    expect(data.albums[0]?.descriptionHtml).toContain('<h2>補足</h2>');
    /* The catalogue track has no audio, so the only playable item is the album crossfade. */
    expect(data.playbackItems.map((item) => item.kind)).toEqual(['album-crossfade']);
  });

  it('omits the artwork when the verified bytes were not passed', () => {
    const data = toPresentationData(parse(example), new Map());

    expect(data.albums[0]).not.toHaveProperty('artwork');
  });

  it('omits optional facts that are absent or empty rather than inventing a placeholder', () => {
    const absent = toPresentationData(
      withoutKeys(
        'releaseDate',
        'catalogNumber',
        'description',
        'descriptionFormat',
        'originalWorkNote',
      ),
      new Map(),
    );
    const empty = toPresentationData(
      withAlbum({ catalogNumber: '', description: '  ', originalWorkNote: '' }),
      new Map(),
    );

    [absent, empty].forEach((data) => {
      ['catalogNumber', 'originalWorkNote', 'descriptionHtml'].forEach((key) => {
        expect(data.albums[0]).not.toHaveProperty(key);
      });
    });
    expect(absent.albums[0]).not.toHaveProperty('releaseDate');
    expect(absent.albums[0]?.artistDisplayName).toBe('見本の名義');
  });

  it('drops embedded images and turns links into text, since the device is offline', () => {
    const data = toPresentationData(
      withAlbum({
        description:
          '![挿絵](/assets/inline.png)\n\n[頒布ページ](https://example.com/) で頒布する。',
      }),
      new Map(),
    );
    const html = data.albums[0]?.descriptionHtml ?? '';

    expect(html).not.toContain('<img');
    expect(html).not.toContain('<a');
    expect(html).not.toContain('https://example.com/');
    expect(html).toContain('頒布ページ で頒布する。');
  });

  it('never renders raw HTML from the description', () => {
    const html =
      toPresentationData(
        withAlbum({ description: '<script>alert(1)</script>\n\n**本文**' }),
        new Map(),
      ).albums[0]?.descriptionHtml ?? '';

    expect(html).not.toContain('<script');
    expect(html).toContain('<strong>本文</strong>');
  });

  it('escapes plain text and keeps its paragraphs and line breaks', () => {
    const html =
      toPresentationData(
        withAlbum({
          description: '一行目 <b>太字ではない</b>\n二行目\n\n次の段落',
          descriptionFormat: 'PLAIN_TEXT',
        }),
        new Map(),
      ).albums[0]?.descriptionHtml ?? '';

    expect(html).toBe(
      '<p>一行目 &lt;b&gt;太字ではない&lt;/b&gt;<br>二行目</p><p>次の段落</p>',
    );
  });

  it('reads older schemas without the presentation facts', () => {
    const legacy = parse({
      ...example,
      schemaVersion: 2,
      albums: [
        {
          albumId: album?.['albumId'],
          title: album?.['title'],
          artworkAssetId: artworkId,
          tracks: album?.['tracks'],
        },
      ],
    });
    const data = toPresentationData(legacy, new Map());

    expect(data.albums[0]).toEqual({
      albumId: '0192f8a0-0000-7000-8000-000000000100',
      title: '見本の作品',
      tracks: [{ trackId: '0192f8a0-0000-7000-8000-000000000200', title: '1曲目' }],
    });
  });
});

describe('artworkAssetIds', () => {
  it('lists each artwork once, so that the caller reads it from the store once', () => {
    const shared = parse({
      ...example,
      albums: [
        album,
        {
          ...album,
          albumId: '0192f8a0-0000-7000-8000-000000000101',
          tracks: [],
        },
      ],
      playbackItems: example.playbackItems,
    });

    expect(artworkAssetIds(shared)).toEqual([artworkId]);
    expect(artworkAssetIds(withoutKeys('artworkAssetId'))).toEqual([]);
  });
});
