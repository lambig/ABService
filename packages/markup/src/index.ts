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

const renderer = (options: RenderOptions) =>
  unified()
    .use(remarkParse)
    .use(remarkGfm)
    .use(remarkDirective)
    .use(remarkDetailsDirective)
    .use(remarkRehype)
    .use(rehypeSanitize, sanitizeSchema)
    .use(rehypeRestrictImageSource, options.assetBasePath)
    .use(rehypeStringify);

/**
 * 冒頭の説明と、次の見出しから始まる補足を描画する。
 * 文書全体を変換してから最上位の節だけで分けるため、参照リンク・脚注・入れ子を保つ。
 * 最初が見出しの場合は説明を空にし、見出しが無ければ全文を説明にする。
 * @param markdown 入力のMarkdown。
 * @param options アセット等の描画設定。
 * @returns サニタイズ済みの説明と補足のHTML。
 */
export function renderMarkupParts(markdown: string, options: RenderOptions): Readonly<{ lead: string; details: string }> {
  const processor = renderer(options);
  const tree = processor.runSync(processor.parse(markdown));
  const boundary = tree.children.findIndex((node) =>
    node.type === 'element' && /^h[1-6]$/u.test(node.tagName),
  );
  const split = boundary === -1 ? tree.children.length : boundary;
  return {
    lead: processor.stringify({ ...tree, children: tree.children.slice(0, split) }),
    details: processor.stringify({ ...tree, children: tree.children.slice(split) }),
  };
}

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
  return String(renderer(options).processSync(markdown));
}
