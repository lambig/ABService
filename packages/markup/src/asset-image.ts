import type { Element, Root } from 'hast';
import type { Node, Parent } from 'unist';
import { SKIP, visit } from 'unist-util-visit';

/**
 * 画像の `src` を配信ベースパス配下に限る。
 *
 * 外部URLを許すと CSP の `img-src` を広げ、リファラの送出と外部ホストの可用性を受け入れることになる
 * （DECISIONS 24）。`data:` も同様に落とす。
 *
 * @param assetBasePath
 *            アセットの配信ベースパス（バックエンドの `abservice.assets.public-base-path` と揃える）
 * @returns rehype のトランスフォーマ
 */
export function rehypeRestrictImageSource(
  assetBasePath: string,
): (tree: Root) => undefined {
  const allowedPrefix = withTrailingSlash(assetBasePath);
  return (tree: Root): undefined => {
    visit(tree, isImage, (node, index, parent) =>
      isAllowedSource(node, allowedPrefix)
        ? undefined
        : dropFromParent(index, parent));
  };
}

function withTrailingSlash(basePath: string): string {
  return basePath.endsWith('/') ? basePath : `${basePath}/`;
}

function isImage(node: Node): node is Element {
  return node.type === 'element' && (node as Element).tagName === 'img';
}

/**
 * `src` が配信ベースパス配下かを判定する。
 *
 * 値が無い画像も許可しない（属性を落とすのではなく要素ごと落とすため、`src` なしの `img` は残さない）。
 *
 * @param node
 *            img 要素
 * @param allowedPrefix
 *            末尾スラッシュを揃えた配信ベースパス
 * @returns 配下であれば true
 */
function isAllowedSource(node: Element, allowedPrefix: string): boolean {
  return [node.properties?.['src']]
    .filter((src): src is string => typeof src === 'string')
    .some((src) => src.startsWith(allowedPrefix));
}

function dropFromParent(
  index: number | undefined,
  parent: Parent | undefined,
): [typeof SKIP, number] | undefined {
  return [{ index, parent }]
    .filter(hasPosition)
    .map(({ index: at, parent: from }) => {
      from.children.splice(at, 1);
      return [SKIP, at] as [typeof SKIP, number];
    })
    .at(0);
}

function hasPosition(candidate: {
  index: number | undefined;
  parent: Parent | undefined;
}): candidate is { index: number; parent: Parent } {
  return typeof candidate.index === 'number' && candidate.parent !== undefined;
}
