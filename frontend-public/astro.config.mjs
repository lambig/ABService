import svelte from '@astrojs/svelte';
import tailwindcss from '@tailwindcss/vite';
import { defineConfig, envField } from 'astro/config';

/*
 * 公開サイトは静的出力（DECISIONS 24 / #125）。記事とアルバムはビルド時に取得して HTML へ焼き込み、
 * ブラウザからバックエンドを呼ばない。配信は S3 + CloudFront で、Node のサーバを置かない。
 */
export default defineConfig({
  integrations: [svelte()],
  output: 'static',

  /*
   * 正規URL・サイトマップ・OG の絶対URLの出所。実際の公開ドメインはリポジトリへ書かず、ビルド時に
   * 環境変数で渡す（#129）。既定値はローカル開発の URL。
   */
  site: process.env.PUBLIC_SITE_URL ?? 'http://localhost:4321',

  env: {
    schema: {
      /*
       * ビルド時に叩くバックエンドの起点。ブラウザへは出ないため server で宣言する（静的出力のため
       * 実行時のフェッチを持たない）。ローカルは docker compose のバックエンド。
       */
      API_BASE_URL: envField.string({
        context: 'server',
        access: 'public',
        default: 'http://localhost:8080',
      }),

      /*
       * アセット（画像）の配信ベースパス。本文の描画が画像の src をこの配下に限る（DECISIONS 24）。
       * バックエンドの abservice.assets.public-base-path と CloudFront のビヘイビアに一致させる。
       *
       * **名前と値は管理画面と共有する**（#289 から #122 へ引き継いだ受け入れ条件）。別々の変数に
       * すると、片方だけを変えたときに公開では出る画像がプレビューでは落ちる。プレビューが嘘に
       * ならないことは、同じ関数を呼ぶだけでは足りず、同じ設定値であることまでを要する。
       *
       * この用途はビルド時だけだが、管理画面はブラウザで描くため client でなければならず、Astro は
       * client の変数に `PUBLIC_` 接頭辞を要求する。名前を1つにするため、こちらも client で宣言する
       * （client の値はビルド時にも読める）。値は配信される画像の URL に元から現れるもので、秘密ではない。
       */
      PUBLIC_ASSET_BASE_PATH: envField.string({
        context: 'client',
        access: 'public',
        default: '/assets',
      }),
    },
  },

  vite: {
    plugins: [tailwindcss()],
  },
});
