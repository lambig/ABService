import js from '@eslint/js';
import astro from 'eslint-plugin-astro';
import functional from 'eslint-plugin-functional';
import svelte from 'eslint-plugin-svelte';
import globals from 'globals';
import tseslint from 'typescript-eslint';

import {
  DEEP_RELATIVE_IMPORT,
  conventionRules,
  restrictedSyntax,
  typeCheckedLayer,
} from './index.js';

/**
 * Astro + Svelte（runes）のアプリ向けの一式。`frontend-public` と `frontend-admin` が参照する。
 *
 * 素の TypeScript のワークスペース（`packages/markup`・`e2e`）はこちらを読まない。Astro / Svelte の
 * プラグインを要求しないよう、入口を分けている。
 */

/** Svelte 4 までの API。runes へ揃えるため、輸入の時点で塞ぐ */
const svelte4Apis = [
  {
    name: 'svelte',
    importNames: ['createEventDispatcher'],
    message:
      'Svelte 4 までの API です。親への通知はコールバックの props で表してください（README の Svelte 節）。',
  },
  {
    name: 'svelte',
    importNames: ['beforeUpdate'],
    message: 'Svelte 4 までの API です。更新前の処理は $effect.pre で表してください。',
  },
  {
    name: 'svelte',
    importNames: ['afterUpdate'],
    message: 'Svelte 4 までの API です。更新後の処理は $effect で表してください。',
  },
];

/**
 * `$state` 以外の `let`。
 *
 * `$state.raw` も許すため、呼び出し先が識別子の場合とメンバ参照の場合の両方を除く。
 */
const LET_WITHOUT_STATE = [
  'VariableDeclaration[kind="let"] > VariableDeclarator',
  '[init.callee.name!="$state"]',
  '[init.callee.object.name!="$state"]',
].join('');

/**
 * Svelte を runes に揃えるためのルール。
 *
 * svelte.config.js の runes 強制と対にする。あちらが塞ぐのはビルドの時点。ここはビルドを待たずに
 * 同じことを出すためと、runes モードでもコンパイルが通ってしまう Svelte 4 由来の書き方
 * （`<slot>`・`on:` のイベントディレクティブ）を塞ぐためにある。
 */
const runesRules = {
  // コンパイラの警告を lint の失敗にする（非推奨の記法はここに出る）
  'svelte/valid-compile': 'error',

  // $derived.by で書けるものは $derived で書く
  'svelte/prefer-derived-over-derived-by': 'error',

  /*
   * 状態の宣言は runes が `let` を要求する（`let count = $state(0)`）。「全ローカル const」の規約と
   * 正面から衝突するため、Svelte では const の強制を外し、代わりに `$state` 以外の `let` を塞ぐ。
   * 縛りたいのは「再代入できる変数を置かない」ことで、runes の状態はそれとは別の概念である。
   */
  'functional/no-let': 'off',
  'no-restricted-syntax': [
    'error',
    ...restrictedSyntax,
    {
      selector: LET_WITHOUT_STATE,
      message:
        'let は状態の宣言（$state）にだけ使います。値は const で受けてください（CODING_GUIDELINES §1）。',
    },
  ],
};

/**
 * @param {object} options
 * @param {string} options.tsconfigRootDir 呼び出し側の `import.meta.dirname`
 */
export const astroSvelteWorkspace = ({ tsconfigRootDir }) =>
  tseslint.config(
    js.configs.recommended,
    ...astro.configs.recommended,

    /*
     * 型情報を使う検査。tsconfig の include が `.svelte` も拾うため、コンポーネントにも同じ水準で効かせる。
     *
     * PARSER-ORDER: この設定は対象のファイルへ TypeScript のパーサを置く。`.svelte` を読めるのは
     * svelte のパーサだけのため、Svelte の設定より前に置いて上書きさせる。
     */
    typeCheckedLayer({
      tsconfigRootDir,
      files: ['**/*.ts', '**/*.svelte', '**/*.svelte.ts'],
      extraFileExtensions: ['.svelte'],
    }),

    ...svelte.configs.recommended,

    {
      files: ['**/*.ts', '**/*.astro', '**/*.svelte'],
      plugins: {
        '@typescript-eslint': tseslint.plugin,
        functional,
      },
      rules: {
        ...conventionRules,
        // 深い相対パスに加えて Svelte 4 の API も塞ぐ（$lib エイリアスを使う）
        'no-restricted-imports': [
          'error',
          { patterns: [DEEP_RELATIVE_IMPORT], paths: svelte4Apis },
        ],
      },
    },

    {
      files: ['**/*.svelte', '**/*.svelte.ts'],
      languageOptions: {
        /* コンポーネントはブラウザで動く。document や console を未定義と見なさない */
        globals: globals.browser,
        /* `<script lang="ts">` の中身は、Svelte のパーサから TypeScript のパーサへ渡さないと読めない */
        parserOptions: {
          parser: tseslint.parser,
        },
      },
      rules: runesRules,
    },
  );
