import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { basename, extname, join } from 'node:path';
import type { AlbumSeed, MarkupFormat, SiteContentSeed, TrackSeed } from 'abservice-admin-api';
import {
  array,
  boolean,
  fail,
  integer,
  object,
  oneOf,
  optional,
  parseJson,
  string,
} from './decode.ts';
import type { ImageContentType, SeedAlbum, SeedArticle, SeedContent, SeedImage } from './seed.ts';

/**
 * 投入ファイルの置き方。
 *
 * ```
 * <dir>/
 *   site/<key>.md | <key>.txt           サイトの文言。拡張子が形式（Markdown / プレーンテキスト）
 *   albums/<catalogNumber>/album.json   作品。ディレクトリ名がカタログナンバー
 *   albums/<catalogNumber>/description.md | description.txt   概要説明（任意・どちらか1つ）
 *   albums/<catalogNumber>/<coverImage>  album.json の coverImage が指す画像（任意）
 *   articles/<name>/article.json        記事。ディレクトリ名の順に公開する
 *   articles/<name>/body.md | body.txt   本文（任意・どちらか1つ）
 * ```
 *
 * <p>
 * 同定に使う値（カタログナンバー・タイトル・キー）をファイルの中に二重に書かせない。カタログナンバーは
 * ディレクトリ名から、キーはファイル名から取る。記事はタイトルで同定するため、ディレクトリ名は並び順だけを
 * 決める。
 * </p>
 */

const SITE_DIR = 'site';
const ALBUMS_DIR = 'albums';
const ARTICLES_DIR = 'articles';
const ALBUM_FILE = 'album.json';
const ARTICLE_FILE = 'article.json';

/** 拡張子と形式の対応。ここに無い拡張子は誤り */
const MARKUP_BY_EXTENSION: Readonly<Record<string, MarkupFormat>> = {
  '.md': 'MARKDOWN',
  '.txt': 'PLAIN_TEXT',
};

/** 拡張子と画像形式の対応。バックエンドが受け入れる形式（`AssetImageFormat`）に揃える */
const IMAGE_BY_EXTENSION: Readonly<Record<string, ImageContentType>> = {
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
};

/** 末尾の改行を1つだけ落とす。編集器が付ける改行を内容にしない */
const readText = (path: string): string => readFileSync(path, 'utf8').replace(/\r?\n$/u, '');

/**
 * 本文のファイルを読む。空（空白と改行だけ）は誤りにする。
 *
 * JSON の項目と同じ契約——空の文言や本文を送るつもりは無く、省くならファイルを置かない。空のまま
 * 通すと、送った先で項目なしとして扱われ、書いた意図と食い違う。
 */
const readContent = (path: string): string => {
  const text = readText(path);
  return text.trim() === ''
    ? fail(path, '空のファイルです。項目を省くならファイルを置きません')
    : text;
};

const sortedEntries = (directory: string): readonly string[] =>
  existsSync(directory) ? [...readdirSync(directory)].sort() : [];

const isDirectory = (path: string): boolean => statSync(path).isDirectory();

const markupOf = (path: string): MarkupFormat =>
  MARKUP_BY_EXTENSION[extname(path)] ??
  fail(path, `拡張子は .md（Markdown）か .txt（プレーンテキスト）にしてください`);

/**
 * 同じ名前で拡張子だけ違う本文ファイルを1つ選ぶ。両方あると、どちらが本当か決められないため落とす。
 */
const readMarkupFile = (
  directory: string,
  stem: string,
): { readonly content: string; readonly format: MarkupFormat } | undefined => {
  const candidates = Object.keys(MARKUP_BY_EXTENSION)
    .map((extension) => join(directory, `${stem}${extension}`))
    .filter((path) => existsSync(path));
  const path =
    candidates.length <= 1
      ? candidates[0]
      : fail(directory, `${stem} は .md と .txt のどちらか1つだけを置いてください`);
  return path === undefined ? undefined : { content: readContent(path), format: markupOf(path) };
};

const readSiteContents = (root: string): readonly SiteContentSeed[] =>
  sortedEntries(join(root, SITE_DIR)).map((name) => {
    const path = join(root, SITE_DIR, name);
    return {
      key: basename(name, extname(name)),
      content: readContent(path),
      contentFormat: markupOf(path),
    };
  });

const trackDecoder = object<TrackSeed>({
  title: optional(string),
  artistDisplayName: optional(string),
  tunes: optional(
    array(
      object({
        tuneTitle: optional(string),
        composerCreditOverride: optional(string),
        arrangerCreditOverride: optional(string),
      }),
    ),
  ),
});

/** `album.json` の形。カタログナンバーと概要説明はファイルの外（ディレクトリ名・別ファイル）が持つ */
interface AlbumFile {
  readonly title: string;
  readonly releaseDate: string;
  readonly artistDisplayName: string;
  readonly artistSortKey: string;
  readonly isdn?: string;
  readonly event?: {
    readonly name: string;
    readonly date?: string;
    readonly place?: string;
    readonly spaceNumber?: string;
    readonly note?: string;
  };
  readonly basePrice?: { readonly amount: number; readonly currency?: string };
  readonly originalWorkNote?: string;
  readonly tracks?: readonly TrackSeed[];
  readonly externalAudioUrls?: readonly string[];
  /** 同じディレクトリに置いた画像のファイル名 */
  readonly coverImage?: string;
  readonly published: boolean;
}

