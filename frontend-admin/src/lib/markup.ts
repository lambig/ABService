import { renderMarkup } from 'abservice-markup';
import { PUBLIC_ASSET_BASE_PATH } from 'astro:env/client';

/**
 * 本文（Markdown）をサニタイズ済みの HTML へ描画する。
 *
 * 描画そのものは共有パッケージの純粋関数が担い、ここは配信ベースパスを与えるだけ（DECISIONS 24）。
 * 公開サイトも同じ関数を呼ぶため、プレビューと公開の見えかたは構造的に一致する。
 */
export const renderBody = (markdown: string): string =>
  renderMarkup(markdown, { assetBasePath: PUBLIC_ASSET_BASE_PATH });
