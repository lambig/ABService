import assert from 'node:assert/strict';
import test from 'node:test';
import { readErrorPage, renderStaticNotFound } from './static-page-404.mjs';

const event = (uri = '/missing/index.html', status = '404', method = 'GET') => ({
  Records: [{ cf: {
    request: { uri, method, origin: { s3: { domainName: 'static.example.invalid', path: '' } } },
    response: { status, headers: {
      'content-type': [{ value: 'application/xml' }],
      'content-length': [{ value: '999' }],
      'content-encoding': [{ value: 'gzip' }],
      etag: [{ value: 'old' }],
      'last-modified': [{ value: 'old' }],
      via: [{ value: 'preserved' }],
      'transfer-encoding': [{ value: 'chunked' }],
    }, body: '<Error>NoSuchKey</Error>' },
  } }],
});
const page = '<!doctype html><html lang="ja"><h1>ページが見つかりません</h1></html>';

test('storage reads only the configured error key for the actual origin, never viewer input', async () => {
  const origins = {
    'public.example.invalid': { bucket: 'public-site', key: '404.html' },
    'admin.example.invalid': { bucket: 'admin-site', key: 'admin/404.html' },
  };
  for (const [domainName, origin] of Object.entries(origins)) {
    let destroyed = false;
    const sdk = {
      GetObjectCommand: class { constructor(input) { this.input = input; } },
      S3Client: class {
        constructor(options) { assert.deepEqual(options, { region: 'us-east-1', maxAttempts: 1 }); }
        async send(command, options) {
          assert.deepEqual(command.input, { Bucket: origin.bucket, Key: origin.key });
          assert.ok(options.abortSignal instanceof AbortSignal);
          return { ContentType: 'text/html; charset=utf-8', ContentLength: Buffer.byteLength(page), Body: { transformToString: async () => page } };
        }
        destroy() { destroyed = true; }
      },
    };
    const request = { uri: '/attacker.html', querystring: 'Key=private', headers: { host: [{ value: 'attacker.invalid' }] }, origin: { s3: { domainName, path: '' } } };
    assert.equal(await readErrorPage(request, { region: 'us-east-1', origins }, sdk), page);
    assert.equal(destroyed, true);
    await assert.rejects(readErrorPage({ ...request, origin: { s3: { domainName: 'other.example.invalid' } } }, { origins }, sdk), /Unexpected/);
    await assert.rejects(readErrorPage({ ...request, origin: { s3: { domainName, path: '/private' } } }, { origins }, sdk), /Unexpected/);
  }
});

test('invalid storage content is rejected before reading its body and closes the client', async () => {
  for (const metadata of [{ ContentType: 'application/xml', ContentLength: 20 }, { ContentType: 'text/html', ContentLength: 512 * 1024 + 1 }]) {
    let destroyed = false;
    let bodyDestroyed = false;
    const sdk = {
      GetObjectCommand: class {},
      S3Client: class {
        async send() { return { ...metadata, Body: { destroy: () => { bodyDestroyed = true; }, transformToString: () => assert.fail('must not read') } }; }
        destroy() { destroyed = true; }
      },
    };
    await assert.rejects(readErrorPage({ origin: { s3: { domainName: 'static.example.invalid' } } }, { origins: { 'static.example.invalid': { bucket: 'site', key: '404.html' } } }, sdk), /Invalid/);
    assert.equal(destroyed, true);
    assert.equal(bodyDestroyed, true);
  }
});

test('public/admin missing HTML gets the generated page, real 404, and no stale entity metadata', async () => {
  for (const uri of ['/albums/missing/index.html', '/admin/missing/index.html', '/administrators/index.html', '/missing.html']) {
    const source = event(uri);
    const result = await renderStaticNotFound(source, async () => page);
    assert.equal(result.status, '404');
    assert.equal(result.body, page);
    assert.equal(result.headers['content-type'][0].value, 'text/html; charset=utf-8');
    assert.equal(result.headers['content-length'][0].value, String(Buffer.byteLength(page)));
    assert.equal(result.headers['cache-control'][0].value, 'no-store');
    for (const header of ['content-encoding', 'etag', 'last-modified']) assert.equal(result.headers[header], undefined);
    for (const header of ['via', 'transfer-encoding']) assert.equal(result.headers[header], source.Records[0].cf.response.headers[header]);
  }
});

test('HEAD has the same status and representation headers as GET but no body', async () => {
  const get = await renderStaticNotFound(event(), async () => page);
  const head = await renderStaticNotFound(event('/missing/index.html', '404', 'HEAD'), async () => page);
  assert.equal(head.status, get.status);
  assert.deepEqual(head.headers, get.headers);
  assert.equal(head.body, '');
});

test('success, redirects, real access denial and origin failures pass through without a storage call', async () => {
  for (const status of ['200', '301', '403', '500', '502', '503']) {
    const source = event('/missing/index.html', status);
    const result = await renderStaticNotFound(source, () => assert.fail('must not read'));
    assert.equal(result, source.Records[0].cf.response);
  }
});

test('API Problem Details, asset errors and non-page files remain unchanged', async () => {
  for (const uri of ['/api/v1/missing', '/api/missing.html', '/assets/missing.html', '/_astro/missing.html', '/admin/_astro/missing.html', '/admin/missing.js', '/missing.css', '/missing.png']) {
    const source = event(uri);
    source.Records[0].cf.response.headers['content-type'] = [{ value: 'application/problem+json' }];
    source.Records[0].cf.response.body = '{"status":404,"type":"about:blank"}';
    assert.equal(await renderStaticNotFound(source, () => assert.fail('must not read')), source.Records[0].cf.response);
  }
  const api = event('/missing.html');
  api.Records[0].cf.request.origin = { custom: {} };
  assert.equal(await renderStaticNotFound(api, () => assert.fail('must not read')), api.Records[0].cf.response);
  const post = event('/missing.html', '404', 'POST');
  assert.equal(await renderStaticNotFound(post, () => assert.fail('must not read')), post.Records[0].cf.response);
});

test('missing/unreadable/oversized error artifact fails closed without disclosing storage details', async () => {
  for (const load of [async () => { throw new Error('AccessDenied private-bucket'); }, async () => 'a'.repeat(512 * 1024 + 1)]) {
    const result = await renderStaticNotFound(event(), load);
    assert.equal(result.status, '503');
    assert.equal(result.body, 'Service Unavailable');
    assert.equal(result.headers['cache-control'][0].value, 'no-store');
    assert.equal(result.headers['content-type'][0].value, 'text/plain; charset=utf-8');
  }
});
