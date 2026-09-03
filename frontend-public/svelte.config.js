/**
 * Svelte の書き方を runes に固定する。
 *
 * <p>
 * 既定では、ファイルの中で runes を使っているかどうかで legacy と runes のどちらのモードになるかが
 * ファイルごとに決まる。混在すると `let` が状態なのかただの変数なのかがファイル単位で変わり、読み手が
 * 毎回モードを判別することになる。`runes: true` で全ファイルを runes 側へ寄せ、legacy の記法
 * （`export let` / `$:` / `$$props`）をコンパイルエラーにする。
 * </p>
 *
 * <p>
 * この設定は Astro（@astrojs/svelte 経由の vite-plugin-svelte）と eslint-plugin-svelte の両方が読む。
 * TypeScript は Svelte のコンパイラが直接扱うため、前処理は置かない。
 * </p>
 */
export default {
  compilerOptions: {
    runes: true,
  },
};
