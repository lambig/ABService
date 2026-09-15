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

const eventInfo = (album: AlbumPresentation): string => {
  const place = [album.eventPlace, album.eventSpaceNumber]
    .filter((value) => value !== null)
    .join(" ");
  return album.eventName === null
    ? ""
    : `<div data-album-event class="text-muted-foreground text-sm">${properNoun(album.eventName)}${album.eventDate === null ? "" : ` <time datetime="${escape(album.eventDate)}">${formatCalendarDate(album.eventDate)}</time>`}${place === "" ? "" : ` ${escape(place)}`}${album.eventNote === null ? "" : `<p>${escape(album.eventNote)}</p>`}</div>`;
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

/** 記事の前置きは試聴と曲目の間。introHtml は共有描画の出力専用で、入力文字列は渡さない。 */
export const renderAlbum = (
  album: AlbumPresentation,
  assetBasePath: string,
  options: Readonly<{
    embedded?: boolean;
    defaultArtistName?: string | null;
    introHtml?: string;
  }> = {},
): string => {
  const heading = options.embedded === true ? "h2" : "h1";
  const subheading = options.embedded === true ? "h3" : "h2";
  const identifiers = [album.catalogNumber, album.isdn]
    .filter((value) => value !== null)
    .join(" / ");
  const cover =
    album.externalAudios.length === 0 && allowedImage(album.coverImageUrl)
      ? `<img class="border-border w-full max-w-64 rounded-md border object-cover" src="${escape(album.coverImageUrl)}" alt="" decoding="async" />`
      : "";
  const audio =
    album.externalAudios.length === 0
      ? ""
      : `<section data-album-audio class="space-y-4"><${subheading} class="text-lg font-medium">試聴</${subheading}>${album.externalAudios.map((item) => `<iframe class="border-border w-full rounded-md border" src="${escape(toEmbedUrl(item.url))}" title="${escape(album.title)} の試聴" height="166" loading="lazy" referrerpolicy="strict-origin-when-cross-origin" allow="autoplay"></iframe>`).join("")}</section>`;
  const tracks =
    album.tracks.length === 0
      ? ""
      : `<section data-album-tracks class="space-y-4"><${subheading} class="text-lg font-medium">曲目</${subheading}>${renderTracks(album.tracks)}</section>`;
  const price =
    options.embedded === true && album.basePrice !== null
      ? `<p data-album-price class="text-muted-foreground text-sm">${formatPrice(album.basePrice.amount, album.basePrice.currency)}</p>`
      : "";
  return `<section data-public-album class="space-y-8" aria-label="${escape(album.title)}"><header class="flex flex-col gap-4 sm:flex-row">${cover}<div class="min-w-0 space-y-2"><${heading} class="text-3xl font-semibold">${properNoun(album.title)}</${heading}>${album.artistDisplayName === options.defaultArtistName ? "" : `<p class="text-muted-foreground">${properNoun(album.artistDisplayName)}</p>`}${album.originalWorkNote === null ? "" : `<p class="text-muted-foreground">${escape(album.originalWorkNote)}</p>`}<p class="text-muted-foreground text-sm"><time datetime="${escape(album.releaseDate)}">${formatCalendarDate(album.releaseDate)}</time></p>${identifiers === "" ? "" : `<p class="text-muted-foreground text-sm">${properNoun(identifiers)}</p>`}</div></header>${renderBody(album.description, album.descriptionFormat, assetBasePath)}${audio}${options.introHtml ?? ""}${tracks}${eventInfo(album)}${price}</section>`;
};
