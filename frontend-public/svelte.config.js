import { fileURLToPath } from 'node:url';

/**
 * このリポジトリが持つコンポーネントの置き場。
 *
 * shadcn-svelte の生成物（`src/lib/components/ui`）もここに入る。上流の生成物ではあるが、
 * リポジトリが抱えるコードであることに変わりはないため、同じ規約の側に置く。
 */
const OWN_COMPONENTS = fileURLToPath(new URL('src/', import.meta.url));

/**
 * Svelte の書き方を runes に固定する。
 *
 * <p>
 * 既定では、ファイルの中で runes を使っているかどうかで legacy と runes のどちらのモードになるかが
 * ファイルごとに決まる。混在すると `let` が状態なのかただの変数なのかがファイル単位で変わり、読み手が
 * 毎回モードを判別することになる。runes を強制することで legacy の記法（`export let` / `$:` /
 * `$$props`）をコンパイルエラーにする。
 * </p>
 *
 * <p>
 * SCOPE-TO-OWN-CODE: 強制の範囲はこのリポジトリのコードに限る。`compilerOptions` へ直接置くと
 * `node_modules` の Svelte コンポーネントにも及び、依存が legacy 記法を含むだけでビルドできなくなる。
 * 依存の実装方式はこちらの規約とは関係がないため、ファイル単位で決める形にする。
 * </p>
 */
export default {
  vitePlugin: {
    dynamicCompileOptions: ({ filename }) =>
      filename.startsWith(OWN_COMPONENTS) ? { runes: true } : {},
  },
};
