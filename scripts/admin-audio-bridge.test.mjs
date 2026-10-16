import assert from 'node:assert/strict';
import { createServer, request } from 'node:http';
import { test } from 'node:test';
import https from 'node:https';
import { syncBuiltinESMExports } from 'node:module';
import { PassThrough } from 'node:stream';
import { startAudioBridge } from './admin-audio-bridge.mjs';

const ID = '0192f8a0-0000-7000-8000-000000000001';
const ROOT = '/api/v1/admin/private-audio/registrations';
const fixture = async (t, handler, options = {}) => {
  const upstream = createServer(handler);
  await new Promise((resolve) => upstream.listen(0, '127.0.0.1', resolve));
  const bridge = await startAudioBridge({ siteOrigin: 'https://example.test', apiPort: upstream.address().port,
    originToken: 'fixture-origin-not-a-real-secret', ...options });
  t.after(async () => { await bridge.close(); upstream.closeAllConnections(); await new Promise((resolve) => upstream.close(resolve)); });
  return new URL(bridge.url).origin;
};
const send = (origin, path, { method = 'GET', headers = {}, body = '', raw = false } = {}) => new Promise((resolve, reject) => {
  const url = new URL(origin);
  const req = request({ host: url.hostname, port: url.port, path, method,
    headers: { Origin: origin, ...headers } }, (res) => {
    const chunks = [];
    res.on('data', (chunk) => chunks.push(chunk));
    res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body: Buffer.concat(chunks).toString() }));
  });
  req.on('error', reject);
  if (raw) { req.write(body); req.end(); } else req.end(body);
});

test('forwards only origin verification and caller authorization, preserving binary bytes', async (t) => {
  const payload = Buffer.from([102, 76, 97, 67, 0, 255, 128, 1]);
  const origin = await fixture(t, async (req, res) => {
    assert.equal(req.url, `${ROOT}/${ID}/content`);
    assert.equal(req.headers['x-origin-verify'], 'fixture-origin-not-a-real-secret');
    assert.equal(req.headers.authorization, 'Bearer fixture-admin');
    assert.equal(req.headers.origin, undefined);
    assert.equal(req.headers.cookie, undefined);
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    assert.deepEqual(Buffer.concat(chunks), payload);
    res.setHeader('X-Origin-Verify', 'never-echo-this');
    res.setHeader('Set-Cookie', 'never-echo-this');
    res.end('confirmed');
  });
  const result = await send(origin, `${ROOT}/${ID}/content`, { method: 'PUT', body: payload,
    headers: { Authorization: 'Bearer fixture-admin', 'Content-Type': 'audio/flac', Cookie: 'ignored' } });
  assert.equal(result.status, 200);
  assert.equal(result.body, 'confirmed');
  assert.equal(result.headers['x-origin-verify'], undefined);
  assert.equal(result.headers['set-cookie'], undefined);
});

test('rejects other websites, rebound hosts, unrelated writes, traversal and non-FLAC without upstream access', async (t) => {
  let calls = 0;
  const origin = await fixture(t, (_req, res) => { calls++; res.end(); });
  for (const args of [
    { headers: { Origin: 'https://example.test' } },
    { headers: { Host: 'evil.test' } },
    { headers: { 'Sec-Fetch-Site': 'cross-site' } },
    { method: 'POST', headers: { Origin: '' } },
  ]) assert.equal((await send(origin, ROOT, args)).status, 403);
  assert.equal((await send(origin, '/api/v1/admin/albums', { method: 'DELETE' })).status, 404);
  assert.equal((await send(origin, '/admin/../api/v1/admin/albums')).status, 400);
  assert.equal((await send(origin, '//example.test/admin/')).status, 400);
  assert.equal((await send(origin, `${ROOT}/${ID}/content`, { method: 'PUT', headers: { 'Content-Type': 'text/plain' } })).status, 415);
  assert.equal((await send(origin, `${ROOT}/${ID}/content`, { method: 'PUT', headers: { 'Content-Type': 'audio/flac', 'Content-Length': String(256 * 1024 * 1024 + 1) } })).status, 413);
  assert.equal(calls, 0);
});

test('does not add administrator credentials or follow redirects and preserves API refusal', async (t) => {
  const origin = await fixture(t, (req, res) => {
    assert.equal(req.headers.authorization, undefined);
    res.writeHead(req.url.endsWith('/confirm') ? 302 : 401, { Location: 'https://example.test/elsewhere' }); res.end();
  });
  assert.equal((await send(origin, `${ROOT}/${ID}`)).status, 401);
  const redirect = await send(origin, `${ROOT}/${ID}/confirm`, { method: 'POST' });
  assert.equal(redirect.status, 502);
  assert.equal(redirect.headers.location, undefined);
});

test('enforces a whole response deadline and sends mutations once', async (t) => {
  let calls = 0;
  const origin = await fixture(t, (req) => { calls++; req.resume(); }, { deadlineMs: 50 });
  const result = await send(origin, ROOT, { method: 'POST' });
  assert.equal(result.status, 504);
  assert.equal(calls, 1);
});

test('local capability endpoint contains no secrets and never contacts upstream', async (t) => {
  const origin = await fixture(t, () => assert.fail('unexpected upstream request'));
  const result = await send(origin, '/__audio-connection');
  assert.equal(result.status, 200);
  assert.deepEqual(JSON.parse(result.body), { kind: 'ssm-audio', maxBytes: 268435456, deadlineMs: 600000 });
});

test('static pages use the fixed HTTPS site without forwarding any credentials', async (t) => {
  const calls = [];
  t.mock.method(https, 'request', (url, options, receive) => {
    calls.push({ url: url.href, options });
    const outgoing = new PassThrough();
    outgoing.resume();
    outgoing.on('finish', () => {
      const incoming = new PassThrough();
      incoming.statusCode = 200;
      incoming.headers = { 'content-type': 'text/html', 'content-security-policy': "default-src 'self'", 'set-cookie': 'not-forwarded' };
      receive(incoming);
      incoming.end('<h1>fixture admin</h1>');
    });
    return outgoing;
  });
  syncBuiltinESMExports();
  t.after(() => { t.mock.restoreAll(); syncBuiltinESMExports(); });
  const origin = await fixture(t, () => assert.fail('static must not reach the API'));
  const response = await send(origin, '/admin/albums/audio/?albumId=fixture', {
    headers: { Authorization: 'Bearer never-send-to-static', Cookie: 'never-send-to-static' },
  });
  assert.equal(response.status, 200);
  assert.equal(response.body, '<h1>fixture admin</h1>');
  assert.equal(response.headers['content-security-policy'], "default-src 'self'");
  assert.equal(response.headers['set-cookie'], undefined);
  assert.deepEqual(calls, [{ url: 'https://example.test/admin/albums/audio/?albumId=fixture',
    options: { method: 'GET', headers: { 'Accept-Encoding': 'identity' } } }]);
});

test('bounds chunked request bodies even without Content-Length', async (t) => {
  const origin = await fixture(t, (req) => req.resume());
  const response = await send(origin, ROOT, { method: 'POST', raw: true, body: Buffer.alloc(65537),
    headers: { 'Content-Type': 'application/json' } });
  assert.equal(response.status, 413);
});
