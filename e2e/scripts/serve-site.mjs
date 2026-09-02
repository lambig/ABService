#!/usr/bin/env node

/**
 * 公開サイトを E2E 用に用意して配信する（Playwright の webServer から起動される）。
 *
 * 公開サイトは静的出力で、ビルド時にバックエンドから内容を取り込む（DECISIONS 24）。したがって
 * 「バックエンドの起動を待つ → 組む → 配信する」の順を守る必要があり、この3つをひとつのプロセスに置く。
 *
 * バックエンドと DB の起動は受け持たない。落ちたときにどこまで進んだのかを曖昧にしないため、起動は
 * 開発者（ローカル）か CI のステップに任せ、ここでは待つだけにする。
 *
 * 既定値は `src/support/config.ts` と揃える（片方だけ変えない）。
 */

import { execFileSync } from 'node:child_process';
import { existsSync, statSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { extname, join, normalize } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

const backendBaseUrl = process.env['E2E_BACKEND_BASE_URL'] ?? 'http://localhost:8080';
const sitePort = Number(process.env['E2E_SITE_PORT'] ?? '4321');

const WAIT_LIMIT_MS = 120_000;
const RETRY_INTERVAL_MS = 2_000;

const repositoryRoot = new URL('../../', import.meta.url).pathname;
const distDir = join(repositoryRoot, 'frontend-public/dist');

const CONTENT_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
};

const isReady = async () => {
  const response = await fetch(`${backendBaseUrl}/q/health/ready`).catch(() => null);
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
              `バックエンドが起動していません（${backendBaseUrl}）。`,
              'docker compose up -d postgres minio と npm run dev:backend で起動してから実行してください。',
            ].join(''),
          ),
        )
      : delay(RETRY_INTERVAL_MS).then(() => waitForBackend(deadline));
};

const buildSite = () => {
  execFileSync('npm', ['run', 'build', '-w', 'abservice-frontend-public'], {
    cwd: repositoryRoot,
    stdio: 'inherit',
    env: { ...process.env, API_BASE_URL: backendBaseUrl },
  });
};

/*
 * OWN-STATIC-SERVER: `astro preview` は常にデーモンとして起動してすぐ終了するため、プロセスの生存で
 * 準備完了を判断する webServer から使えない。配信先は S3 + CloudFront（#125）で、実体は素の静的配信の
 * ため、ここでも同じ形で配る。
 */
const resolveFile = (pathname) => {
  const withinDist = join(distDir, normalize(pathname).replace(/^(\.\.[/\\])+/u, ''));
  const candidates = [withinDist, join(withinDist, 'index.html')];
  return candidates.find((candidate) => existsSync(candidate) && statSync(candidate).isFile());
};

const serve = () =>
  createServer((request, response) => {
    const pathname = new URL(request.url ?? '/', `http://127.0.0.1:${String(sitePort)}`).pathname;
    const file = resolveFile(pathname);

    return file === undefined
      ? response.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' }).end('Not Found')
      : readFile(file).then((body) => {
          response.writeHead(200, {
            'Content-Type': CONTENT_TYPES[extname(file)] ?? 'application/octet-stream',
            'Content-Length': body.byteLength,
          });
          response.end(body);
        });
  }).listen(sitePort, '127.0.0.1', () => {
    console.log(`公開サイトを配信しています: http://127.0.0.1:${String(sitePort)}`);
  });

await waitForBackend(Date.now() + WAIT_LIMIT_MS);
buildSite();
serve();
