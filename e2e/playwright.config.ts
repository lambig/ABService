import { readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

import { defineConfig, devices } from '@playwright/test';

import { stack } from './src/support/config.ts';

/**
 * 実スタック（実 backend + 実 PostgreSQL + MinIO）に対して動かす（#164）。API のモックはしない。
 *
 * シナリオは2つの組（project）に分ける（#428）。境界は「共有している状態を書き換えるか」。
 *
 * - `public`: 組み上がった静的成果物と、管理 API の読み取り（品番・タイトルから ID を引く）だけを使う。
 *   互いに干渉しないため並列に走らせる。証跡の画像は番号付きの名で撮るため、生成順にも依らない。
 * - `admin`: scratch の作品・記事を作って接頭辞で一括に消す、シード済みの作品の公開状態を変える、
 *   サイト文言を書き換える、セッションを失効させる、応答を遅らせる。同時に走ると片方の片付けが
 *   もう片方の scratch を消し、公開状態の前提が崩れるため、1 worker で直列に走らせる。
 *
 * worker 数は組ごとに持てない（Playwright の `workers` は全体の値）ため、`scripts/run-e2e.mjs` が組ごとに
 * `--workers` を渡して順に走らせる。ここでの既定は 1 にしておき、`playwright test` を素で叩いたときも
 * 直列で安全に通る側へ倒す。
 *
 * 動画と trace は `admin` では常に録る（レビューの証跡。#164）。`public` は読むだけの画面なので、失敗した
 * ときだけ残し、アーティファクトを軽くする。
 */

/** 読むだけの組。静的成果物と読み取り API しか使わない */
const READ_ONLY_SPECS = [
  'albums',
  'articles',
  'color-scheme',
  'font-loading',
  'footer',
  'home',
  'markup-parity',
  'navigation',
  'page-kind',
  'responsive',
  'security-headers',
  'site-content-output',
  'site-marks',
  'smoke',
  'static-404',
] as const;

/** 共有している状態を書き換える組。直列で走らせる */
const STATEFUL_SPECS = [
  'admin-album-external-audios',
  'admin-album-form',
  'admin-album-pagination',
  'admin-album-recovery',
  'admin-album-results',
  'admin-album-sections',
  'admin-album-tracks',
  'admin-albums',
  'admin-article-form',
  'admin-articles',
  'admin-editor-recovery',
  'admin-external-audio-preview',
  'admin-markdown-assistance',
  'admin-sessions',
  'admin-site-contents',
  'article-layout-preview',
] as const;

/*
 * CLASSIFY-EVERY-SPEC: どちらの組にも入っていないファイルは走らない。増やしたシナリオが黙って検査から
 * 外れるのを防ぐため、`src/specs/` の実体と上の一覧を突き合わせ、ずれていれば設定の読み込みで止める。
 * 並列で安全かどうかの判断を、追加した人に必ず一度させる。
 */
const specsDir = fileURLToPath(new URL('./src/specs/', import.meta.url));
const presentSpecs = readdirSync(specsDir)
  .filter((name) => name.endsWith('.spec.ts'))
  .map((name) => name.replace(/\.spec\.ts$/u, ''));
const classifiedSpecs: readonly string[] = [...READ_ONLY_SPECS, ...STATEFUL_SPECS];
const isAbsentFrom = (list: readonly string[]) => (name: string) =>
  list.every((known) => known !== name);
const unclassified = presentSpecs.filter(isAbsentFrom(classifiedSpecs));
const missing = classifiedSpecs.filter(isAbsentFrom(presentSpecs));
const duplicated = READ_ONLY_SPECS.filter((name) =>
  (STATEFUL_SPECS as readonly string[]).includes(name),
);
const problems = [
  unclassified.length === 0
    ? null
    : `どの組にも入っていない spec があります（public か admin に加えてください）: ${unclassified.join(', ')}`,
  missing.length === 0
    ? null
    : `組に書かれているが存在しない spec があります: ${missing.join(', ')}`,
  duplicated.length === 0 ? null : `両方の組に入っている spec があります: ${duplicated.join(', ')}`,
].filter((problem) => problem !== null);
/** 組の一覧を、分類に穴が無いことを確かめてから使う。穴があれば設定の読み込みで止める */
const classified = (names: readonly string[]): readonly string[] =>
  problems.length === 0
    ? names
    : (() => {
        throw new Error(problems.join('\n'));
      })();

const specsMatching = (names: readonly string[]): RegExp =>
  new RegExp(`/(${classified(names).join('|')})\\.spec\\.ts$`, 'u');

export default defineConfig({
  testDir: './src/specs',
  outputDir: './test-results',

  /*
   * 全体の既定は直列。組ごとの並列度は `scripts/run-e2e.mjs` が `--workers` で渡す。`public` の組だけが
   * ファイルの中も含めて並列（`fullyParallel`）になる。
   */
  fullyParallel: false,
  workers: 1,

  forbidOnly: process.env['CI'] === 'true',
  retries: 0,

  /*
   * 組を順に走らせるため、HTML レポートの置き場は組ごとに分ける（同じ場所だと後の組が前の組を消す）。
   * 置き場は `scripts/run-e2e.mjs` が環境変数で渡す。素で叩いたときは既定の1か所へ出る。
   */
  reporter: [
    ['list'],
    [
      'html',
      { outputFolder: process.env['E2E_HTML_REPORT_DIR'] ?? 'playwright-report', open: 'never' },
    ],
  ],

  use: {
    baseURL: stack.siteBaseUrl,
    viewport: { width: 1280, height: 800 },
    screenshot: 'only-on-failure',
    locale: 'ja-JP',
    timezoneId: 'Asia/Tokyo',
  },

  projects: [
    {
      name: 'public',
      testMatch: specsMatching(READ_ONLY_SPECS),
      fullyParallel: true,
      outputDir: './test-results/public',
      use: { ...devices['Desktop Chrome'], trace: 'retain-on-failure', video: 'retain-on-failure' },
    },
    {
      name: 'admin',
      testMatch: specsMatching(STATEFUL_SPECS),
      fullyParallel: false,
      outputDir: './test-results/admin',
      use: { ...devices['Desktop Chrome'], trace: 'on', video: 'on' },
    },
  ],

  /*
   * どちらのアプリも静的出力のため、開発サーバではなくビルド済みの成果物を配信して見る。組み立てと
   * データ投入は scripts/prepare-stack.mjs が済ませている（順序に意味があるため1つに置く）。
   *
   * 既存のサーバを再利用しない。配信しているのは静的な成果物のため、再利用すると前の実行で組んだ
   * 古い画面を見続けることになる（変更が反映されないまま緑になる）。組み直しは1秒未満で済む。
   * 組を順に走らせるときも、組ごとに上げ直す。
   */
  webServer: [
    {
      command: 'node scripts/serve-app.mjs public',
      url: stack.siteBaseUrl,
      reuseExistingServer: false,
      timeout: 180_000,
    },
    {
      command: 'node scripts/serve-app.mjs admin',
      url: stack.adminBaseUrl,
      reuseExistingServer: false,
      timeout: 180_000,
    },
  ],
});
