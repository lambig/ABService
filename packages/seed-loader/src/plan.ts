import type { AdminApi, AdminArticle, SiteContent } from 'abservice-admin-api';
import type { SeedAlbum, SeedArticle, SeedContent } from './seed.ts';

/**
 * 投入先の今の状態と、投入内容を突き合わせて「何をするか」を決める。
 *
 * <p>
 * 決め方は**無いものだけを作る**。公開後の内容の正は DB（管理画面）であり、投入ファイルは一度きりの
 * 出発点でしかない（#375）。存在する作品・記事・文言の内容は揃えず、そのことを報告に出す。例外は
 * 「作ったが公開まで進まなかった」「作ったが画像が付かなかった」という途中の状態で、これは前回の実行が
 * 中断した跡なので次の実行が埋める。
 * </p>
 */

/** 投入先に既にある作品 */
export interface ExistingAlbum {
  readonly albumId: string;
  readonly published: boolean;
  readonly hasCoverImage: boolean;
}

/** 投入先の今の状態。計画を立てるのに要る分だけを持つ */
export interface Snapshot {
  /** カタログナンバー → 作品 */
  readonly albums: ReadonlyMap<string, ExistingAlbum>;
  /** タイトル → 記事 */
  readonly articles: ReadonlyMap<string, AdminArticle>;
  /** キー → 文言 */
  readonly siteContents: ReadonlyMap<string, SiteContent>;
}

/** 文言1件の扱い */
export interface SiteContentStep {
  readonly kind: 'site-content';
  readonly key: string;
  readonly create: boolean;
  readonly note?: string;
}

/** 作品1件の扱い。どれも false なら何もしない */
export interface AlbumStep {
  readonly kind: 'album';
  readonly catalogNumber: string;
  readonly create: boolean;
  readonly addCoverImage: boolean;
  readonly publish: boolean;
  readonly note?: string;
}

/** 記事1件の扱い。どれも false なら何もしない */
export interface ArticleStep {
  readonly kind: 'article';
  readonly title: string;
  readonly create: boolean;
  readonly publish: boolean;
  readonly note?: string;
}

/** 実行の計画。`problems` が1つでもあれば実行へ進めない */
export interface Plan {
  readonly siteContents: readonly SiteContentStep[];
  readonly albums: readonly AlbumStep[];
  readonly articles: readonly ArticleStep[];
  readonly problems: readonly string[];
}

const unique = (values: readonly string[]): readonly string[] => [...new Set(values)];

/** 投入内容が同定に使う値について、投入先の今の状態を読む */
export const takeSnapshot = async (api: AdminApi, seed: SeedContent): Promise<Snapshot> => {
  const catalogNumbers = unique([
    ...seed.albums.map((album) => album.catalogNumber),
    ...seed.articles.flatMap((article) =>
      article.albumCatalogNumber === undefined ? [] : [article.albumCatalogNumber],
    ),
  ]);

  const albums = await Promise.all(
    catalogNumbers.map(async (catalogNumber) => {
      const found = await api.findAlbumByCatalogNumber(catalogNumber);
      const detail = found === undefined ? undefined : await api.getAdminAlbumDetail(found.albumId);
      return detail === undefined
        ? []
        : [
            [
              catalogNumber,
              {
                albumId: detail.albumId,
                published: detail.publishedAt !== null,
                hasCoverImage: detail.coverImageKey !== null,
              },
            ] as const,
          ];
    }),
  );

  const articles = await api.findArticlesByTitlePrefix('');
  const siteContents = await api.listSiteContents();

  return {
    albums: new Map(albums.flat()),
    articles: new Map(articles.map((article) => [article.title, article] as const)),
    siteContents: new Map(siteContents.map((item) => [item.key, item] as const)),
  };
};

const planSiteContent = (snapshot: Snapshot, key: string, content: string): SiteContentStep => {
  const existing = snapshot.siteContents.get(key);
  return existing === undefined
    ? { kind: 'site-content', key, create: true }
    : {
        kind: 'site-content',
        key,
        create: false,
        note:
          existing.content === content
            ? '登録済み（同じ内容）'
            : '登録済み（内容が異なる。管理画面の値を正として、ここでは揃えない）',
      };
};

const planAlbum = (snapshot: Snapshot, album: SeedAlbum): AlbumStep => {
  const existing = snapshot.albums.get(album.catalogNumber);
  return existing === undefined
    ? {
        kind: 'album',
        catalogNumber: album.catalogNumber,
        create: true,
        addCoverImage: false,
        publish: album.published,
      }
    : {
        kind: 'album',
        catalogNumber: album.catalogNumber,
        create: false,
        addCoverImage: existing.hasCoverImage ? false : album.coverImage !== undefined,
        publish: existing.published ? false : album.published,
        note: '登録済み（内容は揃えない）',
      };
};

const planArticle = (snapshot: Snapshot, article: SeedArticle): ArticleStep => {
  const existing = snapshot.articles.get(article.title);
  return existing === undefined
    ? { kind: 'article', title: article.title, create: true, publish: article.published }
    : {
        kind: 'article',
        title: article.title,
        create: false,
        publish: article.published && existing.publishedAt === null,
        note: '登録済み（内容は揃えない）',
      };
};

const referenceProblems = (seed: SeedContent, snapshot: Snapshot): readonly string[] =>
  seed.articles.flatMap((article) => {
    const target = article.albumCatalogNumber;
    const problem = (catalogNumber: string): readonly string[] =>
      seed.albums.some((album) => album.catalogNumber === catalogNumber)
        ? []
        : snapshot.albums.has(catalogNumber)
          ? []
          : [
              `記事「${article.title}」が参照する作品 ${catalogNumber} は、投入内容にも投入先にもありません`,
            ];
    return target === undefined ? [] : problem(target);
  });

/** 投入内容と今の状態から計画を立てる */
export const planSeed = (seed: SeedContent, snapshot: Snapshot): Plan => ({
  siteContents: seed.siteContents.map((item) => planSiteContent(snapshot, item.key, item.content)),
  albums: seed.albums.map((album) => planAlbum(snapshot, album)),
  articles: seed.articles.map((article) => planArticle(snapshot, article)),
  problems: referenceProblems(seed, snapshot),
});

const actionsOf = (flags: Readonly<Record<string, boolean>>): string => {
  const names = Object.entries(flags)
    .filter(([, enabled]) => enabled)
    .map(([name]) => name);
  return names.length === 0 ? 'なし' : names.join(' → ');
};

const withNote = (line: string, note: string | undefined): string =>
  note === undefined ? line : `${line}（${note}）`;

/** 計画を人が読む行に写す。dry-run の出力であり、実行時にも先に出す */
export const describePlan = (plan: Plan): readonly string[] => [
  `文言 ${String(plan.siteContents.length)} 件`,
  ...plan.siteContents.map((step) =>
    withNote(`  ${step.key}: ${step.create ? '登録' : 'なし'}`, step.note),
  ),
  `作品 ${String(plan.albums.length)} 件`,
  ...plan.albums.map((step) =>
    withNote(
      `  ${step.catalogNumber}: ${actionsOf({ 作成: step.create, 画像: step.addCoverImage, 公開: step.publish })}`,
      step.note,
    ),
  ),
  `記事 ${String(plan.articles.length)} 件`,
  ...plan.articles.map((step) =>
    withNote(`  ${step.title}: ${actionsOf({ 作成: step.create, 公開: step.publish })}`, step.note),
  ),
  ...(plan.problems.length === 0
    ? []
    : ['問題', ...plan.problems.map((problem) => `  ${problem}`)]),
];
