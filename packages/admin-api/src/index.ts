import type { components } from '@api-schema';

/**
 * 管理API経由でデータを投入・照会するクライアント。
 *
 * <p>
 * E2E のシードと初期データのローダ（#373）が同じ経路を通るためにここへ置く。SQL のフィクスチャを作らないのは、
 * 投入経路そのものも同時に検証するため（#164）。利用側が必要とする形だけを持ち、網羅はしない。
 * </p>
 *
 * <p>
 * 認証は API キーを Bearer で送る。ブラウザは期限付きセッションへ交換するが、機械側は引き続き API キーを
 * 使える（DECISIONS 22）。
 * </p>
 */

/** 接続先と資格情報。`fetch` は検査のために差し替えられる */
export interface AdminApiConnection {
  /** バックエンドの起点（`/api` を含まない） */
  readonly baseUrl: string;
  /** 管理APIの鍵 */
  readonly apiKey: string;
  /** 通信の実装。省略すると実行環境の `fetch` */
  readonly fetch?: typeof fetch;
}

/** 作る作品の指定。省略した項目は API の既定に従う */
export interface AlbumSeed {
  readonly title: string;
  readonly releaseDate: string;
  readonly artistDisplayName: string;
  readonly artistSortKey: string;
  readonly catalogNumber?: string;
  readonly isdn?: string;
  readonly description?: string;
  readonly descriptionFormat?: MarkupFormat;
  readonly event?: {
    readonly name: string;
    readonly date?: string;
    readonly place?: string;
    readonly spaceNumber?: string;
    readonly note?: string;
  };
  /** 頒布の基準額。省略すると額が決まっていない作品になる */
  readonly basePrice?: {
    readonly amount: number;
    /** 通貨コード（ISO 4217）。省略は円 */
    readonly currency?: string;
  };
  /** 原作の出典の記述（#365）。省略すると記述を持たない作品になる */
  readonly originalWorkNote?: string;
  /** カバー画像。省略すると画像を持たない作品になる */
  readonly coverImage?: AssetSeed;
  readonly tracks?: readonly TrackSeed[];
  readonly externalAudioUrls?: readonly string[];
}

/** マークアップの形式 */
export type MarkupFormat = 'MARKDOWN' | 'PLAIN_TEXT';

/**
 * 送るアセットの実体。
 *
 * 形式はバックエンドが先頭バイト列で判定するため、申告（`contentType`）と中身が一致している必要がある。
 */
export interface AssetSeed {
  readonly contentType: string;
  readonly body: Blob;
}

/** 作るトラックの指定。並びは配列の位置がそのまま表すため、番号は持たない（#391） */
export interface TrackSeed {
  /** トラック名。省略すると、チューン名を繋いだものが名になる（#360） */
  readonly title?: string;
  readonly artistDisplayName?: string;
  readonly tunes?: readonly TuneSeed[];
}

/** トラック内のチューン構成。登場順も配列の位置が表す */
export interface TuneSeed {
  /** チューン名。省略すると名を持たない構成要素（MC・環境音など）になる */
  readonly tuneTitle?: string;
  readonly composerCreditOverride?: string;
  readonly arrangerCreditOverride?: string;
}

/** 記事の種別 */
export type ArticleType = 'ALBUM' | 'NOTE' | 'NEWS' | 'EVENT' | 'OTHER';

/** 作る記事の指定。省略した項目は API の既定に従う */
export interface ArticleSeed {
  readonly articleType: ArticleType;
  readonly title: string;
  readonly body?: string;
  readonly bodyFormat?: MarkupFormat;
  readonly introShort?: string;
  /** 参照先の作品のドメインID。参照を持てるのは ALBUM 種別だけ */
  readonly albumId?: string;
  /** 付けるタグ名。同じ名前のタグが無ければ作られる */
  readonly tags?: readonly string[];
}

/** 置くサイト文言の指定。キーごとに1つ */
export interface SiteContentSeed {
  readonly key: string;
  readonly content: string;
  readonly contentFormat: MarkupFormat;
}

/** 管理向け一覧の1件。同定と公開状態の確認に使う項目だけを持つ */
export interface AdminAlbum {
  readonly albumId: string;
  readonly catalogNumber: string | null;
  /** 公開日時。下書きは null */
  readonly publishedAt: string | null;
}

