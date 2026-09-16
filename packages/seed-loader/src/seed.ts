import type { AlbumSeed, ArticleSeed, SiteContentSeed } from 'abservice-admin-api';

/**
 * 投入する内容のモデル。ファイルの置き方（`seed-files.ts`）と、管理APIへの送り方（`apply.ts`）の間に立つ。
 */

/** 受け入れる画像の形式。バックエンドは実体の先頭バイト列で判定するため、拡張子と中身は一致している必要がある */
export type ImageContentType = 'image/png' | 'image/jpeg' | 'image/webp';

/** カバー画像の実体の所在。読み込みは送る直前に行う */
export interface SeedImage {
  readonly path: string;
  readonly contentType: ImageContentType;
}

/** 投入する作品。カタログナンバーで同定する */
export interface SeedAlbum {
  readonly catalogNumber: string;
  /** 作った後に公開まで進めるか */
  readonly published: boolean;
  /** 管理APIへ送る形（画像を除く） */
  readonly input: AlbumSeed;
  readonly coverImage?: SeedImage;
}

/** 投入する記事。タイトルで同定する */
export interface SeedArticle {
  readonly title: string;
  /** 作った後に公開まで進めるか */
  readonly published: boolean;
  /** 参照先の作品のカタログナンバー。作品紹介（ALBUM）の記事だけが持つ */
  readonly albumCatalogNumber?: string;
  /** 管理APIへ送る形（作品参照を除く。参照は投入時にIDへ解く） */
  readonly input: Omit<ArticleSeed, 'albumId'>;
}

/** 投入する内容の全体 */
export interface SeedContent {
  /** 読んだディレクトリ。報告に使う */
  readonly directory: string;
  readonly siteContents: readonly SiteContentSeed[];
  readonly albums: readonly SeedAlbum[];
  /** 公開する順に並んでいる。公開の順序がそのまま一覧の並び（新しいものが先）になる */
  readonly articles: readonly SeedArticle[];
}
