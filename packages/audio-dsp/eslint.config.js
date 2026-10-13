import { publicApiJsdoc, typescriptWorkspace } from 'abservice-eslint-config';
import js from '@eslint/js';
import tseslint from 'typescript-eslint';

/**
 * ルールの正は `packages/eslint-config`。ここが持つのは、このパッケージ固有の緩和だけ。
 */
export default tseslint.config(
  {
    // 設定ファイル自身は tsconfig の include 外のため型情報を使う検査にかけられない
    ignores: ['node_modules/**', 'eslint.config.js'],
  },

  ...typescriptWorkspace({ tsconfigRootDir: import.meta.dirname }).map((config) => ({
    ...config,
    files: ['src/**/*.ts'],
  })),

  // 独立したNode/browser比較用JSは製品のTS projectに含めず、通常のJS検査を適用する。
  {
    ...js.configs.recommended,
    files: ['experiments/music-analysis/*.mjs'],
    languageOptions: {
      globals: {
        console: 'readonly',
        process: 'readonly',
        Buffer: 'readonly',
        URL: 'readonly',
        fetch: 'readonly',
        document: 'readonly',
        window: 'readonly',
        performance: 'readonly',
        requestAnimationFrame: 'readonly',
        setTimeout: 'readonly',
        crypto: 'readonly',
        Option: 'readonly',
        innerWidth: 'readonly',
        WebAssembly: 'readonly',
      },
    },
  },

  publicApiJsdoc({ files: ['src/index.ts'] }),
);
