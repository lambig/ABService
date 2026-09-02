#!/usr/bin/env node

/**
 * 組み上がった公開サイトを配信する（Playwright の webServer から起動される）。
 *
 * 起動待ち・データ投入・組み立ては `prepare-stack.mjs` が済ませている。ここは配信だけを担う。
 *
 * `astro preview` は常にデーモンとして起動してすぐ終了するため、プロセスの生存で準備完了を判断する
 * webServer から使えない。組み上がったファイルを返すだけで足りるため、ここに置く。将来の本番配信も
 * 素の静的配信になる見込み（#125）で、Astro 独自の機能を持つ開発用サーバより実際に近い。
 * E2E そのものは配信先の環境に依存しない（ローカルと CI の中で完結する）。
 */

import { existsSync, statSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { extname, join, normalize } from 'node:path';

import { sitePort } from '../src/support/config.ts';

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

const resolveFile = (pathname) => {
  const withinDist = join(distDir, normalize(pathname).replace(/^(\.\.[/\\])+/u, ''));
  const candidates = [withinDist, join(withinDist, 'index.html')];
  return candidates.find((candidate) => existsSync(candidate) && statSync(candidate).isFile());
};

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
