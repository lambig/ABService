import { stack } from './config.ts';

/**
 * 管理API経由でデータを投入する。
 *
 * SQL のフィクスチャを作らないのは、投入経路そのものも同時に検証するため（#164）。テストが必要とする
 * 形だけを持ち、網羅はしない。
 */

/** 作る作品の指定。省略した項目は API の既定に従う */
export interface AlbumSeed {
  readonly title: string;
  readonly releaseDate: string;
  readonly artistDisplayName: string;
  readonly artistSortKey: string;
  readonly catalogNumber?: string;
  readonly isdn?: string;
  readonly description?: string;
  readonly descriptionFormat?: 'MARKDOWN' | 'PLAIN_TEXT';
  readonly event?: {
    readonly name: string;
    readonly date?: string;
    readonly place?: string;
    readonly spaceNumber?: string;
    readonly note?: string;
  };
  readonly tracks?: readonly TrackSeed[];
  readonly externalAudioUrls?: readonly string[];
}

/** 作るトラックの指定 */
export interface TrackSeed {
  readonly trackNo: number;
  readonly title: string;
  readonly artistDisplayName?: string;
  readonly tunes?: readonly TuneSeed[];
}

/** トラック内のチューン構成 */
export interface TuneSeed {
  readonly seq: number;
  readonly tuneTitle: string;
  readonly composerCreditOverride?: string;
  readonly arrangerCreditOverride?: string;
}

const adminHeaders = {
  Authorization: `Bearer ${stack.adminApiKey}`,
  'Content-Type': 'application/json',
} as const;

const sendAdmin = async (method: 'POST' | 'PUT', path: string, body: unknown): Promise<unknown> => {
  const response = await fetch(`${stack.backendBaseUrl}${path}`, {
    method,
    headers: adminHeaders,
    body: JSON.stringify(body),
  });
  const text = await response.text();
  return response.ok
    ? (JSON.parse(text) as unknown)
    : Promise.reject(
        new Error(`${method} ${path} が失敗しました（HTTP ${String(response.status)}）: ${text}`),
      );
};

const postAdmin = (path: string, body: unknown): Promise<unknown> => sendAdmin('POST', path, body);

const putAdmin = (path: string, body: unknown): Promise<unknown> => sendAdmin('PUT', path, body);

const getAdmin = async (path: string): Promise<unknown> => {
  const response = await fetch(`${stack.backendBaseUrl}${path}`, { headers: adminHeaders });
  const text = await response.text();
  return response.ok
    ? (JSON.parse(text) as unknown)
    : Promise.reject(
        new Error(`GET ${path} が失敗しました（HTTP ${String(response.status)}）: ${text}`),
      );
};

const albumIdOf = (created: unknown): string => {
  const albumId = (created as { albumId?: unknown }).albumId;
  return typeof albumId === 'string'
    ? albumId
    : (() => {
        throw new Error(`作品の作成応答に albumId がありません: ${JSON.stringify(created)}`);
      })();
};

/**
 * 作品を作り、トラックと外部音源を付ける（下書きのまま）。
 *
 * @returns 作った作品のドメインID
 */
export const seedDraftAlbum = async (album: AlbumSeed): Promise<string> => {
  const created = await postAdmin('/api/v1/albums/with-tracks', {
    title: album.title,
    releaseDate: album.releaseDate,
    artistDisplayName: album.artistDisplayName,
    artistSortKey: album.artistSortKey,
    catalogNumber: album.catalogNumber,
    isdn: album.isdn,
    description: album.description,
    descriptionFormat: album.descriptionFormat,
    event: album.event,
    tracks: (album.tracks ?? []).map((track) => ({
      trackNo: track.trackNo,
      title: track.title,
      artistDisplayName: track.artistDisplayName,
      tunes: (track.tunes ?? []).map((tune) => ({
        seq: tune.seq,
        tuneTitle: tune.tuneTitle,
        composerCreditOverride: tune.composerCreditOverride,
        arrangerCreditOverride: tune.arrangerCreditOverride,
      })),
    })),
  });

  const albumId = albumIdOf(created);

  /*
   * SEQUENTIAL-ORDER: 外部音源の表示順は末尾採番のため、並列に投げると順序が実行ごとに変わる。
   * 指定した並びをそのまま再現するために1件ずつ送る。
   */
  for (const url of album.externalAudioUrls ?? []) {
    await postAdmin(`/api/v1/albums/${albumId}/external-audios`, { url });
  }

  return albumId;
};

