import svelte from '@astrojs/svelte';
import tailwindcss from '@tailwindcss/vite';
import { defineConfig, envField } from 'astro/config';

/*
 * 管理画面も静的出力（#122）。配信は公開サイトと同じ S3 + CloudFront で、Node のサーバを置かない。
 *
 * 公開サイトとの違いは、データの取得がビルド時ではなく**ブラウザ**で起きること。管理画面が扱うのは
 * 下書きを含む編集中の状態で、組み立てた時点の内容を配るわけにいかない。したがって画面の中身は
 * Svelte のアイランドが実行時に管理APIから引く。
 */
export default defineConfig({
  integrations: [svelte()],
  output: 'static',

  /*
   * 管理画面が配信されるのは単一ドメインの `/admin*`（`infra/edge.tf` の `/admin*` の振り分け）。
   * base を宣言しないと、生成される資産の参照が `/_astro/...` になる。この綴りは `/admin*` に
   * 当たらないため、既定の振り分けで公開サイトのバケットへ流れて 404 になる。画面間のリンクも同じ。
   *
   * 振り分けは経路を書き換えない。したがって成果物はバケットの `admin/` 配下へ置く（#125）。
   *
   * E2E も `/admin` 配下で配る（`e2e/src/support/config.ts`）。ルートで配ると、プレフィックスの
   * ある状態を一度も検査しないまま緑になる。
   */
  base: '/admin',

  env: {
    schema: {
      /*
       * 管理APIの起点。ブラウザから叩くため client で宣言する（公開サイトの API_BASE_URL は
       * ビルド時にしか使わないため server）。値は公開されるが、URL はそれ自体が秘密ではない。
       * 鍵はここに置かない（静的な成果物へ焼き込むと、配信を受け取れる誰もが管理操作できる）。
       */
      PUBLIC_API_BASE_URL: envField.string({
        context: 'client',
        access: 'public',
        default: 'http://localhost:8080',
      }),

      /*
       * アセット（画像）の配信ベースパス。本文の描画が画像の src をこの配下に限る（DECISIONS 24）。
       * バックエンドの abservice.assets.public-base-path と CloudFront のビヘイビアに一致させる。
       *
       * **名前と値は公開サイトと共有する**（#289 から #122 へ引き継いだ受け入れ条件）。別々の変数に
       * すると、片方だけを変えたときに公開では出る画像がプレビューでは落ちる。プレビューが嘘に
       * ならないことは、同じ関数を呼ぶだけでは足りず、同じ設定値であることまでを要する。
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
