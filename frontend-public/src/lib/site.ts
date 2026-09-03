import { listSiteContents, type SiteContent } from '$lib/api/client';

/**
 * サイトの文言（#230）。
 *
 * 文言はリポジトリへ置かず、管理画面から登録したものを引く。未登録のキーは応答に現れないため、利用側は
 * 「その区画を出さない」。
 *
 * ビルド1回につき1度だけ取得する。すべてのページがサイト名を要するため、ページごとに引くと同じ応答を
 * 何度も取りに行くことになる。呼ばれるのはビルド時だけで、取得に失敗すればビルドが落ちる。
 */
const contents = listSiteContents();

/**
 * 文言が無いときのサイト名。
 *
 * 未設定だとページのタイトルが空になるため、無内容な文字列を1つだけ置く（#230）。実際の名前はデータ側に
 * あり、ここからは読み取れない。
 */
const FALLBACK_SITE_NAME = 'Site';

const contentOf = async (key: string): Promise<SiteContent | undefined> =>
  (await contents).find((content) => content.key === key);

/** サイト名。ページのタイトル・ヘッダー・リンクプレビューに使う */
export const siteName = async (): Promise<string> =>
  (await contentOf('site.name'))?.content ?? FALLBACK_SITE_NAME;

/** サイトの説明。未登録なら undefined（メタ説明そのものを出さない） */
export const siteDescription = async (): Promise<string | undefined> =>
  (await contentOf('site.description'))?.content;

/** トップの紹介文。未登録なら undefined（紹介の区画ごと出さない） */
export const homeIntroduction = async (): Promise<SiteContent | undefined> =>
  contentOf('home.introduction');
