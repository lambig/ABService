import js from '@eslint/js';
import functional from 'eslint-plugin-functional';
import tseslint from 'typescript-eslint';

/**
 * バックエンドの規約（docs/CODING_GUIDELINES.md §1）をシナリオへ写した設定。
 *
 * 選定は packages/markup（#232）と frontend-public に揃える。差分は、Playwright のシナリオが
 * 手続きの並びとして読まれるべきものである点で、その扱いを下に書く。
 */
export default tseslint.config(
  {
    ignores: [
      'node_modules/**',
      'evidence/**',
      'test-results/**',
      'playwright-report/**',
      // 設定と補助スクリプトは tsconfig の include 外のため型情報を使う検査にかけられない
      'eslint.config.js',
      'scripts/**',
    ],
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
      'prefer-const': 'error',
      'no-var': 'error',
      'functional/no-let': 'error',

      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-non-null-assertion': 'error',

      'functional/immutable-data': 'error',

      'no-restricted-syntax': [
        'error',
        {
          selector: 'IfStatement',
          message:
            '値の生成は式（三項）で行ってください。if 文は使いません（CODING_GUIDELINES §2）。',
        },
        {
          selector: 'LogicalExpression[operator="||"]',
          message:
            '|| は使いません。既定値は ?? を、条件の合成は述語の合成で表してください（CODING_GUIDELINES §1）。',
        },
        {
          selector: 'UnaryExpression[operator="!"]',
          message:
            '否定 ! は使いません。述語側で肯定形を用意するか、Predicate.not 相当で合成してください。',
        },
      ],

      'no-restricted-imports': [
        'error',
        {
          patterns: ['../../*'],
        },
      ],
    },
  },

  {
    /*
     * シナリオは「操作の並び」として読まれる。期待値の組み立てで値を作り替えることがあるため、
     * 可変更新の禁止はここへ効かせない（本体のヘルパには効かせたまま）。
     */
    files: ['src/specs/**/*.ts'],
    rules: {
      'functional/immutable-data': 'off',
    },
  },

  {
    /*
     * 証跡の印はブラウザ側で本物の DOM を組み立てて置く。要素の属性を立てるのは DOM API の使い方
     * そのもので、不変更新へ置き換える余地がない（packages/markup の AST 変換と同じ扱い）。
     */
    files: ['src/support/evidence.ts'],
    rules: {
      'functional/immutable-data': 'off',
    },
  },
);
