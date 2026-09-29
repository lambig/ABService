import type { Element, ElementContent, Root } from 'hast';
import type { Node } from 'unist';
import { CONTINUE, SKIP, visit } from 'unist-util-visit';

/**
 * リンクを外し、中の文字だけを残す。
 *
 * 通信の無い端末では、リンクを開くと試聴の画面から離れ、戻る経路が無い。
 * 文字は作品の説明の一部なので消さず、`a` 要素だけを子要素で置き換える。
 *
 * @returns rehype のトランスフォーマ
 */
export function rehypeUnwrapLinks(): (tree: Root) => undefined {
  return (tree: Root): undefined => {
    visit(tree, isLink, (node, index, parent) =>
      typeof index === 'number' && parent !== undefined
        ? replace(parent.children, index, node.children)
        : CONTINUE,
    );
  };
}

function isLink(node: Node): node is Element {
  return node.type === 'element' && (node as Element).tagName === 'a';
}

/* The replaced children are visited next, so a link nested in the text is unwrapped too. */
function replace(
  siblings: (ElementContent | Root['children'][number])[],
  index: number,
  children: readonly ElementContent[],
): [typeof SKIP, number] {
  /* eslint-disable-next-line functional/immutable-data -- rehype transformers rewrite the tree in place. */
  siblings.splice(index, 1, ...children);
  return [SKIP, index];
}
