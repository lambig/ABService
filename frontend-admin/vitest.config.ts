import { getViteConfig } from 'astro/config';
import type { ViteUserConfig } from 'vitest/config';

/**
 * テストの設定。
 *
 * <p>
 * 環境はテストの置き場で分ける。`src/lib` は値と関数だけの層で DOM を要さず、`Response` や `fetch` を
 * そのまま使う（`http.test.ts`）。jsdom はこれらを持たないため、lib まで jsdom へ寄せると通信の検査が
 * 環境の差で落ちる。
 * </p>
 */
const TEST: ViteUserConfig['test'] = {
  projects: [
    {
      extends: true,
      test: {
        name: 'lib',
        environment: 'node',
        include: ['src/lib/**/*.test.ts'],
      },
    },
    {
      extends: true,

      /*
       * BROWSER-CONDITION: Svelte をブラウザ向けの実装で解決する。既定では Astro の設定が持つサーバ側の
       * 条件が効き、`mount` を持たない実装（`svelte/src/index-server.js`）が読まれて描画ができない。
       */
      resolve: { conditions: ['browser'] },

      test: {
        name: 'components',
        environment: 'jsdom',
        include: ['src/components/**/*.test.ts'],
        setupFiles: ['./vitest.setup.ts'],
      },
    },
  ],
};

/*
 * Astro の設定（`astro.config.mjs`）から Vite の設定を組む。@astrojs/svelte が入れる Svelte の
 * プラグインと `astro:env/client` の解決がこの経路で効くため、Vite の設定を別に書き起こさない。
 * 書き起こすと、アプリが動く設定とテストが動く設定の2つを揃え続けることになる。
 */
export default getViteConfig({ test: TEST } as Parameters<typeof getViteConfig>[0]);
