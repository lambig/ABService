import js from '@eslint/js';
import functional from 'eslint-plugin-functional';
import tseslint from 'typescript-eslint';

/**
 * バックエンドの規約（docs/CODING_GUIDELINES.md §1）をフロントへ写した設定。
 *
 * 言語差でそのまま移せないものは #232 に記録している。ここに入れているのは「対応物が明確なもの」と
 * 「実測して通ったもの」だけで、通らなかったものは外した理由を issue へ残す。
 */
export default tseslint.config(
  {
    // 設定ファイル自身は tsconfig の include 外のため型情報を使う検査にかけられない
    ignores: ['node_modules/**', 'eslint.config.js'],
  },
  js.configs.recommended,
  tseslint.configs.strictTypeChecked,
  {
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: {
      functional,
    },
    rules: {
      // 全ローカルを const にする（バックエンドの「全ローカル final」に対応）
      'prefer-const': 'error',
      'no-var': 'error',
      'functional/no-let': 'error',

      // TypeScript の strict の穴を塞ぐ。any と non-null assertion は型検査を無効化する
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-non-null-assertion': 'error',

      // 破壊的な更新の禁止（バックエンドの「可変コレクション直接生成禁止」に対応）。
      // AST を組み立てる remark/rehype プラグインは木を書き換えるため、該当ファイルで個別に緩める
      'functional/immutable-data': 'error',

      // if 文の禁止（バックエンドの PMD ForbiddenIfStatement に対応）。
      // 値の生成は式（三項）で行い、if は使わない
      'no-restricted-syntax': [
        'error',
        {
          selector: 'IfStatement',
          message: '値の生成は式（三項・switch 式相当）で行ってください。if 文は使いません（CODING_GUIDELINES §2）。',
        },
        {
          selector: 'LogicalExpression[operator="||"]',
          message: '|| は使いません。既定値は ?? を、条件の合成は述語の合成で表してください（CODING_GUIDELINES §1）。',
        },
        {
          selector: 'UnaryExpression[operator="!"]',
          message: '否定 ! は使いません。述語側で肯定形を用意するか、Predicate.not 相当で合成してください。',
        },
      ],

      // 深い相対パスの禁止（バックエンドの FQN 禁止に対応する趣旨）
      'no-restricted-imports': [
        'error',
        {
          patterns: ['../../*'],
        },
      ],
    },
  },
  {
    // テストは assert のために値を組み立てる。可変更新の禁止は本体コードに対して効かせる
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
