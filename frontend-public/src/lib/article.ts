import type { PublicArticle, PublicArticleDetail } from '$lib/api/client';

/**
 * 記事が参照するアルバムのID。
 *
 * アルバムへの参照を持てるのは ALBUM 種別だけで、他の種別は項目そのものを持たない（#204）。
 * 参照を持たない ALBUM 記事もあるため、値が無いことと項目が無いことをここで同じ `null` へ畳む。
 */
export const referencedAlbumId = (article: PublicArticle | PublicArticleDetail): string | null =>
  'albumId' in article ? (article.albumId ?? null) : null;
