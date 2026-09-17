import { renderMarkup } from "abservice-markup";
import { toEmbedUrl } from "abservice-external-audio";
import { formatCalendarDate, formatPrice } from "./format";
import { allowedImage, escape, properNoun } from "./html";

/** 作品詳細と作品紹介記事で共有する表示値。管理用の認証・世代は扱わない。 */
export type AlbumPresentation = Readonly<{
  albumId: string;
  title: string;
  artistDisplayName: string;
  originalWorkNote: string | null;
  releaseDate: string;
  catalogNumber: string | null;
  isdn: string | null;
  description: string | null;
  descriptionFormat: string;
  coverImageUrl: string | null;
  eventName: string | null;
  eventDate: string | null;
  eventPlace: string | null;
  eventSpaceNumber: string | null;
  eventCircleName: string | null;
  eventNote: string | null;
  basePrice: Readonly<{ amount: number; currency: string }> | null;
  externalAudios: readonly Readonly<{ url: string }>[];
  tracks: readonly Readonly<{
    trackNo: number;
    title: string;
    artistDisplayName: string | null;
    tunes: readonly Readonly<{
      tuneTitle: string | null;
      composerCreditOverride: string | null;
      arrangerCreditOverride: string | null;
    }>[];
  }>[];
}>;

/**
 * 頒布イベントは「日付 イベント名 会場 スペース サークル名」の順に1行で読む（#415）。会場は探す手掛かり、
 * スペース番号は当日その場で見つけるための値のため、スペース番号だけを立てる。
 */
const eventInfo = (album: AlbumPresentation): string => {
  const parts = [
    album.eventDate === null
      ? null
      : `<time datetime="${escape(album.eventDate)}">${formatCalendarDate(album.eventDate)}</time>`,
    album.eventName === null ? null : properNoun(album.eventName),
    album.eventPlace === null ? null : escape(album.eventPlace),
    album.eventSpaceNumber === null
      ? null
      : `<strong class="text-foreground font-semibold">${escape(album.eventSpaceNumber)}</strong>`,
    album.eventCircleName === null ? null : properNoun(album.eventCircleName),
  ].filter((part) => part !== null);
  return album.eventName === null
    ? ""
    : `<div data-album-event class="text-muted-foreground text-sm space-y-1"><p>${parts.join(" ")}</p>${album.eventNote === null ? "" : `<p>${escape(album.eventNote)}</p>`}</div>`;
};
type Tune = AlbumPresentation["tracks"][number]["tunes"][number];
const creditOf = (tune: Tune): string =>
  [
    tune.composerCreditOverride === null
      ? null
      : `作曲: ${tune.composerCreditOverride}`,
    tune.arrangerCreditOverride === null
      ? null
      : `編曲: ${tune.arrangerCreditOverride}`,
  ]
    .filter((value) => value !== null)
    .join(" / ");
const renderTracks = (tracks: AlbumPresentation["tracks"]): string =>
  `<ol class="divide-border divide-y">${tracks
    .map((track) => {
      const tunes = track.tunes.filter((tune) =>
        [tune.tuneTitle !== null, creditOf(tune) !== ""].some(Boolean),
      );
      return `<li class="flex gap-4 py-2"><span class="text-muted-foreground w-6 shrink-0 text-right tabular-nums">${String(track.trackNo)}</span><div class="min-w-0 space-y-1"><div>${properNoun(track.title)}${track.artistDisplayName === null ? "" : `<span class="text-muted-foreground text-sm"> / ${properNoun(track.artistDisplayName)}</span>`}</div>${tunes.length === 0 ? "" : `<ol class="text-muted-foreground space-y-0.5 text-sm">${tunes.map((tune) => `<li>${tune.tuneTitle === null ? "" : properNoun(tune.tuneTitle)}${creditOf(tune) === "" ? "" : `<span>（${escape(creditOf(tune))}）</span>`}</li>`).join("")}</ol>`}</div></li>`;
    })
    .join("")}</ol>`;

/** 本文と作品概要の描画を同じサニタイザーへ通す。 */
export const renderBody = (
  body: string | null,
  format: string,
  assetBasePath: string,
): string =>
  [body === null, body === ""].some(Boolean)
    ? ""
    : format === "MARKDOWN"
      ? `<div class="prose-body">${renderMarkup(body ?? "", { assetBasePath })}</div>`
      : `<p class="whitespace-pre-wrap">${escape(body ?? "")}</p>`;

