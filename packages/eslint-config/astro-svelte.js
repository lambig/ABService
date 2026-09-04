import js from '@eslint/js';
import astro from 'eslint-plugin-astro';
import functional from 'eslint-plugin-functional';
import svelte from 'eslint-plugin-svelte';
import globals from 'globals';
import tseslint from 'typescript-eslint';

import {
  DEEP_RELATIVE_IMPORT,
  DEEP_RELATIVE_IMPORT_MESSAGE,
  commentsPlugin,
  commentsPluginName,
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

/**
 * 層の依存の向き。
 *
 * <p>
 * `pages` → `layouts` → `components` → `lib` の順に内側へ向かう。内側は外側を輸入しない
 * （バックエンドの ArchUnit `domainShouldNotDependOnOuterLayers` と同じ趣旨）。`pages` は Astro が
 * 経路として読むもので、どこからも輸入されない。
 * </p>
 *
 * <p>
 * 層をまたぐ輸入はすべてエイリアス（`$lib` / `$components` / `$layouts`）を通るため、輸入の綴りで表せる。
 * `eslint-plugin-boundaries` のような位置から層を判定するプラグインは入れていない（#232）。相対パスで
 * 層をまたぐ抜け道は、1つ上（`../components/*` 等）を塞ぎ、2つ以上上は
 * {@link DEEP_RELATIVE_IMPORT} が既に塞いでいる。
 * </p>
 *
 * <p>
 * `frontend-public` は shadcn-svelte のコンポーネントを `src/lib/components/ui/**` に置いており、
 * `lib` の中に `components` がある。`$lib/components/...` は `$components/*` に当たらないため規則は
 * 正しく働くが、名前と中身が食い違っている。置き場の是正は #270 が持つ（#122 で管理画面が shadcn を
 * 入れる時点で両アプリまとめて移す）。
 * </p>
 */
const layerBoundaries = [
  {
    files: ['src/lib/**'],
    outer: ['$components/*', '$layouts/*', '../components/*', '../layouts/*', '../pages/*'],
    message:
      'lib は最も内側の層です。components / layouts / pages を輸入せず、値と関数だけを公開してください。',
  },
  {
    files: ['src/components/**'],
    outer: ['../pages/*', '../../pages/*'],
    message: 'components は pages を輸入しません。画面の組み立ては pages 側が行ってください。',
  },
  {
    files: ['src/layouts/**'],
    outer: ['../pages/*', '../../pages/*'],
    message: 'layouts は pages を輸入しません。画面の組み立ては pages 側が行ってください。',
  },
];

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
        [commentsPluginName]: commentsPlugin,
      },
      rules: {
        ...conventionRules,
        // 深い相対パスに加えて Svelte 4 の API も塞ぐ（$lib エイリアスを使う）
        'no-restricted-imports': [
          'error',
          {
            patterns: [{ group: [DEEP_RELATIVE_IMPORT], message: DEEP_RELATIVE_IMPORT_MESSAGE }],
            paths: svelte4Apis,
          },
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

    /*
     * 層ごとに、外側へ向かう輸入を塞ぐ。`no-restricted-imports` は設定を丸ごと上書きするため、
     * 深い相対パスと Svelte 4 の API もここで並べ直す。
     */
    ...layerBoundaries.map(({ files, outer, message }) => ({
      files,
      rules: {
        'no-restricted-imports': [
          'error',
          {
            patterns: [
              { group: [DEEP_RELATIVE_IMPORT], message: DEEP_RELATIVE_IMPORT_MESSAGE },
              { group: outer, message },
            ],
            paths: svelte4Apis,
          },
        ],
      },
    })),
  );
