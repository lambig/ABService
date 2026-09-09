import { renderMarkup } from 'abservice-markup';
import { PUBLIC_ASSET_BASE_PATH } from 'astro:env/client';

/**
 * 本文（Markdown）をサニタイズ済みの HTML へ描画する。
 *
 * 描画そのものは共有パッケージの純粋関数が担い、ここは配信ベースパスを与えるだけ（DECISIONS 24）。
 * 公開サイトも同じ関数を、同じ名前の変数から得た同じ値で呼ぶため、プレビューと公開の見えかたは
 * 構造的に一致する。**変数を分けると、片方だけを変えたときにプレビューが嘘になる**（#289 から #122
 * へ引き継いだ受け入れ条件）。
 */
export const renderBody = (markdown: string): string =>
  renderMarkup(markdown, { assetBasePath: PUBLIC_ASSET_BASE_PATH });
