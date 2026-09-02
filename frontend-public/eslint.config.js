import js from '@eslint/js';
import astro from 'eslint-plugin-astro';
import functional from 'eslint-plugin-functional';
import svelte from 'eslint-plugin-svelte';
import tseslint from 'typescript-eslint';

/**
 * バックエンドの規約（docs/CODING_GUIDELINES.md §1）をフロントへ写した設定。
 *
 * ルールの選定は packages/markup（#232）に揃える。差分は「Astro / Svelte のファイルが増えること」と
 * 「shadcn-svelte が持ち込むコンポーネントを対象外にすること」の2点で、いずれも下にその理由を書く。
 */

/** バックエンド規約に対応する、この設定の中核ルール */
const conventionRules = {
  // 全ローカルを const にする（バックエンドの「全ローカル final」に対応）
  'prefer-const': 'error',
  'no-var': 'error',
  'functional/no-let': 'error',

  // TypeScript の strict の穴を塞ぐ。any と non-null assertion は型検査を無効化する
  '@typescript-eslint/no-explicit-any': 'error',
  '@typescript-eslint/no-non-null-assertion': 'error',

  // 破壊的な更新の禁止（バックエンドの「可変コレクション直接生成禁止」に対応）
  'functional/immutable-data': 'error',

  // if 文・||・否定 ! の禁止（バックエンドの PMD ForbiddenIfStatement 等に対応）
  'no-restricted-syntax': [
    'error',
    {
      selector: 'IfStatement',
      message: '値の生成は式（三項）で行ってください。if 文は使いません（CODING_GUIDELINES §2）。',
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

  // 深い相対パスの禁止（バックエンドの FQN 禁止に対応する趣旨）。$lib エイリアスを使う
  'no-restricted-imports': [
    'error',
    {
      patterns: ['../../*'],
    },
  ],
};

export default tseslint.config(
  {
    ignores: [
      'node_modules/**',
      'dist/**',
      '.astro/**',
      // 設定ファイル自身は tsconfig の include 外のため型情報を使う検査にかけられない
      'eslint.config.js',
      'astro.config.mjs',
      /*
       * shadcn-svelte が置くコンポーネントは、このリポジトリが書いたコードではなく上流の生成物である。
       * 規約（if 禁止・否定禁止・immutable-data）はここへ効かせない。手を入れる場合も、上流の更新を
       * 取り込み直せる形に留める。デザインの差し替えはトークン（DECISIONS 25）で行い、この配下は触らない。
       */
      'src/lib/components/ui/**',
    ],
  },

  js.configs.recommended,
  ...astro.configs.recommended,
  ...svelte.configs.recommended,

  {
    // 型情報を使う検査は TypeScript のファイルに効かせる
    files: ['**/*.ts'],
    extends: [tseslint.configs.strictTypeChecked],
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
  },

  {
    files: ['**/*.ts', '**/*.astro', '**/*.svelte'],
    plugins: {
      '@typescript-eslint': tseslint.plugin,
      functional,
    },
    rules: conventionRules,
  },
);
