import { astroSvelteWorkspace } from 'abservice-eslint-config/astro-svelte';
import tseslint from 'typescript-eslint';

/**
 * ルールの正は `packages/eslint-config`。ここが持つのは、このアプリが「何を検査の対象から外すか」だけ。
 */
export default tseslint.config(
  {
    ignores: [
      'node_modules/**',
      'dist/**',
      '.astro/**',
      // 設定ファイル自身は tsconfig の include 外のため型情報を使う検査にかけられない
      'eslint.config.js',
      'astro.config.mjs',
      'svelte.config.js',
      /*
       * shadcn-svelte が置くコンポーネントは、このリポジトリが書いたコードではなく上流の生成物である。
       * 規約（if 禁止・否定禁止・immutable-data）はここへ効かせない。手を入れる場合も、上流の更新を
       * 取り込み直せる形に留める。デザインの差し替えはトークン（DECISIONS 25）で行い、この配下は触らない。
       */
      'src/components/ui/**',
      // OpenAPI から生成する型定義（openapi-typescript の出力）
      'src/lib/api/schema.d.ts',
    ],
  },

  ...astroSvelteWorkspace({ tsconfigRootDir: import.meta.dirname }),
);
