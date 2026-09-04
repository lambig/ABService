import comments from '@eslint-community/eslint-plugin-eslint-comments';
import js from '@eslint/js';
import functional from 'eslint-plugin-functional';
import tseslint from 'typescript-eslint';

/**
 * `docs/CODING_GUIDELINES.md` §1 の規約を TypeScript 側へ写したルール。
 *
 * このパッケージが持つのは **TypeScript で書かれたコードの規約だけ**である。バックエンド（Java）の
 * 規約の正は `backend/config/checkstyle` と `backend/config/pmd` で、両者は同じ §1 を別々の機構で
 * 表している。片方を変えたらもう片方も見る。
 *
 * 参照するのは `packages/markup`・`frontend-public`・`frontend-admin`・`e2e` の4つ。パーサの結線
 * （Astro / Svelte のファイルをどう読むか）と、ファイル単位の緩和は各ワークスペースが持つ。ここが持つのは
 * 「どのルールを、なぜ効かせるか」に限る。
 */

/** 深い相対パスの禁止（バックエンドの FQN 禁止に対応する趣旨）。エイリアスを使う */
export const DEEP_RELATIVE_IMPORT = '../../*';

/**
 * ルールを外すコメントに理由を必須とするプラグイン。
 *
 * 登録名を短くしない。`@eslint-community/eslint-comments` のまま使うことで、eslint の出力に出る
 * ルール名が上流の文書と一致する。
 */
export const commentsPluginName = '@eslint-community/eslint-comments';

/** {@link commentsPluginName} で登録するプラグイン本体 */
export const commentsPlugin = comments;

/**
 * 構文そのものを塞ぐルール（バックエンドの PMD ForbiddenIfStatement 等に対応）。
 *
 * `no-restricted-syntax` は配列を丸ごと上書きするため、これに足したいワークスペースは
 * スプレッドで展開してから自分の項目を並べる。
 */
export const restrictedSyntax = [
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
  {
    selector: 'SwitchStatement',
    message:
      'switch 文は使いません。abservice-patterns の patterns(...).when(...).orElse(exhaustive) で書いてください（網羅性はコンパイル時に検査されます）。',
  },
];

/** バックエンド規約に対応する、この設定の中核ルール */
export const conventionRules = {
  // 全ローカルを const にする（バックエンドの「全ローカル final」に対応）
  'prefer-const': 'error',
  'no-var': 'error',
  'functional/no-let': 'error',

  // TypeScript の strict の穴を塞ぐ。any と non-null assertion は型検査を無効化する
  '@typescript-eslint/no-explicit-any': 'error',
  '@typescript-eslint/no-non-null-assertion': 'error',

  // 破壊的な更新の禁止（バックエンドの「可変コレクション直接生成禁止」に対応）
  'functional/immutable-data': 'error',

  'no-restricted-syntax': ['error', ...restrictedSyntax],

  'no-restricted-imports': ['error', { patterns: [DEEP_RELATIVE_IMPORT] }],

  /*
   * ルールを外すなら理由を書く（バックエンドの「@SuppressWarnings に理由必須」に対応）。
   *
   * 外した事実だけが残ると、後から読む人には「規約が間違っているのか、ここが例外なのか、直し忘れなのか」が
   * 区別できない。理由を同じ行に置くことで、外した判断そのものをレビューの対象にする。
   */
  [`${commentsPluginName}/require-description`]: ['error', { ignore: [] }],
};

/**
 * 型情報を使う検査の層。
 *
 * `projectService` は各ワークスペースの tsconfig を出所にするため、`tsconfigRootDir` は呼び出し側が
 * 自分の `import.meta.dirname` を渡す。ここで解決すると、このパッケージの位置を指してしまう。
 *
 * @param {object} options
 * @param {string} options.tsconfigRootDir 呼び出し側の `import.meta.dirname`
 * @param {readonly string[]} [options.files] 対象を絞る場合のパターン（既定は全ファイル）
 * @param {readonly string[]} [options.extraFileExtensions] `.svelte` など TS 以外の拡張子
 */
export const typeCheckedLayer = ({ tsconfigRootDir, files, extraFileExtensions }) => ({
  ...(files === undefined ? {} : { files: [...files] }),
  extends: [tseslint.configs.strictTypeChecked],
  languageOptions: {
    parserOptions: {
      projectService: true,
      tsconfigRootDir,
      ...(extraFileExtensions === undefined
        ? {}
        : { extraFileExtensions: [...extraFileExtensions] }),
    },
  },
});

/**
 * パーサの結線が要らないワークスペース（素の TypeScript）向けの一式。
 *
 * Astro / Svelte を含むワークスペースは、パーサの順序を自分で決める必要があるため、これではなく
 * {@link conventionRules} と {@link typeCheckedLayer} を組み合わせる。
 *
 * @param {object} options
 * @param {string} options.tsconfigRootDir 呼び出し側の `import.meta.dirname`
 */
export const typescriptWorkspace = ({ tsconfigRootDir }) =>
  tseslint.config(js.configs.recommended, typeCheckedLayer({ tsconfigRootDir }), {
    plugins: { functional, [commentsPluginName]: commentsPlugin },
    rules: conventionRules,
  });