/**
 * 記事の前置きは試聴と曲目の間。introHtml は共有描画の出力専用で、入力文字列は渡さない。
 *
 * 記事へ展開する（embedded）ときは、作品の見出しと発売日を出さない——記事の見出しが作品を名指しており、
 * 同じ大きさの見出しが2つ並ぶと段差が読めない。発売日は記事の公開日と無ラベルで並ぶと見分けられない。
 */
export const renderAlbum = (
  album: AlbumPresentation,
  assetBasePath: string,
  options: Readonly<{
    embedded?: boolean;
    defaultArtistName?: string | null;
    introHtml?: string;
  }> = {},
): string => {
  const embedded = options.embedded === true;
  const subheading = embedded ? "h3" : "h2";
  const identifiers = [album.catalogNumber, album.isdn]
    .filter((value) => value !== null)
    .join(" / ");
  const cover =
    album.externalAudios.length === 0 && allowedImage(album.coverImageUrl)
      ? `<img class="border-border w-full max-w-64 rounded-md border object-cover" src="${escape(album.coverImageUrl)}" alt="" decoding="async" />`
      : "";
  const headline = embedded
    ? ""
    : `<h1 class="text-3xl font-semibold">${properNoun(album.title)}</h1>`;
  const artist =
    album.artistDisplayName === options.defaultArtistName
      ? ""
      : `<p class="text-muted-foreground">${properNoun(album.artistDisplayName)}</p>`;
  const releaseDate = embedded
    ? ""
    : `<p class="text-muted-foreground text-sm"><time datetime="${escape(album.releaseDate)}">${formatCalendarDate(album.releaseDate)}</time></p>`;
  const identifierLine =
    identifiers === ""
      ? ""
      : `<p class="text-muted-foreground text-sm">${properNoun(identifiers)}</p>`;
  const facts = `${headline}${artist}${releaseDate}${identifierLine}`;
  const header =
    cover === "" && facts === ""
      ? ""
      : `<header class="flex flex-col gap-4 sm:flex-row">${cover}${facts === "" ? "" : `<div class="min-w-0 space-y-2">${facts}</div>`}</header>`;
  /*
   * 試聴は正方形で、幅いっぱい・上限 700px。埋め込みプレイヤーの絵（アートワーク）を作品の顔として使う
   * 意図のため、コンパクトな帯ではなく絵が出る大きさを取る。狭い幅では幅に合わせて縮む。
   */
  const audio =
    album.externalAudios.length === 0
      ? ""
      : `<section data-album-audio class="space-y-4"><${subheading} class="text-lg font-medium">試聴</${subheading}>${album.externalAudios.map((item) => `<iframe class="border-border aspect-square max-h-[700px] w-full rounded-md border" src="${escape(toEmbedUrl(item.url))}" title="${escape(album.title)} の試聴" loading="lazy" referrerpolicy="strict-origin-when-cross-origin" allow="autoplay"></iframe>`).join("")}</section>`;
  /*
   * 曲目は畳んで置き、読みたい人が開く。長い曲目が記事の本文とイベント情報の間を押し広げないため。
   * 開閉はブラウザの details に任せ、スクリプトを要らなくする（プレビュー文書はスクリプトを実行しない）。
   */
  const tracks =
    album.tracks.length === 0
      ? ""
      : `<details data-album-tracks class="space-y-4"><summary class="cursor-pointer"><${subheading} class="inline text-lg font-medium">曲目</${subheading}></summary>${renderTracks(album.tracks)}</details>`;
  /*
   * 原作の出典は曲目の後ろ。曲目全体を指す一文（「「○○」より各曲」）で、どのトラックがどれかは述べない
   * ため、曲目の行には入れず、並びの直後に置く（#365）。
   */
  const originalWork =
    album.originalWorkNote === null
      ? ""
      : `<p data-album-original-work class="text-muted-foreground">${escape(album.originalWorkNote)}</p>`;
  const price =
    embedded && album.basePrice !== null
      ? `<p data-album-price class="text-muted-foreground text-sm">頒布価格 ${formatPrice(album.basePrice.amount, album.basePrice.currency)}</p>`
      : "";
  return `<section data-public-album class="space-y-8" aria-label="${escape(album.title)}">${header}${renderBody(album.description, album.descriptionFormat, assetBasePath)}${audio}${options.introHtml ?? ""}${tracks}${originalWork}${eventInfo(album)}${price}</section>`;
};
