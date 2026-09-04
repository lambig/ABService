import { typescriptWorkspace } from 'abservice-eslint-config';
import tseslint from 'typescript-eslint';

/**
 * ルールの正は `packages/eslint-config`。ここが持つのは、シナリオ固有の緩和だけ。
 *
 * `e2e/` はフロントを検証する側でどちらのアプリにも属さないが、TypeScript で書く以上、規約は同じ
 * ものに従う（CONTRIBUTION.md のディレクトリ構造を参照）。
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

  ...typescriptWorkspace({ tsconfigRootDir: import.meta.dirname }),

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
