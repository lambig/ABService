import { renderMarkup } from 'abservice-markup';
import { formatCalendarDate, formatPrice, formatPublishedDate } from './format';

export { formatCalendarDate, formatPrice, formatPublishedDate } from './format';

/** 公開記事の作品参照で使う値。管理用の認証情報や編集世代を受け取らない。 */
export type ReferencedAlbum = Readonly<{
  albumId: string;
  title: string;
  coverImageUrl: string | null;
  eventName: string | null;
  eventDate: string | null;
  eventPlace: string | null;
  eventSpaceNumber: string | null;
  eventNote: string | null;
  basePrice: Readonly<{ amount: number; currency: string }> | null;
}>;

/** 保存済み・編集中のどちらからも作れる記事の表示値。未公開なら公開日時を持たない。 */
export type ArticlePresentation = Readonly<{
  title: string;
  body: string;
  bodyFormat: string;
  publishedAt: string | null;
  tags: readonly string[];
  album: ReferencedAlbum | null;
}>;

const escape = (value: string): string =>
  value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');

const properNoun = (value: string): string =>
  `<span translate="no" class="notranslate">${escape(value)}</span>`;

const eventInfo = (album: ReferencedAlbum): string => {
  const place = [album.eventPlace, album.eventSpaceNumber]
    .filter((value) => value !== null)
    .join(' ');
  return album.eventName === null
    ? ''
    : `<div class="text-muted-foreground text-sm">${properNoun(album.eventName)}${album.eventDate === null ? '' : ` <time datetime="${escape(album.eventDate)}">${formatCalendarDate(album.eventDate)}</time>`}${place === '' ? '' : ` ${escape(place)}`}${album.eventNote === null ? '' : `<p>${escape(album.eventNote)}</p>`}</div>`;
};

/** 作品への参照カード。文字列を HTML として解釈せず、画像は HTTP(S) または同一サイトの絶対パスのみ。 */
export const renderAlbumReference = (album: ReferencedAlbum): string => {
  const cover = album.coverImageUrl;
  const allowedCover = cover !== null && /^(?:https?:\/\/|\/(?![/\\]))[^\s\\]*$/i.test(cover);
  return `<a class="border-border hover:bg-muted/50 flex gap-4 rounded-md border p-4 transition-colors" href="/albums/${encodeURIComponent(album.albumId)}">${allowedCover ? `<img class="size-24 shrink-0 rounded object-cover" src="${escape(cover)}" alt="" loading="lazy" decoding="async" />` : ''}<div class="min-w-0 space-y-1"><p class="font-medium">${properNoun(album.title)}</p>${eventInfo(album)}${album.basePrice === null ? '' : `<p class="text-muted-foreground text-sm">${formatPrice(album.basePrice.amount, album.basePrice.currency)}</p>`}</div></a>`;
};

/** 公開詳細と管理プレビューが共有する記事全体。本文だけでなく見出し・タグ・作品参照の配置もここで決める。 */
export const renderArticle = (article: ArticlePresentation, assetBasePath: string): string => {
  const body =
    article.body === ''
      ? ''
      : article.bodyFormat === 'MARKDOWN'
        ? `<div class="prose-body">${renderMarkup(article.body, { assetBasePath })}</div>`
        : `<p class="whitespace-pre-wrap">${escape(article.body)}</p>`;
  return `<article class="flex gap-3 py-6"><div class="border-primary shrink-0 border-r-2 pt-12"><p class="text-primary rotate-180 text-xs leading-none font-medium tracking-widest italic [writing-mode:vertical-rl]">Article</p></div><div class="min-w-0 flex-1 space-y-8"><header class="space-y-2"><h1 class="text-3xl font-semibold">${escape(article.title)}</h1>${article.publishedAt === null ? '' : `<p class="text-muted-foreground text-sm"><time datetime="${escape(article.publishedAt)}">${formatPublishedDate(article.publishedAt)}</time></p>`}${article.tags.length === 0 ? '' : `<ul class="text-muted-foreground flex flex-wrap gap-2 text-sm">${article.tags.map((tag) => `<li class="border-border rounded-full border px-2 py-0.5">${escape(tag)}</li>`).join('')}</ul>`}</header>${body}${article.album === null ? '' : `<section class="space-y-4"><h2 class="text-lg font-medium">この記事の作品</h2>${renderAlbumReference(article.album)}</section>`}</div></article>`;
};

/** 公開サイト共通の主要導線。プレビュー側では親画面がリンク移動だけを止める。 */
export const renderSiteNav = (): string =>
  `<nav class="border-border border-y py-2 lg:sticky lg:top-6 lg:border-y-0 lg:py-0" aria-label="主要な導線"><ul class="flex gap-6 text-sm lg:flex-col lg:gap-0 lg:leading-9">${(
    [
      ['/', 'トップ'],
      ['/albums', '作品'],
      ['/articles', '記事'],
    ] as const
  )
    .map(
      ([href, label]) =>
        `<li><a class="underline underline-offset-4" href="${href}">${label}</a></li>`,
    )
    .join('')}</ul></nav>`;

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
export const renderPageFrame = (frame: PageFrame, contentHtml: string, navHtml: string): string => {
  const tag = frame.isHome ? 'h1' : 'p';
  return `<div class="mx-auto flex min-h-screen max-w-5xl flex-col px-4 lg:max-w-6xl"><header class="py-6"><${tag} class="text-4xl font-semibold"><a class="underline-offset-4 hover:underline" href="/">${escape(frame.name)}</a></${tag}></header><div class="flex-1 lg:grid lg:grid-cols-[9rem_1fr] lg:gap-10"><div class="lg:pt-6">${navHtml}</div><main class="min-w-0">${contentHtml}</main></div>${frame.copyrightHolder === null ? '' : `<footer class="text-muted-foreground border-border border-t py-6 text-sm"><p>© ${String(frame.year)} ${escape(frame.copyrightHolder)}</p></footer>`}</div>`;
};
