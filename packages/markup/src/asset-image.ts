import type { Element, Root } from 'hast';
import type { Node, Parent } from 'unist';
import { CONTINUE, SKIP, visit } from 'unist-util-visit';

/** 走査を続けるか、取り除いた位置から続けるかの指示 */
type VisitResult = typeof CONTINUE | [typeof SKIP, number];

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
  const allowedPrefix = validatedPrefix(assetBasePath);
  return (tree: Root): undefined => {
    visit(tree, isImage, (node, index, parent) => {
      return isAllowedSource(node, allowedPrefix)
        ? CONTINUE
        : dropFromParent(index, parent);
    });
  };
}

/** URL 解決の基準だけに使う。実際のサイト origin や通信には依存しない。 */
const VALIDATION_ORIGIN = 'https://asset-validation.invalid';

/** 不正な設定で許可範囲を拡張しない。ルート全体もアセット配下とは扱わない。 */
function validatedPrefix(basePath: string): string | undefined {
  const url = parseRootRelative(basePath);
  return url !== undefined && url.pathname === basePath && basePath !== '/'
    ? basePath.endsWith('/')
      ? basePath
      : `${basePath}/`
    : undefined;
}

/** ブラウザと同じ WHATWG URL で解決し、同一 origin の root-relative path のみ扱う。 */
function parseRootRelative(value: string): URL | undefined {
  try {
    const url = new URL(value, VALIDATION_ORIGIN);
    return value.startsWith('/') &&
      value[1] !== '/' &&
      url.origin === VALIDATION_ORIGIN
      ? url
      : undefined;
  } catch {
    return undefined;
  }
}

function isImage(node: Node): node is Element {
  return node.type === 'element' && (node as Element).tagName === 'img';
}

/**
 * `src` が配信ベースパス配下かを判定する。
 *
 * dot segment・backslash 等で pathname が正規化される曖昧な入力は拒否する。
 * Markdown が backslash を %5C に符号化する経路も同じ扱いにする。
 * 判定と出力の意味を揃えるため、書き換えて救済せず画像ごと除去する。
 *
 * 値が無い画像も許可しない（属性を落とすのではなく要素ごと落とすため、`src` なしの `img` は残さない）。
 *
 * @param node
 *            img 要素
 * @param allowedPrefix
 *            検証済みで末尾スラッシュを揃えた配信ベースパス。不正設定時は undefined
 * @returns 配下であれば true
 */
function isAllowedSource(
  node: Element,
  allowedPrefix: string | undefined,
): boolean {
  return [node.properties['src']]
    .filter((src): src is string => typeof src === 'string')
    .some((src) => {
      const url = parseRootRelative(src);
      return (
        allowedPrefix !== undefined &&
        url !== undefined &&
        url.pathname.startsWith(allowedPrefix) &&
        url.pathname.match(/%5c/iu) === null &&
        url.pathname === src.split(/[?#]/u)[0]
      );
    });
}

function dropFromParent(
  index: number | undefined,
  parent: Parent | undefined,
): VisitResult {
  return (
    [{ index, parent }]
      .filter(hasPosition)
      .map(({ index: at, parent: from }): VisitResult => {
        from.children.splice(at, 1);
        return [SKIP, at];
      })
      .at(0) ?? CONTINUE
  );
}

function hasPosition(candidate: {
  index: number | undefined;
  parent: Parent | undefined;
}): candidate is { index: number; parent: Parent } {
  return typeof candidate.index === 'number' && candidate.parent !== undefined;
}
