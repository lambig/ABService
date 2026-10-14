import { publicApiJsdoc, typescriptWorkspace } from "abservice-eslint-config";
import tseslint from "typescript-eslint";
import js from "@eslint/js";

/**
 * ルールの正は `packages/eslint-config`。ここが持つのは、このパッケージ固有の緩和だけ。
 */
export default tseslint.config(
  {
    // 設定ファイル自身は tsconfig の include 外のため型情報を使う検査にかけられない
    ignores: [
      "node_modules/**",
      "dist/**",
      "test-results/**",
      "playwright-report/**",
      "eslint.config.js",
    ],
  },

  ...typescriptWorkspace({ tsconfigRootDir: import.meta.dirname }).map(
    (config) => ({ ...config, files: ["**/*.ts"] }),
  ),
  {
    files: ["analysis/*.mjs"],
    ...js.configs.recommended,
    languageOptions: {
      globals: Object.fromEntries(
        [
          "URL",
          "FileReaderSync",
          "WebAssembly",
          "DOMException",
          "setTimeout",
          "navigator",
          "crypto",
          "fetch",
          "self",
          "AbortController",
          "TextDecoder",
          "btoa",
        ].map((name) => [name, "readonly"]),
      ),
    },
  },

  publicApiJsdoc({ files: ["src/index.ts"] }),
);
