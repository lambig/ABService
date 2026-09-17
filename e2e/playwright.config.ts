import { readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

import { defineConfig, devices } from '@playwright/test';

import { stack } from './src/support/config.ts';

/**
 * 実スタック（実 backend + 実 PostgreSQL + MinIO）に対して動かす（#164）。API のモックはしない。
 *
 * シナリオは3つの組（project）に分ける（#428, #430）。境界は「どの状態を、誰と共有しているか」。
 *
 * - `public`: 組み上がった静的成果物と、管理 API の読み取り（品番・タイトルから ID を引く）だけを使う。
 *   互いに干渉しないため、ファイルの中も含めて並列に走らせる。証跡の画像は番号付きの名で撮るため、
 *   生成順にも依らない。
 * - `admin-parallel`: 触るのは自分の worker が作った scratch（作品・記事・サイト文言のキー）だけで、
 *   シード済みのデータは読むだけ。scratch の名と片付けは worker ごとに分かれている
 *   （`src/support/worker.ts`）ため、複数 worker で走らせても互いの状態に触れない。ファイル単位で
 *   worker に配り、ファイルの中は直列にする。
 * - `admin-serial`: 一覧の件数やページ割りそのものを前提にする。51 件を投入して 1 ページ目を押し出す、
 *   記事の総数が 51 以上であることを見る、シード済みの行が 1 ページ目にあることを見る——他の worker が
 *   同時に scratch を作ると、これらの前提は崩れる。1 worker で、他の組と重ねずに走らせる。
 *
 * worker 数は組ごとに持てない（Playwright の `workers` は全体の値）ため、`scripts/run-e2e.mjs` が組ごとに
 * `--workers` を渡して順に走らせる。ここでの既定は 1 にしておき、`playwright test` を素で叩いたときも
 * 直列で安全に通る側へ倒す。
 *
 * 動画と trace は管理画面の組では常に録る（レビューの証跡。#164）。`public` は読むだけの画面なので、
 * 失敗したときだけ残し、アーティファクトを軽くする。
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

/** 自分の worker の scratch だけを書き換える組。ファイル単位で並列に走らせる */
const WORKER_OWNED_SPECS = [
  'admin-album-external-audios',
  'admin-album-form',
  'admin-album-recovery',
  'admin-album-results',
  'admin-album-sections',
  'admin-album-tracks',
  'admin-article-form',
  'admin-editor-recovery',
  'admin-external-audio-preview',
  'admin-markdown-assistance',
  'admin-sessions',
  'admin-site-contents',
  'article-layout-preview',
] as const;

/** 一覧の件数・ページ割り・シード済みの行の位置を前提にする組。1 worker で他と重ねずに走らせる */
const SHARED_LISTING_SPECS = ['admin-album-pagination', 'admin-albums', 'admin-articles'] as const;

/*
 * CLASSIFY-EVERY-SPEC: どの組にも入っていないファイルは走らない。増やしたシナリオが黙って検査から
 * 外れるのを防ぐため、`src/specs/` の実体と上の一覧を突き合わせ、ずれていれば設定の読み込みで止める。
 * どの組に入れるか（並列で安全か、一覧の前提を持つか）の判断を、追加した人に必ず一度させる。
 */
const GROUPS: readonly (readonly string[])[] = [
  READ_ONLY_SPECS,
  WORKER_OWNED_SPECS,
  SHARED_LISTING_SPECS,
];
const specsDir = fileURLToPath(new URL('./src/specs/', import.meta.url));
const presentSpecs = readdirSync(specsDir)
  .filter((name) => name.endsWith('.spec.ts'))
  .map((name) => name.replace(/\.spec\.ts$/u, ''));
const classifiedSpecs: readonly string[] = GROUPS.flat();
const isAbsentFrom = (list: readonly string[]) => (name: string) =>
  list.every((known) => known !== name);
const unclassified = presentSpecs.filter(isAbsentFrom(classifiedSpecs));
const missing = classifiedSpecs.filter(isAbsentFrom(presentSpecs));
const duplicated = classifiedSpecs.filter((name, index) => classifiedSpecs.indexOf(name) !== index);
const problems = [
  unclassified.length === 0
    ? null
    : `どの組にも入っていない spec があります（public / admin-parallel / admin-serial のどれかに加えてください）: ${unclassified.join(', ')}`,
  missing.length === 0
    ? null
    : `組に書かれているが存在しない spec があります: ${missing.join(', ')}`,
  duplicated.length === 0 ? null : `複数の組に入っている spec があります: ${duplicated.join(', ')}`,
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

/** 管理画面の組に共通の設定。証跡のため trace と動画を常に録る */
const adminUse = { ...devices['Desktop Chrome'], trace: 'on', video: 'on' } as const;

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
      name: 'admin-parallel',
      testMatch: specsMatching(WORKER_OWNED_SPECS),
      fullyParallel: false,
      outputDir: './test-results/admin-parallel',
      use: adminUse,
    },
    {
      name: 'admin-serial',
      testMatch: specsMatching(SHARED_LISTING_SPECS),
      fullyParallel: false,
      outputDir: './test-results/admin-serial',
      use: adminUse,
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
