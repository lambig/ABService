import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import rehypeStringify from 'rehype-stringify';
import remarkDirective from 'remark-directive';
import remarkGfm from 'remark-gfm';
import remarkParse from 'remark-parse';
import remarkRehype from 'remark-rehype';
import { unified } from 'unified';

import { rehypeRestrictImageSource } from './asset-image.js';
import { remarkDetailsDirective } from './details.js';

/**
 * 描画の設定。
 *
 * 値の出所は利用側が持つ。パッケージが環境変数を読むと純粋関数でなくなり、ビルド時（公開サイト）と
 * ブラウザ（管理画面のプレビュー）で参照先が変わって「同じ関数」の保証が崩れる。
 */
export type RenderOptions = {
  /** アセットの配信ベースパス（例: `/assets`）。同一 origin の root-relative path を指定する（ルート `/` は不可）。画像の `src` をURL解決後もこの配下に限る */
  readonly assetBasePath: string;
};

/**
 * 許可するタグの一覧。
 *
 * `rehype-sanitize` の既定に `details` / `summary` を加える。既定に無いのは、これらが生HTMLとして
 * 書かれることを想定した一覧だから。ここでは**描画側が組み立てたタグ**を通すために加える。入力の生HTMLは
 * パースしない（`remarkRehype` に `allowDangerousHtml` を渡さず、`rehype-raw` も使わない）ため、
 * 入力から `<details>` が入る経路はない。
 */
const sanitizeSchema = {
  ...defaultSchema,
  tagNames: [...(defaultSchema.tagNames ?? []), 'details', 'summary'],
};

/**
 * Markdown をサニタイズ済みの HTML へ描画する。
 *
 * <p>
 * 公開サイトはビルド時、管理画面のプレビューはブラウザで、同じこの関数を呼ぶ（DECISIONS 24）。同期で
 * 処理するため、Astro のテンプレートでも Svelte のリアクティブ式でもそのまま使える。
 * </p>
 *
 * <p>
 * 通す構文は CommonMark + GFM と、折りたたみのディレクティブ記法（`:::details[ラベル]`）。**生HTMLは
 * 描画の対象にせず、タグもテキストも出力しない**（`remarkRehype` に `allowDangerousHtml` を渡さない
 * ため、html ノードごと落ちる）。落ちるのは生HTMLの部分だけで、前後の Markdown は描画される。
 * **クラスは出力しない**（スタイルは利用側が祖先要素のクラスで当てる。DECISIONS 25）。
 * </p>
 *
 * @param markdown
 *            入力の Markdown
 * @param options
 *            描画の設定
 * @returns サニタイズ済みの HTML 文字列
 */
export function renderMarkup(markdown: string, options: RenderOptions): string {
  return String(
    unified()
      .use(remarkParse)
      .use(remarkGfm)
      .use(remarkDirective)
      .use(remarkDetailsDirective)
      .use(remarkRehype)
      .use(rehypeSanitize, sanitizeSchema)
      .use(rehypeRestrictImageSource, options.assetBasePath)
      .use(rehypeStringify)
      .processSync(markdown),
  );
}
