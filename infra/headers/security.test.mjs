import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { toEmbedUrl } from '../../packages/external-audio/src/index.ts';
import { securityHeaders } from './security.mjs';

const origins = { apiOrigin: 'https://api.example.invalid', imageOrigin: 'https://site.example.invalid', uploadOrigin: 'https://uploads.example.invalid' };
const policy = (kind) => securityHeaders(kind, origins)['Content-Security-Policy'];
const directives = (kind) => Object.fromEntries(policy(kind).split('; ').map((part) => {
  const [name, ...values] = part.split(' ');
  return [name, values];
}));

test('all response kinds have security headers and bounded, fully resolved CSP', () => {
  for (const kind of ['public', 'admin', 'api', 'assets']) {
    const headers = securityHeaders(kind, origins);
    assert.equal(headers['Strict-Transport-Security'], 'max-age=31536000');
    assert.equal(headers['X-Content-Type-Options'], 'nosniff');
    assert.equal(headers['Referrer-Policy'], 'strict-origin-when-cross-origin');
    assert.ok(policy(kind).length <= 1783);
    assert.doesNotMatch(policy(kind), /\$\{|unsafe-eval|\*/);
    assert.deepEqual(directives(kind)['default-src'], ["'none'"]);
    assert.deepEqual(directives(kind)['base-uri'], ["'none'"]);
  }
});

test('admin upload/API destinations do not widen public or non-document policies', () => {
  assert.ok(directives('admin')['connect-src'].includes(origins.uploadOrigin));
  assert.ok(directives('admin')['connect-src'].includes(origins.apiOrigin));
  for (const kind of ['public', 'api', 'assets']) {
    assert.ok(!policy(kind).includes(origins.uploadOrigin));
    assert.ok(!policy(kind).includes(origins.apiOrigin));
  }
  assert.ok(directives('admin')['frame-src'].includes("'self'"));
  for (const kind of ['api', 'assets']) assert.deepEqual(directives(kind)['frame-ancestors'], ["'none'"]);
});

test('every accepted audio source maps to an embed origin allowed on both page kinds', () => {
  const java = readFileSync(new URL('../../backend/src/main/java/com/abservice/domain/model/vo/common/ExternalAudioUrl.java', import.meta.url), 'utf8');
  const declaration = java.match(/ALLOWED_HOSTS = Set\.of\(([\s\S]*?)\);/u)?.[1];
  assert.ok(declaration, 'Could not inspect accepted audio hosts');
  const hosts = [...declaration.matchAll(/"([^"]+)"/gu)].map((match) => match[1]);
  assert.ok(hosts.length > 0);
  for (const host of hosts) {
    const embedOrigin = new URL(toEmbedUrl(`https://${host}/example`)).origin;
    for (const kind of ['public', 'admin']) assert.ok(directives(kind)['frame-src'].includes(embedOrigin));
  }
});
