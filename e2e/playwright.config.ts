import { defineConfig, devices } from '@playwright/test';

import { stack } from './src/support/config.ts';

/**
 * 実スタック（実 backend + 実 PostgreSQL + MinIO）に対して動かす（#164）。API のモックはしない。
 *
 * 証跡を残すことも実行の目的に含めるため、trace と動画は常に録る（失敗時だけではない）。
 */
export default defineConfig({
  testDir: './src/specs',
  outputDir: './test-results',

  /*
   * 証跡は撮った順に読まれる。並行実行すると画像の生成順が実行ごとに変わるため、直列で走らせる。
   * シナリオ数が増えて実行時間が問題になったら、証跡を撮るシナリオだけを直列にする。
   */
  fullyParallel: false,
  workers: 1,

  forbidOnly: process.env['CI'] === 'true',
  retries: 0,

  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],

  use: {
    baseURL: stack.siteBaseUrl,
    viewport: { width: 1280, height: 800 },
    trace: 'on',
    video: 'on',
    screenshot: 'only-on-failure',
    locale: 'ja-JP',
    timezoneId: 'Asia/Tokyo',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  /*
   * 公開サイトは静的出力のため、開発サーバではなくビルド済みの成果物を配信して見る。バックエンドの
   * 起動待ちと組み立ても含めて scripts/serve-site.mjs が受け持つ（順序に意味があるため1つに置く）。
   */
  /*
   * 既存のサーバを再利用しない。配信しているのは静的な成果物のため、再利用すると前の実行で組んだ
   * 古い画面を見続けることになる（変更が反映されないまま緑になる）。組み直しは1秒未満で済む。
   */
  webServer: {
    command: 'node scripts/serve-site.mjs',
    url: stack.siteBaseUrl,
    reuseExistingServer: false,
    timeout: 180_000,
  },
});
