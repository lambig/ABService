/**
 * サイトの記号（#341）。
 *
 * <p>
 * 値の出所は画面の実装（`BaseLayout.astro` と `PreviewMeta.astro`、焼く側は
 * `frontend-public/scripts/render-og-image.mjs`）。シナリオ側へ写しているのは、**画面が何を指すと
 * 決めたのか**を検査が知っている必要があるため。ずれたらシナリオが落ちる。
 * </p>
 */

/** サイトの印。どのページも同じものを指す */
export const FAVICON_PATH = '/favicon.svg';

/** リンクプレビューの既定画像。作品から採る画像が無いページで使う */
export const DEFAULT_PREVIEW_IMAGE = '/og-default.png';

/**
 * 既定画像の大きさ。
 *
 * リンクプレビューを読む側の多くがこの比で切り出す。焼く側が変えたらここも変わる——画像が届くこと
 * だけを見ていると、比が崩れて切れた絵になっても緑のままになる。
 */
export const PREVIEW_IMAGE_SIZE = { width: 1200, height: 630 } as const;
