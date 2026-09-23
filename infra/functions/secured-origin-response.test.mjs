import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import { secureOriginResponse } from './secured-origin-response.mjs';
import { buildSecurityHeaders } from '../headers/build-security-headers.mjs';

const security = JSON.parse(readFileSync(new URL('../headers/security.json', import.meta.url), 'utf8')
  .replaceAll('${image_sources}', "'self' data:")
  .replaceAll('${api_sources}', "'self'")
  .replaceAll('${upload_origin}', 'https://uploads.example.invalid'));
const kinds = ['public', 'admin', 'api', 'assets'];
const config = {
  security, noindex: { public: true, admin: true, api: true, assets: false },
  origins: Object.fromEntries(kinds.map((kind) => [`${kind}.example.invalid`, kind])),
};
const makeEvent = (kind, status) => ({ Records: [{ cf: {
  request: { method: 'GET', uri: kind === 'public' ? '/missing/index.html' : `/${kind}/missing.html`,
    origin: { [kind === 'api' ? 'custom' : 's3']: { domainName: `${kind}.example.invalid`, path: '' } } },
  response: { status, body: '{"status":404}', headers: {
    'content-type': [{ value: 'application/problem+json' }],
    'content-security-policy': [{ value: 'unsafe inherited policy' }],
    'x-robots-tag': [{ value: 'old noindex' }], via: [{ value: 'preserve' }],
  } },
} }] });

test('all origin statuses retain enforced headers, including errors skipped by viewer functions', async () => {
  for (const kind of kinds) for (const status of ['200', '301', '304', '401', '403', '404', '500', '503']) {
    const event = makeEvent(kind, status);
    const result = await secureOriginResponse(event, async () => '<html>not found</html>', config);
    assert.equal(result.status, status);
    assert.equal(result.headers.via[0].value, 'preserve');
    for (const [name, value] of Object.entries(buildSecurityHeaders(security, kind, config.noindex[kind]))) {
      assert.equal(result.headers[name.toLowerCase()][0].value, value, `${kind}/${status}/${name}`);
    }
    if (kind === 'assets') assert.equal(result.headers['x-robots-tag'], undefined);
    if (['api', 'assets'].includes(kind)) {
      assert.equal(result.body, event.Records[0].cf.response.body);
      assert.equal(result.headers['content-type'][0].value, 'application/problem+json');
    }
  }
});

test('static artifact failure has security headers and no storage details', async () => {
  const result = await secureOriginResponse(makeEvent('admin', '404'), async () => {
    throw new Error('private storage details');
  }, config);
  assert.equal(result.status, '503');
  assert.equal(result.body, 'Service Unavailable');
  assert.equal(result.headers['x-robots-tag'][0].value, 'noindex, nofollow');
  assert.equal(result.headers['cache-control'][0].value, 'no-store');
});

test('origin identity wins over ambiguous URI and viewer Host', async () => {
  const event = makeEvent('api', '404');
  event.Records[0].cf.request.uri = '/admin/../missing.html';
  event.Records[0].cf.request.headers = { host: [{ value: 'public.example.invalid' }] };
  const result = await secureOriginResponse(event, () => assert.fail('API must not read an HTML artifact'), config);
  assert.equal(result.headers['content-security-policy'][0].value, security.policies.api);
  event.Records[0].cf.request.origin.custom.domainName = 'unknown.example.invalid';
  await assert.rejects(secureOriginResponse(event, () => {}, config), /Unexpected response origin/);
});

test('generated viewer functions update cached headers without changing response identity', () => {
  const renderer = readFileSync(new URL('../headers/build-security-headers.mjs', import.meta.url), 'utf8')
    .replace('export function', 'function');
  const template = readFileSync(new URL('./security-response.js.tftpl', import.meta.url), 'utf8');
  for (const kind of kinds) for (const noindex of [false, true]) {
    const values = { renderer, config: JSON.stringify(security), kind: JSON.stringify(kind), noindex: String(noindex) };
    const code = template.replace(/\$\{(renderer|config|kind|noindex)\}/g, (_, key) => values[key]);
    assert.ok(Buffer.byteLength(code) < 10000, 'CloudFront function code quota');
    const context = vm.createContext({});
    vm.runInContext(code, context);
    const event = { response: { statusCode: 200, headers: {
      etag: { value: 'immutable-content' }, 'x-robots-tag': { value: 'stale-noindex' },
      'content-security-policy': { value: 'stale-policy' },
    } } };
    const result = context.handler(event);
    assert.equal(result, event.response);
    assert.equal(result.headers.etag.value, 'immutable-content');
    assert.equal(result.headers['content-security-policy'].value, security.policies[kind]);
    assert.equal(result.headers['x-robots-tag']?.value, noindex ? 'noindex, nofollow' : undefined);
  }
});