/** 管理向け一覧の1件。同定と公開状態の確認に使う項目だけを持つ */
export interface AdminArticle {
  readonly articleId: string;
  readonly title: string;
  /** 公開日時。下書きは null */
  readonly publishedAt: string | null;
}

/** 管理向けの作品詳細（生成された API 定義の型） */
export type AdminAlbumDetail = components['schemas']['AdminAlbumDetailResponse'];

/** 管理向けの記事詳細（生成された API 定義の型） */
export type AdminArticleDetail = components['schemas']['AdminArticleDetailResponse'];

/** 管理向けの作品一覧（生成された API 定義の型） */
export type AdminAlbumPage = components['schemas']['AdminAlbumListResponse'];

/** 作品の全項目置換の要求（生成された API 定義の型） */
export type UpdateAlbumRequest = components['schemas']['UpdateAlbumRequest'];

/** 記事の全項目置換の要求（生成された API 定義の型） */
export type UpdateArticleRequest = components['schemas']['UpdateArticleRequest'];

/** 登録済みのサイト文言（生成された API 定義の型） */
export type SiteContent = components['schemas']['SiteContentResponse'];

/** 払い出しの応答のうち、実体を送るために要る項目 */
interface AssetUploadUrl {
  readonly assetKey: string;
  readonly uploadUrl: string;
}

interface AdminArticlePage {
  readonly items: readonly AdminArticle[];
  readonly totalElements: number;
  readonly totalPages: number;
}

type WritingMethod = 'POST' | 'PUT';

const idOf =
  (field: 'albumId' | 'articleId', subject: string) =>
  (created: unknown): string => {
    const id = (created as Record<string, unknown>)[field];
    return typeof id === 'string'
      ? id
      : (() => {
          throw new Error(
            `${subject}の作成応答に ${field} がありません: ${JSON.stringify(created)}`,
          );
        })();
  };

const albumIdOf = idOf('albumId', '作品');
const articleIdOf = idOf('articleId', '記事');

const range = (length: number): readonly number[] =>
  Array.from({ length }, (_unused, index) => index);

/**
 * 値があるときだけ項目を持つ断片。詳細の null を要求では「項目なし」として送るために使う。
 *
 * `?? undefined` で写すと、`exactOptionalPropertyTypes` を有効にした利用側で型が合わない（任意項目に
 * 明示の undefined を入れられない）。キーごと落とせば JSON の形は同じで、どの利用側からも読める。
 */
const present = <K extends string, V>(
  key: K,
  value: V | null | undefined,
): { readonly [P in K]?: V } =>
  value === null ? {} : value === undefined ? {} : ({ [key]: value } as { readonly [P in K]?: V });

/** 詳細を、画像の鍵だけ差し替えて全項目置換の要求へ写す */
const updateRequestOf = (
  detail: AdminAlbumDetail,
  coverImageKey: string | null,
): UpdateAlbumRequest => ({
  expectedRevision: detail.revision,
  title: detail.title,
  releaseDate: detail.releaseDate,
  artistDisplayName: detail.artistDisplayName,
  ...present('artistSortKey', detail.artistSortKey),
  ...present('catalogNumber', detail.catalogNumber),
  ...present('isdn', detail.isdn),
  ...present('coverImageKey', coverImageKey),
  ...present('description', detail.description),
  descriptionFormat: detail.descriptionFormat,
  ...present(
    'event',
    detail.eventName === null
      ? null
      : {
          name: detail.eventName,
          ...present('date', detail.eventDate),
          ...present('place', detail.eventPlace),
          ...present('spaceNumber', detail.eventSpaceNumber),
          ...present('note', detail.eventNote),
        },
  ),
  ...present('basePrice', detail.basePrice),
  ...present('originalWorkNote', detail.originalWorkNote),
  tracks: detail.tracks.map((track) => ({
    trackId: track.trackId,
    ...present('title', track.title),
    ...present('artistDisplayName', track.artistDisplayName),
    ...present('artistSortKey', track.artistSortKey),
    tunes: track.tunes.map((tune) => ({
      ...present('tuneTitle', tune.tuneTitle),
      ...present('composerCreditOverride', tune.composerCreditOverride),
      ...present('arrangerCreditOverride', tune.arrangerCreditOverride),
      ...present('linkUrl', tune.linkUrl),
    })),
  })),
  externalAudios: detail.externalAudios.map((audio) => ({
    externalAudioId: audio.externalAudioId,
    url: audio.url,
  })),
});