const albumFileDecoder = object<AlbumFile>({
  title: string,
  releaseDate: string,
  artistDisplayName: string,
  artistSortKey: string,
  isdn: optional(string),
  event: optional(
    object({
      name: string,
      date: optional(string),
      place: optional(string),
      spaceNumber: optional(string),
      note: optional(string),
    }),
  ),
  basePrice: optional(object({ amount: integer, currency: optional(string) })),
  originalWorkNote: optional(string),
  tracks: optional(array(trackDecoder)),
  externalAudioUrls: optional(array(string)),
  coverImage: optional(string),
  published: boolean,
});

const imageOf = (directory: string, fileName: string): SeedImage => {
  const path = join(directory, fileName);
  const contentType =
    IMAGE_BY_EXTENSION[extname(fileName).toLowerCase()] ??
    fail(path, '画像は .png / .jpg / .jpeg / .webp のどれかにしてください');
  return existsSync(path) ? { path, contentType } : fail(path, 'coverImage が指す画像がありません');
};

const readAlbum = (directory: string): SeedAlbum => {
  const catalogNumber = basename(directory);
  const filePath = join(directory, ALBUM_FILE);
  const file = existsSync(filePath)
    ? albumFileDecoder(parseJson(readText(filePath), filePath), filePath)
    : fail(directory, `${ALBUM_FILE} がありません`);
  const description = readMarkupFile(directory, 'description');
  const { coverImage, published, externalAudioUrls, ...rest } = file;
  const input: AlbumSeed = {
    ...rest,
    catalogNumber,
    ...(description === undefined
      ? {}
      : { description: description.content, descriptionFormat: description.format }),
    ...(externalAudioUrls === undefined ? {} : { externalAudioUrls }),
  };
  return {
    catalogNumber,
    published,
    input,
    ...(coverImage === undefined ? {} : { coverImage: imageOf(directory, coverImage) }),
  };
};

const readAlbums = (root: string): readonly SeedAlbum[] =>
  sortedEntries(join(root, ALBUMS_DIR))
    .map((name) => join(root, ALBUMS_DIR, name))
    .filter(isDirectory)
    .map(readAlbum);

/** `article.json` の形。本文は別ファイルが持つ */
interface ArticleFile {
  readonly articleType: 'ALBUM' | 'NOTE' | 'NEWS' | 'EVENT' | 'OTHER';
  readonly title: string;
  readonly introShort?: string;
  /** 参照先の作品のカタログナンバー */
  readonly album?: string;
  readonly tags?: readonly string[];
  readonly published: boolean;
}

const articleFileDecoder = object<ArticleFile>({
  articleType: oneOf(['ALBUM', 'NOTE', 'NEWS', 'EVENT', 'OTHER']),
  title: string,
  introShort: optional(string),
  album: optional(string),
  tags: optional(array(string)),
  published: boolean,
});

const readArticle = (directory: string): SeedArticle => {
  const filePath = join(directory, ARTICLE_FILE);
  const file = existsSync(filePath)
    ? articleFileDecoder(parseJson(readText(filePath), filePath), filePath)
    : fail(directory, `${ARTICLE_FILE} がありません`);
  const body = readMarkupFile(directory, 'body');
  const { album, published, ...rest } = file;
  const referenceIsConsistent = (file.articleType === 'ALBUM') === (album !== undefined);
  return referenceIsConsistent
    ? {
        title: file.title,
        published,
        ...(album === undefined ? {} : { albumCatalogNumber: album }),
        input: {
          ...rest,
          ...(body === undefined ? {} : { body: body.content, bodyFormat: body.format }),
        },
      }
    : fail(
        filePath,
        file.articleType === 'ALBUM'
          ? '作品紹介（ALBUM）の記事には album（参照先のカタログナンバー）が要ります'
          : `${file.articleType} の記事は album を持てません（作品を参照できるのは ALBUM だけ）`,
      );
};

const readArticles = (root: string): readonly SeedArticle[] =>
  sortedEntries(join(root, ARTICLES_DIR))
    .map((name) => join(root, ARTICLES_DIR, name))
    .filter(isDirectory)
    .map(readArticle);

const duplicatesOf = (values: readonly string[]): readonly string[] => [
  ...new Set(values.filter((value, index) => values.indexOf(value) !== index)),
];

/** 同定に使う値が重なっていないことを確かめる。重なると、どちらを入れたのか後から分からない */
const checkedForDuplicates = (content: SeedContent): SeedContent => {
  const duplicateTitles = duplicatesOf(content.articles.map((article) => article.title));
  const duplicateKeys = duplicatesOf(content.siteContents.map((item) => item.key));
  return duplicateTitles.length > 0
    ? fail(
        join(content.directory, ARTICLES_DIR),
        `同じタイトルの記事があります: ${duplicateTitles.join(', ')}`,
      )
    : duplicateKeys.length > 0
      ? fail(
          join(content.directory, SITE_DIR),
          `同じキーの文言があります: ${duplicateKeys.join(', ')}`,
        )
      : content;
};

/**
 * 投入ディレクトリを読み、形を確かめる。
 *
 * <p>
 * ここで落ちるのは形の誤りだけ。値の妥当性（ISDN のチェックデジット、トラック名の規則、Markdown の形式）は
 * バックエンドの値オブジェクトが持ち、送った時点で検証される。
 * </p>
 */
export const readSeedDirectory = (directory: string): SeedContent =>
  existsSync(directory) && isDirectory(directory)
    ? checkedForDuplicates({
        directory,
        siteContents: readSiteContents(directory),
        albums: readAlbums(directory),
        articles: readArticles(directory),
      })
    : fail(directory, '投入ディレクトリがありません');
