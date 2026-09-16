import { publicApiJsdoc, typescriptWorkspace } from 'abservice-eslint-config';
import tseslint from 'typescript-eslint';

/**
 * ルールの正は `packages/eslint-config`。ここが持つのは、このパッケージ固有の緩和だけ。
 */
export default tseslint.config(
  {
    ignores: [
      'node_modules/**',
      // 設定と補助スクリプトは tsconfig の include 外のため型情報を使う検査にかけられない
      'eslint.config.js',
      'scripts/**',
    ],
  },

  ...typescriptWorkspace({ tsconfigRootDir: import.meta.dirname }),

  publicApiJsdoc({ files: ['src/index.ts'] }),

  {
    /* テストは expect のために値を組み立てる。可変更新の禁止は本体コードに対して効かせる */
    files: ['**/*.test.ts'],
    rules: {
      'functional/immutable-data': 'off',
    },
  },
);