/**
 * 作品の詳細を、全項目置換の要求へ写す。
 *
 * PUT は全項目置換のため、一部だけを変えたい呼び出し側はこれに上書きを重ねて送る。子のID・並び・イベントも
 * 詳細から保つ。詳細で null の項目は要求に含めない。
 */
export const toUpdateAlbumRequest = (detail: AdminAlbumDetail): UpdateAlbumRequest =>
  updateRequestOf(detail, detail.coverImageKey);

/**
 * 記事の詳細を、全項目置換の要求へ写す。
 *
 * 作品への参照とタグは別の経路で書くため、ここには含まれない。
 */
export const toUpdateArticleRequest = (detail: AdminArticleDetail): UpdateArticleRequest => ({
  expectedRevision: detail.revision,
  articleType: detail.articleType,
  title: detail.title,
  body: detail.body,
  bodyFormat: detail.bodyFormat,
  introShort: detail.introShort,
});

/**
 * 接続先に結び付いたクライアントを作る。
 *
 * <p>
 * 返るのは操作の集まりで、どれも失敗を例外で表す（HTTP の状態と応答本文を含む）。冪等にしたい呼び出し側は
 * 照会（`findAlbumByCatalogNumber` 等）で存在を見てから書く。
 * </p>
 */
export const adminApi = (connection: AdminApiConnection) => {
  const send = connection.fetch ?? fetch;
  const headers = {
    Authorization: `Bearer ${connection.apiKey}`,
    'Content-Type': 'application/json',
  } as const;

  const failed = (label: string, response: Response, text: string): Promise<never> =>
    Promise.reject(
      new Error(`${label} が失敗しました（HTTP ${String(response.status)}）: ${text}`),
    );

  const write = async (method: WritingMethod, path: string, body: unknown): Promise<unknown> => {
    const response = await send(`${connection.baseUrl}${path}`, {
      method,
      headers,
      body: JSON.stringify(body),
    });
    const text = await response.text();
    return response.ok
      ? (JSON.parse(text) as unknown)
      : failed(`${method} ${path}`, response, text);
  };

  const post = (path: string, body: unknown): Promise<unknown> => write('POST', path, body);
  const put = (path: string, body: unknown): Promise<unknown> => write('PUT', path, body);

  const get = async (path: string): Promise<unknown> => {
    const response = await send(`${connection.baseUrl}${path}`, { headers });
    const text = await response.text();
    return response.ok ? (JSON.parse(text) as unknown) : failed(`GET ${path}`, response, text);
  };

  const remove = async (path: string): Promise<void> => {
    const response = await send(`${connection.baseUrl}${path}`, { method: 'DELETE', headers });
    return response.ok ? undefined : failed(`DELETE ${path}`, response, await response.text());
  };

  /**
   * アセットを保管先へ送り、確定して配信できる鍵にする。
   *
   * 3段（払い出し・署名付きURLへの直接送信・確定）をそのまま通す。実体が管理APIを経由しないのは契約
   * （#136）であり、ここもその経路を迂回しない——迂回すると、画面が通る経路とは別の入れ方だけを
   * 検証したことになる。
   */
  const seedAsset = async (asset: AssetSeed): Promise<string> => {
    const issued = (await post('/api/v1/assets/upload-url', {
      contentType: asset.contentType,
    })) as AssetUploadUrl;

    const stored = await send(issued.uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': asset.contentType },
      body: asset.body,
    });

    await (stored.ok
      ? post(`/api/v1/assets/${issued.assetKey}/confirm`, {})
      : Promise.reject(
          new Error(
            `アセットを保管先へ送れませんでした（HTTP ${String(stored.status)}）: ${issued.assetKey}`,
          ),
        ));

    return issued.assetKey;
  };

  /**
   * 作品を、曲目と外部音源ごと作る（下書きのまま）。
   *
   * 作品の子を書く経路は集約ルートに1つしかないため、1リクエストで揃う（#391）。並びは送った配列の位置が
   * そのまま表すので、番号も送らず、順序を再現するための逐次送信も要らない。
   */
  const seedDraftAlbum = async (album: AlbumSeed): Promise<string> => {
    const coverImageKey =
      album.coverImage === undefined ? undefined : await seedAsset(album.coverImage);

    const created = await post('/api/v1/albums/with-tracks', {
      title: album.title,
      releaseDate: album.releaseDate,
      artistDisplayName: album.artistDisplayName,
      artistSortKey: album.artistSortKey,
      catalogNumber: album.catalogNumber,
      isdn: album.isdn,
      description: album.description,
      descriptionFormat: album.descriptionFormat,
      event: album.event,
      basePrice: album.basePrice,
      originalWorkNote: album.originalWorkNote,
      coverImageKey,
      tracks: (album.tracks ?? []).map((track) => ({
        title: track.title,
        artistDisplayName: track.artistDisplayName,
        tunes: (track.tunes ?? []).map((tune) => ({
          tuneTitle: tune.tuneTitle,
          composerCreditOverride: tune.composerCreditOverride,
          arrangerCreditOverride: tune.arrangerCreditOverride,
        })),
      })),
      externalAudios: (album.externalAudioUrls ?? []).map((url) => ({ url })),
    });

    return albumIdOf(created);
  };

  const getAdminAlbumDetail = async (albumId: string): Promise<AdminAlbumDetail> =>
    (await get(`/api/v1/admin/albums/${albumId}`)) as AdminAlbumDetail;

  const updateAlbum = async (albumId: string, request: UpdateAlbumRequest): Promise<void> => {
    await put(`/api/v1/albums/${albumId}`, request);
  };

  /** 画像だけを変更する。他の項目は最新の詳細から保つ */
  const setAlbumCoverImage = async (
    albumId: string,
    coverImageKey: string | null,
  ): Promise<void> => {
    const detail = await getAdminAlbumDetail(albumId);
    await updateAlbum(albumId, updateRequestOf(detail, coverImageKey));
  };

  /** 画像を持たない作品にだけ画像を補う。設定済みなら再アップロード・再保存しない */
  const ensureAlbumCoverImage = async (albumId: string, image: AssetSeed): Promise<void> => {
    const detail = await getAdminAlbumDetail(albumId);
    return detail.coverImageKey === null
      ? setAlbumCoverImage(albumId, await seedAsset(image))
      : undefined;
  };

  const publishAlbum = async (albumId: string): Promise<void> => {
    await post(`/api/v1/albums/${albumId}/publish`, {});
  };

  const unpublishAlbum = async (albumId: string): Promise<void> => {
    await post(`/api/v1/albums/${albumId}/unpublish`, {});
  };

  const seedPublishedAlbum = async (album: AlbumSeed): Promise<string> => {
    const albumId = await seedDraftAlbum(album);
    await publishAlbum(albumId);
    return albumId;
  };

  const deleteAlbum = (albumId: string): Promise<void> => remove(`/api/v1/albums/${albumId}`);

  /** 管理画面と同じ50件単位で、総件数とページ情報を含む作品一覧を読む */
  const fetchAdminAlbumPage = async (page = 0, catalogNumber = ''): Promise<AdminAlbumPage> =>
    (await get(
      `/api/v1/admin/albums?page=${String(page)}&size=50&catalogNumber=${encodeURIComponent(catalogNumber)}`,
    )) as AdminAlbumPage;

  /**
   * カタログナンバーで作品を引く（下書きを含む）。
   *
   * 公開の一覧には下書きが出ないため、管理APIを通す。絞り込みは部分一致のため、完全一致で選び直す。
   */
  const findAlbumByCatalogNumber = async (
    catalogNumber: string,
  ): Promise<AdminAlbum | undefined> => {
    const body = await get(
      `/api/v1/admin/albums?size=100&catalogNumber=${encodeURIComponent(catalogNumber)}`,
    );
    const { items } = body as { items: readonly AdminAlbum[] };
    return items.find((item) => item.catalogNumber === catalogNumber);
  };

  /** カタログナンバーの接頭辞で作品を探す。検査のためだけに作った作品を控えなしに片付けるために使う */
  const findAlbumsByCatalogNumberPrefix = async (
    prefix: string,
  ): Promise<readonly AdminAlbum[]> => {
    const body = await get('/api/v1/admin/albums?size=100');
    const { items } = body as { items: readonly AdminAlbum[] };
    return items.filter((item) => (item.catalogNumber ?? '').startsWith(prefix));
  };

  /** 記事を作り、作品への参照とタグを付ける（下書きのまま） */
  const seedDraftArticle = async (article: ArticleSeed): Promise<string> => {
    const created = await post('/api/v1/articles', {
      articleType: article.articleType,
      title: article.title,
      body: article.body,
      bodyFormat: article.bodyFormat,
      introShort: article.introShort,
    });

    const articleId = articleIdOf(created);

    /*
     * 参照の設定は全項目置換の PUT（作成時のリクエストは参照を持たない）。作成直後のため世代は0
     * （紐付けも記事の世代を進める契約、#323）。
     */
    await (article.albumId === undefined
      ? Promise.resolve()
      : put(`/api/v1/articles/${articleId}/album`, {
          albumId: article.albumId,
          expectedRevision: 0,
        }));

    /*
     * SEQUENTIAL-ORDER: タグは名前で追加し、無ければ作られる。並列に投げると同じ名前を同時に作る
     * 経路へ入るため、1件ずつ送る。
     */
    for (const name of article.tags ?? []) {
      await post(`/api/v1/articles/${articleId}/tags`, { name });
    }

    return articleId;
  };

  const getAdminArticleDetail = async (articleId: string): Promise<AdminArticleDetail> =>
    (await get(`/api/v1/admin/articles/${articleId}`)) as AdminArticleDetail;

  const updateArticle = async (articleId: string, request: UpdateArticleRequest): Promise<void> => {
    await put(`/api/v1/articles/${articleId}`, request);
  };

  const publishArticle = async (articleId: string): Promise<void> => {
    await post(`/api/v1/articles/${articleId}/publish`, {});
  };

  const deleteArticle = (articleId: string): Promise<void> =>
    remove(`/api/v1/articles/${articleId}`);

  const fetchAdminArticlePage = async (page: number): Promise<AdminArticlePage> =>
    (await get(`/api/v1/admin/articles?page=${String(page)}&size=100`)) as AdminArticlePage;

  /**
   * 下書きを含む全記事。
   *
   * 管理の記事一覧はタイトルでの絞り込みを持たない（作品の一覧とは非対称。検索が要るのは記事編集画面から
   * 作品を選ぶ経路だけのため）ので、全ページたぐってから選ぶ。
   */
  const allAdminArticles = async (): Promise<readonly AdminArticle[]> => {
    const firstPage = await fetchAdminArticlePage(0);
    const remainingPages = await Promise.all(
      range(Math.max(firstPage.totalPages - 1, 0)).map((index) => fetchAdminArticlePage(index + 1)),
    );
    return [firstPage, ...remainingPages].flatMap((page) => page.items);
  };

  /** タイトルで記事を引く（下書きを含む）。公開の一覧には下書きが出ないため管理APIを通す */
  const findArticleByTitle = async (title: string): Promise<AdminArticle | undefined> =>
    (await allAdminArticles()).find((item) => item.title === title);

  /** タイトルの接頭辞で記事を探す（下書きを含む） */
  const findArticlesByTitlePrefix = async (prefix: string): Promise<readonly AdminArticle[]> =>
    (await allAdminArticles()).filter((item) => item.title.startsWith(prefix));

  /** 下書きを含む記事の総件数 */
  const countArticles = async (): Promise<number> => (await fetchAdminArticlePage(0)).totalElements;

  /** 登録済みのサイト文言を全件読む。認証を要求しない経路だが、同じクライアントから読む */
  const listSiteContents = async (): Promise<readonly SiteContent[]> => {
    const body = (await get('/api/v1/site-contents')) as { items: readonly SiteContent[] };
    return body.items;
  };

  /** サイト文言を登録する（同じキーがあれば置き換える）。文言はリポジトリに置かず管理画面から入れる（#230） */
  const upsertSiteContent = async (content: SiteContentSeed): Promise<void> => {
    await put(`/api/v1/site-contents/${content.key}`, {
      content: content.content,
      contentFormat: content.contentFormat,
    });
  };

  return {
    seedAsset,
    seedDraftAlbum,
    seedPublishedAlbum,
    getAdminAlbumDetail,
    updateAlbum,
    setAlbumCoverImage,
    ensureAlbumCoverImage,
    publishAlbum,
    unpublishAlbum,
    deleteAlbum,
    fetchAdminAlbumPage,
    findAlbumByCatalogNumber,
    findAlbumsByCatalogNumberPrefix,
    seedDraftArticle,
    getAdminArticleDetail,
    updateArticle,
    publishArticle,
    deleteArticle,
    findArticleByTitle,
    findArticlesByTitlePrefix,
    countArticles,
    listSiteContents,
    upsertSiteContent,
  } as const;
};

/** {@link adminApi} が返す操作の集まり */
export type AdminApi = ReturnType<typeof adminApi>;
