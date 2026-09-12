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

/**
 * 既定の名義。未登録なら undefined。
 *
 * サイト全体が1つの名義の作品を並べる場のため、既定の名義はどの作品にも同じ値が並ぶ。読み手にとって
 * 情報量が無く、そのぶん臨時ユニットのような例外が目立たなくなる（#348）。
 */
export const defaultArtistName = async (): Promise<string | undefined> =>
  (await contentOf('site.artist'))?.content;

/**
 * その作品の名義を画面に出すか。
 *
 * 既定の名義が未登録のときは出す。未登録を「すべて既定」と読むと、名義そのものが画面から消える
 * （#230 の「未設定ならその区画を出さない」は、文言が無いときに区画を出さない話であって、
 * 作品の持つ事実を消してよいという話ではない）。
 */
export const showsArtistName = async (artistDisplayName: string): Promise<boolean> =>
  artistDisplayName !== (await defaultArtistName());
