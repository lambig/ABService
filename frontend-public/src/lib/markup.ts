import { renderMarkup } from 'abservice-markup';
import { ASSET_BASE_PATH } from 'astro:env/server';

/**
 * 本文（Markdown）をサニタイズ済みの HTML へ描画する。
 *
 * 描画そのものは共有パッケージの純粋関数が担い、ここは配信ベースパスを与えるだけ（DECISIONS 24）。
 * 管理画面のプレビューも同じ関数を呼ぶため、公開の見えかたとプレビューは構造的に一致する。
 */
export const renderBody = (markdown: string): string =>
  renderMarkup(markdown, { assetBasePath: ASSET_BASE_PATH });
