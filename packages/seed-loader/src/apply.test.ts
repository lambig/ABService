import { describe, expect, it, vi } from 'vitest';
import type { AdminApi } from 'abservice-admin-api';
import { applyPlan } from './apply.ts';
import type { Plan } from './plan.ts';
import type { SeedContent } from './seed.ts';

const seed: SeedContent = {
  directory: '/seed',
  siteContents: [{ key: 'site.name', content: 'Name', contentFormat: 'PLAIN_TEXT' }],
  albums: [
    {
      catalogNumber: 'NEW-1',
      published: true,
      input: {
        title: 'New',
        releaseDate: '2026-01-01',
        artistDisplayName: 'A',
        artistSortKey: 'a',
        catalogNumber: 'NEW-1',
      },
      coverImage: { path: '/seed/albums/NEW-1/cover.png', contentType: 'image/png' },
    },
    {
      catalogNumber: 'HALF-1',
      published: true,
      input: {
        title: 'Half',
        releaseDate: '2026-01-01',
        artistDisplayName: 'A',
        artistSortKey: 'a',
        catalogNumber: 'HALF-1',
      },
      coverImage: { path: '/seed/albums/HALF-1/cover.png', contentType: 'image/png' },
    },
  ],
  articles: [
    {
      title: 'Album article',
      published: true,
      albumCatalogNumber: 'NEW-1',
      input: { articleType: 'ALBUM', title: 'Album article', tags: ['tag'] },
    },
    { title: 'Draft note', published: false, input: { articleType: 'NOTE', title: 'Draft note' } },
  ],
};

const plan: Plan = {
  siteContents: [{ kind: 'site-content', key: 'site.name', create: true }],
  albums: [
    { kind: 'album', catalogNumber: 'NEW-1', create: true, addCoverImage: false, publish: true },
    { kind: 'album', catalogNumber: 'HALF-1', create: false, addCoverImage: true, publish: true },
  ],
  articles: [
    { kind: 'article', title: 'Album article', create: true, publish: true },
    { kind: 'article', title: 'Draft note', create: false, publish: false },
  ],
  problems: [],
};

const image = new Blob(['png'], { type: 'image/png' });
const readImage = vi.fn(() => Promise.resolve(image));

/** 呼ばれた操作を順に記録する管理APIの代わり */
const recordingApi = (overrides: Partial<Record<keyof AdminApi, unknown>> = {}) => {
  const calls: string[] = [];
  const record =
    <T>(name: string, result: T) =>
    (...args: readonly unknown[]) => {
      calls.push(`${name}(${args.map((arg) => (typeof arg === 'string' ? arg : '…')).join(', ')})`);
      return Promise.resolve(result);
    };
  const api = {
    upsertSiteContent: record('upsertSiteContent', undefined),
    seedDraftAlbum: record('seedDraftAlbum', 'album-new'),
    findAlbumByCatalogNumber: (catalogNumber: string) =>
      record('findAlbumByCatalogNumber', { albumId: `album-${catalogNumber}` })(catalogNumber),
    seedAsset: record('seedAsset', 'asset-key'),
    setAlbumCoverImage: record('setAlbumCoverImage', undefined),
    publishAlbum: record('publishAlbum', undefined),
    seedDraftArticle: record('seedDraftArticle', 'article-new'),
    findArticleByTitle: (title: string) =>
      record('findArticleByTitle', { articleId: `article-${title}` })(title),
    publishArticle: record('publishArticle', undefined),
    ...overrides,
  };
  return { api: api as unknown as AdminApi, calls };
};

describe('計画の実行', () => {
  it('文言 → 作品 → 記事の順に、計画にある操作だけを1件ずつ送る', async () => {
    const { api, calls } = recordingApi();
    const lines: string[] = [];

    const summary = await applyPlan(plan, seed, api, (line) => lines.push(line), readImage);

    expect(calls).toStrictEqual([
      'upsertSiteContent(…)',
      'seedDraftAlbum(…)',
      'publishAlbum(album-new)',
      'findAlbumByCatalogNumber(HALF-1)',
      'seedAsset(…)',
      'setAlbumCoverImage(album-HALF-1, asset-key)',
      'publishAlbum(album-HALF-1)',
      'findAlbumByCatalogNumber(NEW-1)',
      'seedDraftArticle(…)',
      'publishArticle(article-new)',
      'findArticleByTitle(Draft note)',
    ]);
    expect(lines).toStrictEqual([
      '文言 site.name: 登録した',
      '作品 NEW-1: 作成した・公開した',
      '作品 HALF-1: 画像を付けた・公開した',
      '記事「Album article」: 作成した・公開した',
      '記事「Draft note」: 飛ばした',
    ]);
    expect(summary).toStrictEqual({ created: 3, published: 3, coverImagesAdded: 1, skipped: 1 });
  });

  it('作品の作成には画像の実体と、記事の作成には解いた作品IDを添える', async () => {
    const seedDraftAlbum = vi.fn(() => Promise.resolve('album-new'));
    const seedDraftArticle = vi.fn(() => Promise.resolve('article-new'));
    const { api } = recordingApi({ seedDraftAlbum, seedDraftArticle });

    await applyPlan(plan, seed, api, () => undefined, readImage);

    expect(seedDraftAlbum).toHaveBeenCalledWith({
      ...seed.albums[0]?.input,
      coverImage: { contentType: 'image/png', body: image },
    });
    expect(seedDraftArticle).toHaveBeenCalledWith({
      articleType: 'ALBUM',
      title: 'Album article',
      tags: ['tag'],
      albumId: 'album-NEW-1',
    });
  });

  it('途中で落ちたら、その段と再実行で続けられることを伝えて止まる', async () => {
    const { api, calls } = recordingApi({
      publishAlbum: () => Promise.reject(new Error('POST .../publish が失敗しました（HTTP 400）')),
    });

    await expect(applyPlan(plan, seed, api, () => undefined, readImage)).rejects.toThrow(
      /作品 NEW-1 の公開 で止まりました。\n原因を直してから同じコマンドを再実行すると、入った分は飛ばして続きから進みます。\nPOST/u,
    );
    expect(calls).toStrictEqual(['upsertSiteContent(…)', 'seedDraftAlbum(…)']);
  });

  it('問題を持つ計画は、何も送らずに落とす', async () => {
    const { api, calls } = recordingApi();

    await expect(
      applyPlan({ ...plan, problems: ['参照先が無い'] }, seed, api, () => undefined, readImage),
    ).rejects.toThrow(/計画に問題があるため実行しません:\n参照先が無い/u);
    expect(calls).toStrictEqual([]);
  });
});
