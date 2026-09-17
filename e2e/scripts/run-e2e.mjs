#!/usr/bin/env node

/**
 * E2E の2つの組を、それぞれに合った並列度で順に走らせる（#428）。
 *
 *   node scripts/run-e2e.mjs [playwright test への追加引数]
 *
 * 組の境界は `playwright.config.ts` が持つ。ここが持つのは並列度と順序だけ。
 *
 * - `public`（読むだけ）は複数 worker で並列に走らせる。
 * - `admin`（共有している状態を書き換える）は 1 worker で直列に走らせる。
 *
 * Playwright の `workers` は全体の値で組ごとに持てないため、`--project` と `--workers` を組ごとに渡して
 * 2回起動する。HTML レポートは組ごとの置き場へ出す（同じ場所だと後の組が前の組を消す）。
 *
 * 片方が落ちてももう片方は走らせる。CI では両方の結果を1回で読みたい。終了コードはどちらかが
 * 落ちていれば非0。追加引数（`--grep` など）は両方の組へ渡す。該当するシナリオが無い組は空で通す。
 */

import { spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const e2eRoot = fileURLToPath(new URL('../', import.meta.url));
const playwrightCli = createRequire(import.meta.url).resolve('@playwright/test/cli');

/*
 * 公開側の worker 数。GitHub の Linux ランナーは 4 vCPU で、同じ機械で backend と MinIO も動く。
 * 手元で変えて試すときは環境変数で上書きする。
 */
const PUBLIC_WORKERS = process.env.E2E_PUBLIC_WORKERS ?? '4';

const extraArgs = process.argv.slice(2);

const SUITES = [
  { project: 'public', workers: PUBLIC_WORKERS },
  { project: 'admin', workers: '1' },
];

const run = ({ project, workers }) => {
  console.log(`\n=== ${project}（workers: ${workers}）===\n`);
  const result = spawnSync(
    process.execPath,
    [
      playwrightCli,
      'test',
      `--project=${project}`,
      `--workers=${workers}`,
      '--pass-with-no-tests',
      ...extraArgs,
    ],
    {
      cwd: e2eRoot,
      stdio: 'inherit',
      env: { ...process.env, E2E_HTML_REPORT_DIR: `playwright-report/${project}` },
    },
  );
  return { project, status: result.status ?? 1 };
};

const results = SUITES.map(run);
const failed = results.filter(({ status }) => status !== 0);

console.log(
  `\n=== 結果 ===\n${results.map(({ project, status }) => `${project}: ${status === 0 ? 'passed' : `failed (exit ${String(status)})`}`).join('\n')}`,
);

process.exit(failed.length === 0 ? 0 : 1);
