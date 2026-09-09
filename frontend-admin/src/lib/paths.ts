/**
 * 画面間の経路。
 *
 * <p>
 * 管理画面は単一ドメインの `/admin*` 配下で配信される（`astro.config.mjs` の `base`）。綴りを直に
 * 書くと、プレフィックスを1つ落としたリンクが既定の振り分けで公開サイトのバケットへ流れて 404 に
 * なる。宣言した `base` を出所にして組み立てる。
 * </p>
 */
const BASE = import.meta.env.BASE_URL.replace(/\/$/u, '');

/** 作品の一覧（管理画面の入口） */
export const ALBUM_LIST_PATH = `${BASE}/`;

/** 作品の新規作成 */
export const NEW_ALBUM_PATH = `${BASE}/albums/new`;

/**
 * 作品の編集。
 *
 * <p>
 * 対象は経路の一部ではなく問い合わせ文字列で渡す。成果物は静的で、組み立ての時点に存在する作品しか
 * 経路として出せない（管理画面が扱うのは下書きを含む編集中の状態で、組み立て後に増える）。
 * </p>
 */
export const editAlbumPath = (albumId: string): string =>
  `${BASE}/albums/edit?albumId=${encodeURIComponent(albumId)}`;

/** 編集画面が受け取った対象。指定が無ければ null */
export const albumIdIn = (search: string): string | null =>
  new URLSearchParams(search).get('albumId');

/** 記事の一覧 */
export const ARTICLE_LIST_PATH = `${BASE}/articles`;

/** 記事の新規作成 */
export const NEW_ARTICLE_PATH = `${BASE}/articles/new`;

/** 記事の編集。対象の渡し方は作品と同じ（{@link editAlbumPath}） */
export const editArticlePath = (articleId: string): string =>
  `${BASE}/articles/edit?articleId=${encodeURIComponent(articleId)}`;

/** 編集画面が受け取った対象。指定が無ければ null */
export const articleIdIn = (search: string): string | null =>
  new URLSearchParams(search).get('articleId');