/**
 * 下書きの作品を公開する。
 *
 * @param albumId
 *            公開する作品のドメインID
 */
export const publishAlbum = async (albumId: string): Promise<void> => {
  await postAdmin(`/api/v1/albums/${albumId}/publish`, {});
};

/**
 * 公開中の作品を下書きへ戻す。
 *
 * @param albumId
 *            下書きへ戻す作品のドメインID
 */
export const unpublishAlbum = async (albumId: string): Promise<void> => {
  await postAdmin(`/api/v1/albums/${albumId}/unpublish`, {});
};

/**
 * 作品を作り、トラックと外部音源を付けて公開する。
 *
 * @returns 作った作品のドメインID
 */
export const seedPublishedAlbum = async (album: AlbumSeed): Promise<string> => {
  const albumId = await seedDraftAlbum(album);
  await publishAlbum(albumId);
  return albumId;
};

/** 管理向け一覧の1件。同定と公開状態の確認に使う項目だけを持つ */
export interface AdminAlbum {
  readonly albumId: string;
  readonly catalogNumber: string | null;
  /** 公開日時。下書きは null */
  readonly publishedAt: string | null;
}

/**
 * カタログナンバーで作品を引く（下書きを含む）。
 *
 * <p>
 * 公開の一覧には下書きが出ないため、管理APIを通す。絞り込みは部分一致のため、完全一致で選び直す。
 * </p>
 *
 * @param catalogNumber
 *            同定に使うカタログナンバー
 * @returns 見つかった作品。無ければ undefined
 */
export const findAlbumByCatalogNumber = async (
  catalogNumber: string,
): Promise<AdminAlbum | undefined> => {
  const body = await getAdmin(
    `/api/v1/admin/albums?size=100&catalogNumber=${encodeURIComponent(catalogNumber)}`,
  );
  const { items } = body as { items: readonly AdminAlbum[] };
  return items.find((item) => item.catalogNumber === catalogNumber);
};

/** 作る記事の指定。省略した項目は API の既定に従う */
export interface ArticleSeed {
  readonly articleType: 'ALBUM' | 'NOTE' | 'NEWS' | 'EVENT' | 'OTHER';
  readonly title: string;
  readonly body?: string;
  readonly bodyFormat?: 'MARKDOWN' | 'PLAIN_TEXT';
  readonly introShort?: string;
  /** 参照先の作品のドメインID。参照を持てるのは ALBUM 種別だけ */
  readonly albumId?: string;
  /** 付けるタグ名。同じ名前のタグが無ければ作られる */
  readonly tags?: readonly string[];
}

const articleIdOf = (created: unknown): string => {
  const articleId = (created as { articleId?: unknown }).articleId;
  return typeof articleId === 'string'
    ? articleId
    : (() => {
        throw new Error(`記事の作成応答に articleId がありません: ${JSON.stringify(created)}`);
      })();
};

/**
 * 記事を作り、作品への参照とタグを付ける（下書きのまま）。
 *
 * @returns 作った記事のドメインID
 */
