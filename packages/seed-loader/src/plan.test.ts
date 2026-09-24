import { describe, expect, it } from 'vitest';
import type { AdminApi } from 'abservice-admin-api';
import { describePlan, planSeed, takeSnapshot } from './plan.ts';
import type { Snapshot } from './plan.ts';
import type { SeedAlbum, SeedArticle, SeedContent } from './seed.ts';

const albumSeed = (catalogNumber: string, published: boolean, withCover: boolean): SeedAlbum => ({
  catalogNumber,
  published,
  input: {
    title: catalogNumber,
    releaseDate: '2026-01-01',
    artistDisplayName: 'A',
    artistSortKey: 'a',
    catalogNumber,
  },
  ...(withCover ? { coverImage: { path: '/seed/cover.png', contentType: 'image/png' } } : {}),
});

const articleSeed = (title: string, published: boolean, album?: string): SeedArticle => ({
  title,
  published,
  ...(album === undefined ? {} : { albumCatalogNumber: album }),
  input: { articleType: album === undefined ? 'NOTE' : 'ALBUM', title },
});

const seed: SeedContent = {
  directory: '/seed',
  siteContents: [
    { key: 'site.name', content: 'Name', contentFormat: 'PLAIN_TEXT' },
    { key: 'site.description', content: 'New', contentFormat: 'PLAIN_TEXT' },
    { key: 'home.introduction', content: 'Intro', contentFormat: 'MARKDOWN' },
  ],
  albums: [
    albumSeed('NEW-1', true, true),
    albumSeed('HALF-1', true, true),
    albumSeed('DONE-1', true, true),
    albumSeed('DRAFT-1', false, false),
  ],
  articles: [
    articleSeed('新しい記事', true, 'NEW-1'),
    articleSeed('途中の記事', true),
    articleSeed('済んだ記事', true),
    articleSeed('下書きの記事', false),
  ],
};

const snapshot: Snapshot = {
  albums: new Map([
    ['HALF-1', { albumId: 'half', published: false, hasCoverImage: false }],
    ['DONE-1', { albumId: 'done', published: true, hasCoverImage: true }],
    ['DRAFT-1', { albumId: 'draft', published: true, hasCoverImage: false }],
  ]),
  articles: new Map([
    ['途中の記事', { articleId: 'half', title: '途中の記事', publishedAt: null }],
    ['済んだ記事', { articleId: 'done', title: '済んだ記事', publishedAt: '2026-01-01T00:00:00Z' }],
  ]),
  siteContents: new Map([
    ['site.name', { key: 'site.name', content: 'Name', contentFormat: 'PLAIN_TEXT' }],
    ['site.description', { key: 'site.description', content: 'Old', contentFormat: 'PLAIN_TEXT' }],
  ]),
};

describe('計画', () => {
  const plan = planSeed(seed, snapshot);

  it('文言は無いキーだけ登録し、あるキーは内容が違っても揃えない', () => {
    expect(plan.siteContents).toStrictEqual([
      { kind: 'site-content', key: 'site.name', create: false, note: '登録済み（同じ内容）' },
      {
        kind: 'site-content',
        key: 'site.description',
        create: false,
        note: '登録済み（内容が異なる。管理画面の値を正として、ここでは揃えない）',
      },
      { kind: 'site-content', key: 'home.introduction', create: true },
    ]);
  });

  it('作品は無いものを作り、あるものは途中の状態（画像なし・下書き）だけを埋める', () => {
    expect(plan.albums).toStrictEqual([
      { kind: 'album', catalogNumber: 'NEW-1', create: true, addCoverImage: false, publish: true },
      {
        kind: 'album',
        catalogNumber: 'HALF-1',
        create: false,
        addCoverImage: true,
        publish: true,
        note: '登録済み（内容は揃えない）',
      },
      {
        kind: 'album',
        catalogNumber: 'DONE-1',
        create: false,
        addCoverImage: false,
        publish: false,
        note: '登録済み（内容は揃えない）',
      },
      {
        kind: 'album',
        catalogNumber: 'DRAFT-1',
        create: false,
        addCoverImage: false,
        publish: false,
        note: '登録済み（内容は揃えない）',
      },
    ]);
  });

  it('記事は無いものを作り、下書きのまま残ったものだけを公開する', () => {
    expect(plan.articles).toStrictEqual([
      { kind: 'article', title: '新しい記事', create: true, publish: true },
      {
        kind: 'article',
        title: '途中の記事',
        create: false,
        publish: true,
        note: '登録済み（内容は揃えない）',
      },
      {
        kind: 'article',
        title: '済んだ記事',
        create: false,
        publish: false,
        note: '登録済み（内容は揃えない）',
      },
      { kind: 'article', title: '下書きの記事', create: true, publish: false },
    ]);
    expect(plan.problems).toStrictEqual([]);
  });

  it('投入内容にも投入先にも無い作品を参照する記事は、問題として挙げる', () => {
    const broken = planSeed(
      {
        ...seed,
        articles: [
          articleSeed('宙に浮く記事', true, 'NOWHERE-1'),
          articleSeed('既存を指す記事', true, 'DONE-1'),
        ],
      },
      snapshot,
    );
    expect(broken.problems).toStrictEqual([
      '記事「宙に浮く記事」が参照する作品 NOWHERE-1 は、投入内容にも投入先にもありません',
    ]);
  });

  it('計画を行に写す', () => {
    expect(describePlan(plan)).toStrictEqual([
      '文言 3 件',
      '  site.name: なし（登録済み（同じ内容））',
      '  site.description: なし（登録済み（内容が異なる。管理画面の値を正として、ここでは揃えない））',
      '  home.introduction: 登録',
      '作品 4 件',
      '  NEW-1: 作成 → 公開',
      '  HALF-1: 画像 → 公開（登録済み（内容は揃えない））',
      '  DONE-1: なし（登録済み（内容は揃えない））',
      '  DRAFT-1: なし（登録済み（内容は揃えない））',
      '記事 4 件',
      '  新しい記事: 作成 → 公開',
      '  途中の記事: 公開（登録済み（内容は揃えない））',
      '  済んだ記事: なし（登録済み（内容は揃えない））',
      '  下書きの記事: 作成',
    ]);
    expect(describePlan({ ...plan, problems: ['x'] })).toContain('問題');
  });
});

