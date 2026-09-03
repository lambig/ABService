#!/usr/bin/env node

/**
 * E2E を走らせる前に、スタックと成果物を整える。
 *
 * 公開サイトは静的出力で、ビルド時の内容がそのまま HTML になる（DECISIONS 24）。したがって画面へ
 * 現れてほしいデータは**組み立てより前**に入れる必要がある。シナリオの中で投入してもその回の画面には
 * 出ないため、順序をここで固定する。
 *
 *   バックエンドの起動を待つ → 前回の証跡を捨てる → 画面用のデータを入れる → 組む
 *
 * バックエンドと DB の起動は受け持たない。落ちたときにどこまで進んだのかを曖昧にしないため、起動は
 * 開発者（ローカル）か CI のステップに任せ、ここでは待つだけにする。
 */

import { execFileSync } from 'node:child_process';
import { rmSync } from 'node:fs';
import { join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

import { stack } from '../src/support/config.ts';
import { seedForBuild } from '../src/support/build-fixtures.ts';

const WAIT_LIMIT_MS = 120_000;
const RETRY_INTERVAL_MS = 2_000;

const repositoryRoot = new URL('../../', import.meta.url).pathname;

const isReady = async () => {
  const response = await fetch(`${stack.backendBaseUrl}/q/health/ready`).catch(() => null);
  return response?.ok === true;
};

const waitForBackend = async (deadline) => {
  const ready = await isReady();
  return ready
    ? undefined
    : Date.now() > deadline
      ? Promise.reject(
          new Error(
            [
              `バックエンドが起動していません（${stack.backendBaseUrl}）。`,
              'docker compose up -d postgres minio と npm run dev:backend:e2e で起動してから実行してください。',
              'E2E は専用のデータベースを見ます（#252）。開発用の npm run dev:backend とはポートも接続先も別です。',
            ].join(''),
          ),
        )
      : delay(RETRY_INTERVAL_MS).then(() => waitForBackend(deadline));
};

/*
 * STALE-EVIDENCE: 証跡は「この実行で撮ったもの」でなければ意味がない。シナリオや名前を変えた後に
 * 残っていると、生成されていない画像まで PR へ混ざる。実行のたびに捨てる。
 */
const clearEvidence = () => {
  rmSync(join(repositoryRoot, 'e2e/evidence'), { recursive: true, force: true });
};

const buildSite = () => {
  execFileSync('npm', ['run', 'build', '-w', 'abservice-frontend-public'], {
    cwd: repositoryRoot,
    stdio: 'inherit',
    env: { ...process.env, API_BASE_URL: stack.backendBaseUrl },
  });
};

await waitForBackend(Date.now() + WAIT_LIMIT_MS);
clearEvidence();
await seedForBuild();
buildSite();