export const seedDraftArticle = async (article: ArticleSeed): Promise<string> => {
  const created = await postAdmin('/api/v1/articles', {
    articleType: article.articleType,
    title: article.title,
    body: article.body,
    bodyFormat: article.bodyFormat,
    introShort: article.introShort,
  });

  const articleId = articleIdOf(created);

  /* 参照の設定は全項目置換の PUT（作成時のリクエストは参照を持たない） */
  await Promise.all(
    article.albumId === undefined
      ? []
      : [putAdmin(`/api/v1/articles/${articleId}/album`, { albumId: article.albumId })],
  );

  /*
   * SEQUENTIAL-ORDER: タグは名前で追加し、無ければ作られる。並列に投げると同じ名前を同時に作る
   * 経路へ入るため、1件ずつ送る。
   */
  for (const name of article.tags ?? []) {
    await postAdmin(`/api/v1/articles/${articleId}/tags`, { name });
  }

  return articleId;
};

/**
 * 下書きの記事を公開する。
 *
 * @param articleId
 *            公開する記事のドメインID
 */
export const publishArticle = async (articleId: string): Promise<void> => {
  await postAdmin(`/api/v1/articles/${articleId}/publish`, {});
};

/**
 * 記事を削除する。
 *
 * <p>
 * 記事は子を持たないため削除できる（作品はトラックの外部キーで塞がっている。#251）。フィクスチャを
 * 毎回同じ内容へ揃えるために使う。
 * </p>
 *
 * @param articleId
 *            削除する記事のドメインID
 */
export const deleteArticle = async (articleId: string): Promise<void> => {
  const response = await fetch(`${stack.backendBaseUrl}/api/v1/articles/${articleId}`, {
    method: 'DELETE',
    headers: adminHeaders,
  });
  return response.ok
    ? undefined
    : Promise.reject(
        new Error(
          `DELETE /api/v1/articles/${articleId} が失敗しました（HTTP ${String(response.status)}）`,
        ),
      );
};

/** 管理向け一覧の1件。同定に使う項目だけを持つ */
export interface AdminArticle {
  readonly articleId: string;
  readonly title: string;
}

interface AdminArticlePage {
  readonly items: readonly AdminArticle[];
  readonly totalPages: number;
}

const fetchAdminArticlePage = async (page: number): Promise<AdminArticlePage> =>
  (await getAdmin(`/api/v1/admin/articles?page=${String(page)}&size=100`)) as AdminArticlePage;

/**
 * タイトルで記事を引く（下書きを含む）。
 *
 * <p>
 * 公開の一覧には下書きが出ないため管理APIを通す。管理の記事一覧はタイトルでの絞り込みを持たない
 * （作品の一覧とは非対称。検索が要るのは記事編集画面から作品を選ぶ経路だけのため）ので、全ページ
 * たぐって完全一致で選ぶ。
 * </p>
 *
 * @param title
 *            同定に使うタイトル
 * @returns 見つかった記事。無ければ undefined
 */
export const findArticleByTitle = async (title: string): Promise<AdminArticle | undefined> => {
  const firstPage = await fetchAdminArticlePage(0);
  const remainingPages = await Promise.all(
    Array.from({ length: Math.max(firstPage.totalPages - 1, 0) }, (_unused, index) =>
      fetchAdminArticlePage(index + 1),
    ),
  );
  return [firstPage, ...remainingPages]
    .flatMap((page) => page.items)
    .find((item) => item.title === title);
};

/** 置くサイト文言の指定。キーごとに1つ */
export interface SiteContentSeed {
  readonly key: string;
  readonly content: string;
  readonly contentFormat: 'MARKDOWN' | 'PLAIN_TEXT';
}

/**
 * サイト文言を登録する（同じキーがあれば置き換える）。
 *
 * <p>
 * 文言はリポジトリに置かず管理画面から入れる（#230）。E2E も同じ経路を通す。
 * </p>
 */
export const upsertSiteContent = async (content: SiteContentSeed): Promise<void> => {
  await putAdmin(`/api/v1/site-contents/${content.key}`, {
    content: content.content,
    contentFormat: content.contentFormat,
  });
};
