import assert from 'node:assert/strict';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { buildPublic } from './build-public.mjs';
import { readGeneration } from './generation.mjs';

const first = '11111111-1111-4111-8111-111111111111';
const second = '22222222-2222-4222-8222-222222222222';
const setup = () => {
  const root = mkdtempSync(join(tmpdir(), 'generation-build-'));
  const publicRoot = join(root, 'dist'); mkdirSync(publicRoot);
  const metadataPath = join(root, 'public-build.json');
  writeFileSync(metadataPath, 'old build must not survive');
  return { publicRoot, metadataPath, codeSha: 'a'.repeat(40), generation: async () => first,
    build: async () => {
      writeFileSync(join(publicRoot, 'index.html'), 'current');
      writeFileSync(join(publicRoot, '404.html'), 'missing');
    } };
};

test('build records generation, selected code and exact file hashes outside dist', async () => {
  const config = setup();
  const record = await buildPublic(config);
  assert.equal(record.generation, first);
  assert.equal(record.codeSha, config.codeSha);
  assert.deepEqual(record.files.map(({ path }) => path), ['404.html', 'index.html']);
  record.files.forEach(({ sha256 }) => assert.match(sha256, /^[0-9a-f]{64}$/));
  assert.deepEqual(JSON.parse(readFileSync(config.metadataPath)), record);
});

test('updates during SSG reject mixed data and remove the previous success record', async () => {
  const config = setup();
  const observed = [first, second];
  await assert.rejects(buildPublic({ ...config, generation: async () => observed.shift() }), /changed during SSG/);
  assert.equal(existsSync(config.metadataPath), false);
});

test('build failure, API failure and missing HTML never leave a deployable record', async () => {
  for (const override of [
    { build: async () => { throw new Error('build failed'); } },
    { generation: async () => { throw new Error('API failed'); } },
    { build: async () => {} },
  ]) {
    const config = setup();
    await assert.rejects(buildPublic({ ...config, ...override }));
    assert.equal(existsSync(config.metadataPath), false);
  }
});

test('generation fetch disables caches, bounds waiting, and rejects unavailable or invalid responses', async () => {
  const request = async (url, options) => {
    assert.equal(url.href, 'https://api.example.test/api/v1/public-data-generation');
    assert.equal(options.cache, 'no-store');
    assert.equal(options.redirect, 'error');
    assert.equal(options.headers['Cache-Control'], 'no-cache');
    assert.ok(options.signal instanceof AbortSignal);
    return Response.json({ generation: first }, { headers: { 'Cache-Control': 'no-store' } });
  };
  assert.equal(await readGeneration('https://api.example.test', request), first);
  for (const response of [new Response('', { status: 404 }), new Response('', { status: 500 }),
    Response.json({ generation: first }),
    Response.json({ generation: 'invalid' }, { headers: { 'Cache-Control': 'no-store' } }),
    new Response('broken', { headers: { 'Cache-Control': 'no-store' } })]) {
    await assert.rejects(readGeneration('https://api.example.test', async () => response));
  }
});
