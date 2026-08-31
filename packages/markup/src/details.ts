import type { Paragraph, Root } from 'mdast';
import type { ContainerDirective, Directives } from 'mdast-util-directive';
import type { Node, Parent } from 'unist';
import { CONTINUE, SKIP, visit } from 'unist-util-visit';

/**
 * 折りたたみとして通す唯一のディレクティブ名。
 *
 * 生HTMLをパースしない方針（DECISIONS 24）のもとで折りたたみを表すため、`<details>` の直書きではなく
 * ディレクティブ記法を入力に用いる。HTML を組み立てるのは描画側であり、入力に属性が混ざらない。
 */
const DETAILS = 'details';

/** remark-directive が作るノードの型（`:::name` / `::name` / `:name`） */
const DIRECTIVE_TYPES: ReadonlySet<string> = new Set([
  'containerDirective',
  'leafDirective',
  'textDirective',
]);

/** 走査を続けるか、取り除いた位置から続けるかの指示 */
type VisitResult = typeof CONTINUE | [typeof SKIP, number];

/**
 * ディレクティブを `<details>` へ変換し、それ以外のディレクティブは出力から落とす。
 *
 * 許可したものだけを通す（allow-list）。未知のディレクティブを段落やテキストとして出すと、書き間違いが
 * そのまま公開ページへ出てしまう。
 *
 * @returns remark のトランスフォーマ
 */
export function remarkDetailsDirective(): (tree: Root) => undefined {
  return (tree: Root): undefined => {
    visit(tree, isDirective, (node, index, parent) => {
      return isDetailsContainer(node)
        ? markAsDetails(node)
        : dropFromParent(index, parent);
    });
  };
}

function isDirective(node: Node): node is Directives {
  return DIRECTIVE_TYPES.has(node.type);
}

function isDetailsContainer(node: Directives): node is ContainerDirective {
  return node.type === 'containerDirective' && node.name === DETAILS;
}

/**
 * `<details>` として出力するよう印を付け、ラベル段落を `<summary>` にする。
 *
 * @param node
 *            details コンテナディレクティブ
 * @returns 子孫の走査を続ける指示
 */
function markAsDetails(node: ContainerDirective): VisitResult {
  node.data = { ...node.data, hName: DETAILS };
  labelOf(node).forEach(markAsSummary);
  return CONTINUE;
}

/**
 * ラベル（`:::details[ここ]`）に当たる段落を返す。
 *
 * remark-directive はラベルを先頭の段落として置き、`data.directiveLabel` で印を付ける。印のない段落は
 * 本文なので対象にしない。
 *
 * @param node
 *            details コンテナディレクティブ
 * @returns ラベル段落（無ければ空）
 */
function labelOf(node: ContainerDirective): readonly Paragraph[] {
  return node.children
    .filter(isParagraph)
    .filter((child) => child.data?.directiveLabel === true)
    .slice(0, 1);
}

function isParagraph(node: Node): node is Paragraph {
  return node.type === 'paragraph';
}

function markAsSummary(label: Paragraph): void {
  label.data = { ...label.data, hName: 'summary', directiveLabel: undefined };
}

/**
 * 親から自身を取り除く。
 *
 * @param index
 *            親の children における位置
 * @param parent
 *            親ノード
 * @returns 取り除いた位置から走査を続ける指示。位置が取れない場合はそのまま続ける
 */
function dropFromParent(
  index: number | undefined,
  parent: Parent | undefined,
): VisitResult {
  return [{ index, parent }]
    .filter(hasPosition)
    .map(({ index: at, parent: from }): VisitResult => {
      from.children.splice(at, 1);
      return [SKIP, at];
    })
    .at(0) ?? CONTINUE;
}

function hasPosition(candidate: {
  index: number | undefined;
  parent: Parent | undefined;
}): candidate is { index: number; parent: Parent } {
  return typeof candidate.index === 'number' && candidate.parent !== undefined;
}
