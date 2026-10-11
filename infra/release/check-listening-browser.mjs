// Local browser acceptance of the actual staged shell, edge gate and CSP.
// The existing preview supplies synthetic API/audio fixtures on a second origin.
import assert from 'node:assert/strict';
import { readFileSync, mkdtempSync, rmSync } from 'node:fs';
import { createServer, request as httpRequest } from 'node:http';
import { tmpdir } from 'node:os';
import { join, extname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { runInNewContext } from 'node:vm';
import { chromium, expect } from '@playwright/test';
import { stageListening } from './listening-artifacts.mjs';

const fixture = new URL(process.argv[2]);
assert.equal(fixture.hostname, '127.0.0.1'); assert.equal(fixture.protocol, 'http:');
const root = fileURLToPath(new URL('../../', import.meta.url));
const stage = mkdtempSync(join(tmpdir(), 'listening-browser-'));
stageListening(join(root, 'packages/offline-player/dist'), stage);
const csp = readFileSync(join(root, 'infra/headers/listening-csp.txt'), 'utf8').trim().replace('${audio_origins}', fixture.origin);
const gate = runInNewContext(readFileSync(join(root, 'infra/functions/listening-request.js.tftpl'), 'utf8')
  .replace('${enabled}', 'true') + '; handler');
const server = createServer(async (request, response) => {
  try {
    if (!/^\/(?![\/\\])/.test(request.url)) { response.writeHead(400); response.end(); return; }
    const incoming = new URL(request.url, 'http://127.0.0.1');
    const uri = incoming.pathname;
    if (uri.startsWith('/api/') || uri.startsWith('/assets/')) {
      const target = new URL(fixture.origin);
      target.pathname = uri; target.search = incoming.search;
      const upstream = await fetch(target, {
        redirect: 'error',
        headers: request.headers.authorization ? { authorization: request.headers.authorization } : {},
      });
      response.writeHead(upstream.status, { 'content-type': upstream.headers.get('content-type'), 'cache-control': 'no-store' });
      response.end(Buffer.from(await upstream.arrayBuffer())); return;
    }
    const result = gate({ request: { uri, method: request.method } });
    if (result.statusCode) { response.writeHead(result.statusCode); response.end(); return; }
    const file = join(stage, result.uri);
    const type = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.woff2': 'font/woff2', '.wasm': 'application/wasm' }[extname(file)] ?? 'application/octet-stream';
    response.writeHead(200, { 'content-type': type, 'content-security-policy': csp,
      'cache-control': 'no-cache', 'x-content-type-options': 'nosniff', 'x-robots-tag': 'noindex, nofollow' });
    response.end(readFileSync(file));
  } catch { response.writeHead(500); response.end(); }
});
await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
let browser;
try {
  browser = await chromium.launch();
  const context = await browser.newContext();
  const origin = `http://127.0.0.1:${server.address().port}`;
  for (const path of ['http://example.invalid/api/listening/package', '//example.invalid/api/listening/package', '/\\example.invalid/api/listening/package']) {
    const status = await new Promise((resolve, reject) => {
      const request = httpRequest(origin, { path }, (response) => { response.resume(); resolve(response.statusCode); });
      request.on('error', reject); request.end();
    });
    assert.equal(status, 400, 'Reject absolute and authority-form proxy targets');
  }
  await context.addInitScript(() => {
    globalThis.cspViolations = [];
    document.addEventListener('securitypolicyviolation', (event) => globalThis.cspViolations.push({ directive: event.violatedDirective, blocked: event.blockedURI, source: event.sourceFile, line: event.lineNumber }));
  });
  let page = await context.newPage();
  const errors = [];
  const observe = (value) => value.on('pageerror', (error) => errors.push(error.message));
  observe(page);
  await page.goto(`${origin}/offline-player/#probe`);
  await page.locator('#token').fill(`abs_device_${'e'.repeat(64)}`); // Deliberately invalid production token; preview fixture only.
  await page.locator('#prepare').click();
  await expect(page.locator('#readiness')).toHaveText('新しい配布物の準備ができました');
  assert.deepEqual(await page.evaluate(() => globalThis.cspViolations), []);
  assert.equal((await fetch(`${origin}/offline-player/audio/first.flac`)).status, 404);
  await page.close(); await context.setOffline(true); page = await context.newPage(); observe(page);
  assert.ok((await page.goto(`${origin}/offline-player/#probe`)).fromServiceWorker());
  await expect(page.locator('#readiness')).toHaveText('オフライン再生の準備ができました');
  await page.getByRole('button', { name: 'Reel study', exact: true }).click();
  await expect(page.locator('#play-status')).toHaveText('再生できます');
  await page.locator('#play').click(); await expect(page.locator('#play-status')).toHaveText('再生中');
  await expect.poll(() => page.locator('#seek').inputValue().then(Number)).toBeGreaterThan(0.3);
  await expect(page.locator('#analysis-status')).toBeEmpty();
  await expect(page.locator('#probe')).toContainText(/worklet load p50 [\d.]+%/);
  await page.locator('#pause').click(); await page.locator('#probe-benchmark').click();
  await expect(page.locator('#probe')).toContainText(/dsp bench [1-9]\d* quanta/);
  assert.ok(await page.evaluate(async () => { await document.fonts.ready; return document.fonts.check('16px "Klee One"', '試聴'); }));
  assert.deepEqual(await page.evaluate(() => globalThis.cspViolations), []); assert.deepEqual(errors, []);
  console.log('Staged shell: prepare, offline restart, playback, DSP worker, fonts and CSP passed (synthetic fixtures).');
} finally {
  await browser?.close(); await new Promise((resolve) => server.close(resolve));
  rmSync(stage, { recursive: true, force: true });
}