describe('投入先の状態の読み取り', () => {
  it('多数の作品でも照会を重ねず、既存作品の詳細と欠損の判定を保持する', async () => {
    const active = new Set<object>();
    const concurrency: number[] = [];
    const calls: string[] = [];
    const read = async <T>(label: string, result: T): Promise<T> => {
      const ticket = {};
      active.add(ticket);
      concurrency.push(active.size);
      calls.push(label);
      await Promise.resolve();
      active.delete(ticket);
      return result;
    };
    const codes = Array.from({ length: 40 }, (_, i) => `TEST-${String(i)}`);
    const api = {
      findAlbumByCatalogNumber: (code: string) =>
        read(`find:${code}`, code === 'TEST-1' ? undefined : { albumId: code }),
      getAdminAlbumDetail: (id: string) =>
        read(`detail:${id}`, { albumId: id, publishedAt: null, coverImageKey: 'fixture.jpg' }),
      findArticlesByTitlePrefix: () => read('articles', []),
      listSiteContents: () => read('site', []),
    } as unknown as AdminApi;
    const taken = await takeSnapshot(api, {
      ...seed,
      albums: codes.map((code) => albumSeed(code, false, false)),
      articles: [articleSeed('参照の重複', false, 'TEST-0')],
    });
    expect(Math.max(...concurrency)).toBe(1);
    expect(taken.albums.size).toBe(39);
    expect(taken.albums.has('TEST-1')).toBe(false);
    expect(taken.albums.get('TEST-39')).toStrictEqual({
      albumId: 'TEST-39',
      published: false,
      hasCoverImage: true,
    });
    expect(calls).toStrictEqual([
      ...codes.flatMap((code) =>
        code === 'TEST-1' ? [`find:${code}`] : [`find:${code}`, `detail:${code}`],
      ),
      'articles',
      'site',
    ]);
  });

  it('照会が失敗したら後続の照会を開始せず停止する', async () => {
    const calls: string[] = [];
    const api = {
      findAlbumByCatalogNumber: (code: string) => {
        calls.push(code);
        return Promise.reject(new Error('temporary read failure'));
      },
    } as unknown as AdminApi;
    await expect(takeSnapshot(api, seed)).rejects.toThrow('temporary read failure');
    expect(calls).toStrictEqual(['NEW-1']);
  });

  it('投入内容が同定に使う値だけを照会し、記事の参照先も含める', async () => {
    const asked: string[] = [];
    const api = {
      findAlbumByCatalogNumber: (catalogNumber: string) => {
        asked.push(catalogNumber);
        return Promise.resolve(
          catalogNumber === 'DONE-1'
            ? { albumId: 'done', catalogNumber, publishedAt: '2026-01-01T00:00:00Z' }
            : undefined,
        );
      },
      getAdminAlbumDetail: (albumId: string) =>
        Promise.resolve({ albumId, publishedAt: '2026-01-01T00:00:00Z', coverImageKey: null }),
      findArticlesByTitlePrefix: () =>
        Promise.resolve([{ articleId: 'a', title: '済んだ記事', publishedAt: null }]),
      listSiteContents: () =>
        Promise.resolve([{ key: 'site.name', content: 'Name', contentFormat: 'PLAIN_TEXT' }]),
    } as unknown as AdminApi;

    const taken = await takeSnapshot(api, {
      ...seed,
      albums: [albumSeed('DONE-1', true, true)],
      articles: [articleSeed('記事', true, 'OTHER-1')],
    });

    expect(asked.sort()).toStrictEqual(['DONE-1', 'OTHER-1']);
    expect([...taken.albums.entries()]).toStrictEqual([
      ['DONE-1', { albumId: 'done', published: true, hasCoverImage: false }],
    ]);
    expect([...taken.articles.keys()]).toStrictEqual(['済んだ記事']);
    expect([...taken.siteContents.keys()]).toStrictEqual(['site.name']);
  });
});
