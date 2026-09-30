import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const requestHandler = (enabled) => {
  const code = readFileSync(new URL('./listening-request.js.tftpl', import.meta.url), 'utf8').replace('${enabled}', String(enabled));
  assert.ok(Buffer.byteLength(code) < 10000);
  return vm.runInNewContext(`${code}; handler`);
};
const shell = `/offline-player/releases/${'a'.repeat(64)}`;
const allowed = ['/offline-player', '/offline-player/', '/offline-player/index.html', '/offline-player/sw.js',
  `${shell}/index.html`, `${shell}/assets/app-123.js`, `${shell}/assets/font.woff2`];

test('disabled listening denies direct entry, worker and immutable assets even if already cached', () => {
  const handler = requestHandler(false);
  for (const uri of allowed) {
    const result = handler({ request: { uri, method: 'GET' } });
    assert.equal(result.statusCode, 404); assert.equal(result.headers['cache-control'].value, 'no-store');
    assert.equal(result.headers['x-robots-tag'].value, 'noindex, nofollow');
  }
});

test('enabled listening resolves canonical paths within the configured origin prefix', () => {
  const handler = requestHandler(true);
  for (const uri of allowed) {
    const result = handler({ request: { uri, method: 'HEAD' } });
    assert.equal(result.uri, ['/offline-player', '/offline-player/'].includes(uri) ? '/index.html' : uri.slice('/offline-player'.length));
    assert.equal(result.method, 'HEAD');
  }
});

test('lookalikes, preview fixtures, encoded paths and traversal cannot expose another prefix', () => {
  const handler = requestHandler(true);
  for (const uri of ['/offline-player-other/sw.js', '/offline-player/audio/first.flac', '/offline-player/assets/app.js',
    '/offline-player//index.html', '/offline-player/%73w.js', '/offline-player/../admin/index.html',
    `${shell}/assets/../index.html`, `${shell}/assets/./app.js`, `${shell}/assets//app.js`, `${shell}/assets/`,
    '/admin/../offline-player/index.html', '/offline-player\\sw.js']) {
    assert.equal(handler({ request: { uri } }).statusCode, 404, uri);
  }
});
