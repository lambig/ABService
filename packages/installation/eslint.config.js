import { publicApiJsdoc, typescriptWorkspace } from 'abservice-eslint-config';
import tseslint from 'typescript-eslint';

/**
 * ルールの正は `packages/eslint-config`。ここが持つのは、このパッケージ固有の緩和だけ。
 */
export default tseslint.config(
  {
    // 設定ファイル自身は tsconfig の include 外のため型情報を使う検査にかけられない
    ignores: ['node_modules/**', 'eslint.config.js'],
  },

  ...typescriptWorkspace({ tsconfigRootDir: import.meta.dirname }),

  publicApiJsdoc({ files: ['src/index.ts'] }),
);
