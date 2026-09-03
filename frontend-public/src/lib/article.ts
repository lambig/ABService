import type { PublicArticle, PublicArticleDetail } from '$lib/api/client';

/**
 * 記事種別の表示文言。
 *
 * 契約は列挙子名で、見せかたはクライアントが決める（#197）。定義に無い種別が来たら列挙子名をそのまま
 * 出す。翻訳した名前を勝手に作るより、契約の値が見えている方が追える。
 */
const TYPE_LABELS: Readonly<Record<string, string>> = {
  ALBUM: '作品紹介',
  NOTE: 'ノート',
  NEWS: 'お知らせ',
  EVENT: 'イベント',
  OTHER: 'その他',
};

/**
 * 記事種別の表示文言を返す。
 *
 * @param articleType
 *            記事種別（列挙子名）
 */
export const articleTypeLabel = (articleType: string): string =>
  TYPE_LABELS[articleType] ?? articleType;

/**
 * 記事が参照するアルバムのID。
 *
 * アルバムへの参照を持てるのは ALBUM 種別だけで、他の種別は項目そのものを持たない（#204）。
 * 参照を持たない ALBUM 記事もあるため、値が無いことと項目が無いことをここで同じ `null` へ畳む。
 */
export const referencedAlbumId = (article: PublicArticle | PublicArticleDetail): string | null =>
  'albumId' in article ? (article.albumId ?? null) : null;
