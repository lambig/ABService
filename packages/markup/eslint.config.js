import { publicApiJsdoc, typescriptWorkspace } from 'abservice-eslint-config';
import tseslint from 'typescript-eslint';

/**
 * ルールの正は `packages/eslint-config`。ここが持つのは、このパッケージ固有の緩和だけ。
 *
 * 言語差でそのまま移せないものは #232 に記録している。共有側に入れているのは「対応物が明確なもの」と
 * 「実測して通ったもの」だけで、通らなかったものは外した理由を issue へ残す。
 */
export default tseslint.config(
  {
    // 設定ファイル自身は tsconfig の include 外のため型情報を使う検査にかけられない
    ignores: ['node_modules/**', 'eslint.config.js'],
  },

  ...typescriptWorkspace({ tsconfigRootDir: import.meta.dirname }),

  publicApiJsdoc({ files: ['src/index.ts'] }),

  {
    /* テストは assert のために値を組み立てる。可変更新の禁止は本体コードに対して効かせる */
    files: ['**/*.test.ts'],
    rules: {
      'functional/immutable-data': 'off',
    },
  },
  {
    /*
     * AST を書き換えるプラグインでは可変更新を許す。
     *
     * remark / rehype の visitor は「木を辿って書き換える」ことが設計そのもので、`node.data` への
     * 代入や `children.splice` はその API の使い方である。ここを不変更新で書くには木を再構築する
     * 別の仕組みが必要になり、上流の設計から離れる。
     *
     * 対象は AST 変換プラグインのファイルに限る。描画の入口（index.ts）とテストには効かせたまま。
     */
    files: ['src/details.ts', 'src/asset-image.ts'],
    rules: {
      'functional/immutable-data': 'off',
    },
  },
);
