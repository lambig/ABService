import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { SeedFileError } from './decode.ts';
import { readSeedDirectory } from './seed-files.ts';

/** 一時ディレクトリへ投入ファイルの木を書く */
const seedTree = (files: Readonly<Record<string, string | Buffer>>): string => {
  const root = mkdtempSync(join(tmpdir(), 'seed-files-'));
  Object.entries(files).forEach(([path, content]) => {
    mkdirSync(dirname(join(root, path)), { recursive: true });
    writeFileSync(join(root, path), content);
  });
  return root;
};

const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

const album = (extra: Readonly<Record<string, unknown>> = {}): string =>
  JSON.stringify({
    title: '作品',
    releaseDate: '2026-01-01',
    artistDisplayName: '名義',
    artistSortKey: 'めいぎ',
    published: true,
    ...extra,
  });

const article = (extra: Readonly<Record<string, unknown>> = {}): string =>
  JSON.stringify({ articleType: 'NOTE', title: 'ノート', published: true, ...extra });

describe('投入ディレクトリの読み方', () => {
  it('文言・作品・記事を、ファイル名と拡張子から同定と形式を決めて読む', () => {
    const root = seedTree({
      'site/site.name.txt': 'サイト名\n',
      'site/home.introduction.md': '## ようこそ\n\n紹介文\n',
      'albums/CAT-0001/album.json': album({
        isdn: '2784000001004',
        event: { name: 'イベント', date: '2026-01-02' },
        basePrice: { amount: 1500 },
        originalWorkNote: '「原作」より',
        tracks: [
          { title: '曲', tunes: [{ tuneTitle: 'チューン', composerCreditOverride: 'Trad.' }] },
          { tunes: [{}] },
        ],
        externalAudioUrls: ['https://soundcloud.com/x/y'],
        coverImage: 'cover.png',
      }),
      'albums/CAT-0001/description.md': '## 概要\n',
      'albums/CAT-0001/cover.png': PNG_SIGNATURE,
      'albums/CAT-0002/album.json': album({ published: false }),
      'articles/01-note/article.json': article({ introShort: '紹介', tags: ['タグ'] }),
      'articles/01-note/body.txt': '本文',
      'articles/02-album/article.json': article({
        articleType: 'ALBUM',
        title: '作品紹介',
        album: 'CAT-0001',
      }),
      'articles/02-album/body.md': '## 見出し',
    });

    const seed = readSeedDirectory(root);

    expect(seed.siteContents).toStrictEqual([
      { key: 'home.introduction', content: '## ようこそ\n\n紹介文', contentFormat: 'MARKDOWN' },
      { key: 'site.name', content: 'サイト名', contentFormat: 'PLAIN_TEXT' },
    ]);
    expect(seed.albums).toStrictEqual([
      {
        catalogNumber: 'CAT-0001',
        published: true,
        input: {
          title: '作品',
          releaseDate: '2026-01-01',
          artistDisplayName: '名義',
          artistSortKey: 'めいぎ',
          catalogNumber: 'CAT-0001',
          isdn: '2784000001004',
          event: { name: 'イベント', date: '2026-01-02' },
          basePrice: { amount: 1500 },
          originalWorkNote: '「原作」より',
          tracks: [
            { title: '曲', tunes: [{ tuneTitle: 'チューン', composerCreditOverride: 'Trad.' }] },
            { tunes: [{}] },
          ],
          externalAudioUrls: ['https://soundcloud.com/x/y'],
          description: '## 概要',
          descriptionFormat: 'MARKDOWN',
        },
        coverImage: { path: join(root, 'albums/CAT-0001/cover.png'), contentType: 'image/png' },
      },
      {
        catalogNumber: 'CAT-0002',
        published: false,
        input: {
          title: '作品',
          releaseDate: '2026-01-01',
          artistDisplayName: '名義',
          artistSortKey: 'めいぎ',
          catalogNumber: 'CAT-0002',
        },
      },
    ]);
    expect(seed.articles).toStrictEqual([
      {
        title: 'ノート',
        published: true,
        input: {
          articleType: 'NOTE',
          title: 'ノート',
          introShort: '紹介',
          tags: ['タグ'],
          body: '本文',
          bodyFormat: 'PLAIN_TEXT',
        },
      },
      {
        title: '作品紹介',
        published: true,
        albumCatalogNumber: 'CAT-0001',
        input: {
          articleType: 'ALBUM',
          title: '作品紹介',
          body: '## 見出し',
          bodyFormat: 'MARKDOWN',
        },
      },
    ]);
  });

  it('空のディレクトリは、何も持たない投入内容として読む', () => {
    const root = seedTree({});
    expect(readSeedDirectory(root)).toStrictEqual({
      directory: root,
      siteContents: [],
      albums: [],
      articles: [],
    });
  });

  it.each([
    {
      name: '知らない項目（綴り違い）',
      files: { 'albums/CAT-1/album.json': album({ publised: true }) },
      message: /知らない項目があります: publised/u,
    },
    {
      name: '必須項目の欠け',
      files: { 'albums/CAT-1/album.json': JSON.stringify({ title: 'x', published: true }) },
      message: /album\.json\.releaseDate: 文字列が要ります/u,
    },
    {
      name: '公開の指定が無い',
      files: {
        'albums/CAT-1/album.json': JSON.stringify({
          title: 'x',
          releaseDate: '2026-01-01',
          artistDisplayName: 'a',
          artistSortKey: 'a',
        }),
      },
      message: /published: true か false が要ります/u,
    },
    {
      name: '空文字の項目',
      files: { 'albums/CAT-1/album.json': album({ isdn: '  ' }) },
      message: /isdn: 空の文字列です/u,
    },
    {
      name: '壊れた JSON',
      files: { 'albums/CAT-1/album.json': '{ "title": ' },
      message: /JSON として読めません/u,
    },
    {
      name: 'album.json の無い作品ディレクトリ',
      files: { 'albums/CAT-1/description.md': 'x' },
      message: /album\.json がありません/u,
    },
    {
      name: '概要説明の形式が2つ',
      files: {
        'albums/CAT-1/album.json': album(),
        'albums/CAT-1/description.md': 'a',
        'albums/CAT-1/description.txt': 'b',
      },
      message: /description は \.md と \.txt のどちらか1つだけ/u,
    },
    {
      name: '無い画像を指す',
      files: { 'albums/CAT-1/album.json': album({ coverImage: 'cover.png' }) },
      message: /coverImage が指す画像がありません/u,
    },
    {
      name: '受け入れない画像形式',
      files: {
        'albums/CAT-1/album.json': album({ coverImage: 'cover.gif' }),
        'albums/CAT-1/cover.gif': 'GIF89a',
      },
      message: /画像は \.png \/ \.jpg \/ \.jpeg \/ \.webp のどれか/u,
    },
    {
      name: '作品紹介の記事が参照を持たない',
      files: { 'articles/a/article.json': article({ articleType: 'ALBUM' }) },
      message: /作品紹介（ALBUM）の記事には album/u,
    },
    {
      name: '作品紹介でない記事が参照を持つ',
      files: { 'articles/a/article.json': article({ album: 'CAT-1' }) },
      message: /NOTE の記事は album を持てません/u,
    },
    {
      name: '知らない記事種別',
      files: { 'articles/a/article.json': article({ articleType: 'BLOG' }) },
      message: /ALBUM \/ NOTE \/ NEWS \/ EVENT \/ OTHER のどれかが要ります/u,
    },
    {
      name: '同じタイトルの記事',
      files: {
        'articles/a/article.json': article(),
        'articles/b/article.json': article(),
      },
      message: /同じタイトルの記事があります: ノート/u,
    },
    {
      name: '文言の拡張子が形式を表さない',
      files: { 'site/site.name.html': 'x' },
      message: /拡張子は \.md（Markdown）か \.txt/u,
    },
  ])('$name は、所在を示して落とす', ({ files, message }) => {
    const root = seedTree(files);
    expect(() => readSeedDirectory(root)).toThrow(SeedFileError);
    expect(() => readSeedDirectory(root)).toThrow(message);
  });

  it('投入ディレクトリそのものが無ければ落とす', () => {
    expect(() => readSeedDirectory(join(tmpdir(), 'seed-files-missing'))).toThrow(
      /投入ディレクトリがありません/u,
    );
  });
});
