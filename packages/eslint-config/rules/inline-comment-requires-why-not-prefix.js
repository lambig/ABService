/**
 * 行コメント（`//`）を「why not」に限る独自ルール。
 *
 * <p>
 * バックエンドの Checkstyle `InlineCommentRequiresWhyNotPrefix` に対応する（`CODING_GUIDELINES` §8）。
 * 大文字とハイフンからなる語＋コロンのプレフィックスを要求し、それが付かない行コメントを塞ぐ。
 * </p>
 *
 * <p>
 * 上流のプラグインを使わず独自に書いている。コメントの内容を検査するルールは既製のものが無く、
 * eslint の `SourceCode#getAllComments` で読むのが唯一の経路である。
 * </p>
 *
 * <p>
 * Checkstyle 側は行の正規表現で判定するため、文字列中の `://` を除く細工が要る。こちらはコメントの
 * トークンだけを見るため、その細工が不要になる（URL は文字列であってコメントではない）。
 * </p>
 *
 * <p>
 * ブロックコメント（`/* ... *&#47;`）は対象にしない。バックエンドの規則も行コメントだけを縛っており、
 * 複数行の説明はプレフィックス付きのブロックコメントで書く、という使い分けをそのまま写している。
 * </p>
 */

/** 許容するプレフィックス。大文字とハイフンからなる語＋コロン（例: `HACK:` `PARSER-ORDER:`） */
const WHY_NOT_PREFIX = /^\s?[A-Z][A-Z-]*:/;

/**
 * eslint が生成する `///` の指示行（TypeScript のトリプルスラッシュ指令）は対象外にする。
 * 内容がコメントではなくコンパイラへの指示であるため。
 */
const TRIPLE_SLASH = /^\//;

export const inlineCommentRequiresWhyNotPrefix = {
  meta: {
    type: 'problem',
    docs: {
      description:
        '行コメントを、大文字プレフィックス＋コロンを伴う「why not」（この箇所固有の例外的な判断の理由）に限る',
    },
    schema: [],
    messages: {
      missingPrefix:
        '行コメント（// ...）は大文字+ハイフンのプレフィックス＋コロンを伴う「why not」（この箇所固有の例外的な実装判断の理由）のみ許可されます（例: // HACK: 理由）。一般的な理由（why）はコメントではなく命名か JSDoc、あるいは規約が定義されている場所に書いてください（CODING_GUIDELINES §8）。',
    },
  },

  create: (context) => ({
    Program: () => {
      context.sourceCode
        .getAllComments()
        .filter((comment) => comment.type === 'Line')
        .filter((comment) => TRIPLE_SLASH.test(comment.value) === false)
        .filter((comment) => WHY_NOT_PREFIX.test(comment.value) === false)
        .forEach((comment) => {
          /*
           * REPORT-LOC: コメントは AST のノードではないため、node ではなく loc で位置を渡す。
           */
          context.report({ loc: comment.loc, messageId: 'missingPrefix' });
        });
    },
  }),
};
