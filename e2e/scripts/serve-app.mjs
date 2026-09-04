#!/usr/bin/env node

/**
 * 組み上がったアプリを配信する（Playwright の webServer から起動される）。
 *
 *   node scripts/serve-app.mjs public
 *   node scripts/serve-app.mjs admin
 *
 * 起動待ち・データ投入・組み立ては `prepare-stack.mjs` が済ませている。ここは配信だけを担う。
 *
 * `astro preview` は常にデーモンとして起動してすぐ終了するため、プロセスの生存で準備完了を判断する
 * webServer から使えない。組み上がったファイルを返すだけで足りるため、ここに置く。将来の本番配信も
 * 素の静的配信になる見込み（#125）で、Astro 独自の機能を持つ開発用サーバより実際に近い。
 * E2E そのものは配信先の環境に依存しない（ローカルと CI の中で完結する）。
 *
 * 公開サイトと管理画面は別のポートで配信する。1つのプロセスに畳まないのは、Playwright に
 * 「どちらが上がっていないのか」を持たせるため（webServer は URL ごとに待つ）。
 */

import { existsSync, statSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { extname, join, normalize } from 'node:path';

import { apps, basePathOf, portOf } from '../src/support/config.ts';

const repositoryRoot = new URL('../../', import.meta.url).pathname;

const fail = (message) => {
  console.error(message);
  process.exit(1);
};

const appName = process.argv[2] ?? '';

const app = Object.hasOwn(apps, appName)
  ? apps[appName]
  : fail(`配信するアプリを指定してください: ${Object.keys(apps).join(' | ')}`);

const distDir = join(repositoryRoot, app.distDir);
const port = portOf(appName);
const basePath = basePathOf(appName);

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

/*
 * 組み上がった成果物はプレフィックスを含まない平らな形で出る（Astro の `base` は URL にだけ効く）。
 * 配信側で剥がす。プレフィックスの外への要求は、そのアプリの担当ではないため引かない。
 */
const withoutBasePath = (pathname) => {
  const atBase = pathname === basePath ? '/' : undefined;
  const underBase = pathname.startsWith(`${basePath}/`)
    ? pathname.slice(basePath.length)
    : undefined;
  return atBase ?? underBase;
};

const candidatesOf = (withinApp) => {
  const withinDist = join(distDir, normalize(withinApp).replace(/^(\.\.[/\\])+/u, ''));
  return [withinDist, join(withinDist, 'index.html')];
};

const resolveFile = (pathname) =>
  [withoutBasePath(pathname)]
    .filter((withinApp) => withinApp !== undefined)
    .flatMap(candidatesOf)
    .find((candidate) => existsSync(candidate) && statSync(candidate).isFile());

/*
 * 見つからない要求には、組み上がった 404 のページを 404 のまま返す（#197。未存在と非公開を区別しない）。
 * 本番の配信もエラーページを同じ形で返す想定のため（#125）、ここでも本文を伴わせる。管理画面は
 * 404 のページを持たないため、その場合は本文なしで返る。
 */
const notFoundPage = join(distDir, '404.html');

const respondNotFound = (response) =>
  existsSync(notFoundPage)
    ? readFile(notFoundPage).then((body) => {
        response.writeHead(404, {
          'Content-Type': CONTENT_TYPES['.html'],
          'Content-Length': body.byteLength,
        });
        response.end(body);
      })
    : response.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' }).end('Not Found');

createServer((request, response) => {
  const pathname = new URL(request.url ?? '/', `http://127.0.0.1:${String(port)}`).pathname;
  const file = resolveFile(pathname);

  return file === undefined
    ? respondNotFound(response)
    : readFile(file).then((body) => {
        response.writeHead(200, {
          'Content-Type': CONTENT_TYPES[extname(file)] ?? 'application/octet-stream',
          'Content-Length': body.byteLength,
        });
        response.end(body);
      });
}).listen(port, '127.0.0.1', () => {
  console.log(`${appName} を配信しています: http://127.0.0.1:${String(port)}`);
});
