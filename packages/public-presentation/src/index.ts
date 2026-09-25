import { formatPublishedDate } from "./format";
import { escape } from "./html";
import { renderAlbum, renderBody, renderBodyParts, type AlbumPresentation } from "./album";

export { formatCalendarDate, formatPrice, formatPublishedDate } from "./format";
export { renderAlbum, type AlbumPresentation } from "./album";

/** 記事の表示値。参照作品も詳細を共有し、管理用の認証情報は描画しない。 */
export type ArticlePresentation = Readonly<{
  title: string;
  body: string;
  bodyFormat: string;
  publishedAt: string | null;
  tags: readonly string[];
  album: AlbumPresentation | null;
}>;

/** 入力に追従して更新する記事の見出し。作品のプレイヤーを作り直さないため分離する。 */
export const renderArticleHeader = (
  article: Pick<ArticlePresentation, "title" | "publishedAt" | "tags">,
): string =>
  `<h1 class="text-3xl font-semibold">${escape(article.title)}</h1>${article.publishedAt === null ? "" : `<p class="text-muted-foreground text-sm"><time datetime="${escape(article.publishedAt)}">${formatPublishedDate(article.publishedAt)}</time></p>`}${article.tags.length === 0 ? "" : `<ul class="text-muted-foreground flex flex-wrap gap-2 text-sm">${article.tags.map((tag) => `<li class="border-border rounded-full border px-2 py-0.5">${escape(tag)}</li>`).join("")}</ul>`}`;

/** 入力中の本文を公開側と同じサニタイザーで描画する。 */
export const renderArticleBody = (
  article: Pick<ArticlePresentation, "body" | "bodyFormat">,
  assetBasePath: string,
): string => renderBody(article.body, article.bodyFormat, assetBasePath);

/**
 * 作品紹介の本文を、曲目の前の説明と後ろの補足へ描画する。
 * @param article 入力本文と形式。
 * @param assetBasePath 許可する画像配信先。
 * @returns 説明と補足のサニタイズ済みHTML。
 */
export const renderArticleBodyParts = (
  article: Pick<ArticlePresentation, "body" | "bodyFormat">,
  assetBasePath: string,
): Readonly<{ lead: string; details: string }> => renderBodyParts(article.body, article.bodyFormat, assetBasePath);

/** 作品紹介記事は本文を一度だけ使い、冒頭の説明の直後へ曲目を置く。 */
export const renderArticle = (
  article: ArticlePresentation,
  assetBasePath: string,
  defaultArtistName: string | null = null,
): string => {
  const body = `<div data-article-body>${renderArticleBody(article, assetBasePath)}</div>`;
  const parts = renderArticleBodyParts(article, assetBasePath);
  const content =
    article.album === null
      ? body
      : renderAlbum(article.album, assetBasePath, {
          embedded: true,
          defaultArtistName,
          bodyParts: {
            lead: `<div data-article-body>${parts.lead}</div>`,
            details: `<div data-article-details class="empty:hidden">${parts.details}</div>`,
          },
        });
  return `<article class="flex gap-3 py-6"><div class="border-primary shrink-0 border-r-2 pt-12"><p class="text-primary rotate-180 text-xs leading-none font-medium tracking-widest italic [writing-mode:vertical-rl]">Article</p></div><div class="min-w-0 flex-1 space-y-8"><header data-article-header class="space-y-2">${renderArticleHeader(article)}</header>${content}</div></article>`;
};

/** 公開サイト共通の主要導線。プレビュー側では親画面がリンク移動だけを止める。 */
export const renderSiteNav = (): string =>
  `<nav class="border-border border-y py-2 lg:sticky lg:top-6 lg:border-y-0 lg:py-0" aria-label="主要な導線"><ul class="flex gap-6 text-sm lg:flex-col lg:gap-0 lg:leading-9">${(
    [
      ["/", "トップ"],
      ["/albums", "作品"],
      ["/articles", "記事"],
    ] as const
  )
    .map(
      ([href, label]) =>
        `<li><a class="underline underline-offset-4" href="${href}">${label}</a></li>`,
    )
    .join("")}</ul></nav>`;

/** サイト名とフッターの表示値。実際の運用内容は呼び出し側から渡す。 */
export type PageFrame = Readonly<{
  name: string;
  isHome: boolean;
  copyrightHolder: string | null;
  year: number;
}>;

/**
 * 公開サイトとプレビューの外枠。1024px から導線を脇へ出し、サイト名はページ見出しより大きくする。
 * contentHtml/navHtml は Astro の描画済みスロットか本パッケージの出力専用。未処理の入力は渡さない。
 */
export const renderPageFrame = (
  frame: PageFrame,
  contentHtml: string,
  navHtml: string,
): string => {
  const tag = frame.isHome ? "h1" : "p";
  return `<div class="mx-auto flex min-h-screen max-w-5xl flex-col px-4 lg:max-w-6xl"><header class="py-6"><${tag} class="text-4xl font-semibold"><a class="underline-offset-4 hover:underline" href="/">${escape(frame.name)}</a></${tag}></header><div class="flex-1 lg:grid lg:grid-cols-[9rem_1fr] lg:gap-10"><div class="lg:pt-6">${navHtml}</div><main class="min-w-0">${contentHtml}</main></div>${frame.copyrightHolder === null ? "" : `<footer class="text-muted-foreground border-border border-t py-6 text-sm"><p>© ${String(frame.year)} ${escape(frame.copyrightHolder)}</p></footer>`}</div>`;
};
