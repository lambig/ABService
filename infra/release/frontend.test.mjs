import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync, cpSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { createDelivery, filesUnder, invalidationPaths, validateManifest } from './frontend.mjs';

const codeSha = 'a'.repeat(40);
const config = { publicBucket: 'public-site', adminBucket: 'admin-site', releaseBucket: 'release-records', distributionId: 'EXAMPLE123' };
const setup = () => {
  const root = mkdtempSync(join(tmpdir(), 'delivery-test-'));
  const publicRoot = join(root, 'public');
  const adminRoot = join(root, 'admin');
  mkdirSync(join(publicRoot, 'articles', 'old'), { recursive: true });
  mkdirSync(adminRoot);
  writeFileSync(join(publicRoot, 'index.html'), 'home');
  writeFileSync(join(publicRoot, '404.html'), 'missing');
  writeFileSync(join(publicRoot, 'articles', 'old', 'index.html'), 'old');
  writeFileSync(join(adminRoot, 'index.html'), 'admin');
  const objects = new Map();
  const archives = new Map();
  const calls = [];
  let failure = () => false;
  const aws = (args) => {
    calls.push(args);
    if (failure(args)) throw new Error('injected transfer/invalidation failure');
    const option = (key) => args[args.indexOf(key) + 1];
    const key = option('--key');
    if (args[1] === 'get-object') {
      if (!objects.has(key)) throw Object.assign(new Error('missing'), { stderr: 'An error occurred (NoSuchKey)' });
      writeFileSync(args.at(-2), objects.get(key));
    }
    if (args[1] === 'put-object') objects.set(key, readFileSync(option('--body'), 'utf8'));
    if (args[1] === 'delete-object') objects.delete(key);
    if (args[1] === 'sync' && args[3].startsWith('s3://release-records/')) {
      const target = join(root, `archive-${archives.size}`);
      cpSync(args[2], target, { recursive: true });
      archives.set(args[3], target);
    }
    if (args[1] === 'sync' && args[2].startsWith('s3://release-records/')) cpSync(archives.get(args[2]), args[3], { recursive: true });
    if (args[1] === 'create-invalidation') {
      return JSON.stringify({ Invalidation: { Id: 'I123' } });
    }
    return '{}';
  };
  return { root, publicRoot, adminRoot, objects, archives, calls,
    delivery: createDelivery(config, aws), failWhen: (predicate) => { failure = predicate; } };
};
const deploy = (context, id = '100-1') => context.delivery.publish({ action: 'deploy', id, codeSha,
  publicRoot: context.publicRoot, adminRoot: context.adminRoot });

test('preflight accepts first deployment and completed state without writes', () => {
  const context = setup();
  assert.equal(context.delivery.preflight(), null);
  const current = deploy(context);
  context.calls.length = 0;
  assert.deepEqual(context.delivery.preflight(), current);
  assert.ok(context.calls.every((args) => args[1] === 'get-object'));
});

test('preflight refuses pending, access errors and malformed current', () => {
  const context = setup();
  context.objects.set('pending.json', JSON.stringify({ id: '101-1' }));
  assert.throws(() => context.delivery.preflight(), /incomplete/);
  assert.ok(context.calls.every((args) => args[1] === 'get-object'));
  context.objects.delete('pending.json');
  context.objects.set('current.json', '{broken');
  assert.throws(() => context.delivery.preflight(), SyntaxError);
  const denied = createDelivery(config, () => { throw Object.assign(new Error('denied'), { stderr: '(AccessDenied)' }); });
  assert.throws(() => denied.preflight(), /denied/);
});

test('first deploy archives both builds; records current only after invalidation completes', () => {
  const context = setup();
  deploy(context);
  const live = context.calls.filter((args) => args[1] === 'sync' && !args[3].startsWith('s3://release-records/'));
  assert.deepEqual([...new Set(live.map((args) => args[3]))], ['s3://public-site/', 's3://admin-site/admin/']);
  const pending = context.calls.findIndex((args) => args[1] === 'put-object' && args.includes('pending.json'));
  assert.ok(pending < context.calls.indexOf(live[0]));
  const current = context.calls.findIndex((args) => args[1] === 'put-object' && args.includes('current.json'));
  assert.ok(context.calls.findIndex((args) => args[1] === 'wait') < current);
  assert.ok(!context.objects.has('pending.json'));
  assert.equal(JSON.parse(context.objects.get('current.json')).public.codeSha, codeSha);
});

test('content rebuild uses deployed SHA, removes withdrawn HTML and does not deploy admin', () => {
  const context = setup();
  const previous = deploy(context);
  assert.equal(context.delivery.resolveCode('rebuild-public', 'b'.repeat(40)), codeSha);
  context.calls.length = 0;
  rmSync(join(context.publicRoot, 'articles'), { recursive: true });
  const result = context.delivery.publish({ action: 'rebuild-public', id: '101-1', codeSha, publicRoot: context.publicRoot });
  assert.deepEqual(result.admin, previous.admin);
  assert.ok(!context.calls.some((args) => args.some((value) => value.includes('s3://admin-site'))));
  assert.ok(context.calls.some((args) => args.includes('--delete') && args.includes('s3://public-site/')));
  const paths = invalidationPaths([previous.public, result.public], 'public');
  assert.ok(paths.includes('/articles/old/') && paths.includes('/articles/old') && paths.includes('/articles/old/index.html'));
  assert.ok(!paths.some((path) => path === '/*' || path.startsWith('/admin') || path.startsWith('/assets')));
  assert.throws(() => context.delivery.publish({ action: 'rebuild-public', id: '102-1', codeSha: 'b'.repeat(40), publicRoot: context.publicRoot }), /currently deployed/);
});

test('invalidation failure leaves previous SHA and blocks rebuild until artifact rollback', () => {
  const context = setup();
  const original = deploy(context);
  context.failWhen((args) => args[1] === 'wait');
  assert.throws(() => context.delivery.publish({ action: 'deploy', id: '102-1', codeSha: 'b'.repeat(40), publicRoot: context.publicRoot, adminRoot: context.adminRoot }));
  assert.deepEqual(JSON.parse(context.objects.get('current.json')), original);
  assert.ok(context.objects.has('pending.json'));
  assert.throws(() => context.delivery.resolveCode('rebuild-public'), /incomplete/);
  context.failWhen(() => false);
  context.delivery.rollback({ targetId: '100-1', id: '103-1' });
  assert.deepEqual(JSON.parse(context.objects.get('current.json')), original);
  assert.ok(!context.objects.has('pending.json'));
});

test('copy failure never advances current and rollback checks archive bytes before any live write', () => {
  const context = setup();
  const original = deploy(context);
  context.failWhen((args) => args[1] === 'sync' && args.includes('s3://admin-site/admin/'));
  assert.throws(() => deploy(context, '104-1'));
  assert.deepEqual(JSON.parse(context.objects.get('current.json')), original);
  context.failWhen(() => false);
  writeFileSync(join(context.archives.get('s3://release-records/releases/100-1/public/'), 'index.html'), 'corrupt');
  context.calls.length = 0;
  assert.throws(() => context.delivery.rollback({ targetId: '100-1', id: '105-1' }), /checksum/);
  assert.ok(!context.calls.some((args) => args[1] === 'sync' && args[3].startsWith('s3://')));
});

test('missing build, missing initial release, reused ID and overlapping live buckets are rejected', () => {
  const context = setup();
  assert.throws(() => context.delivery.resolveCode('rebuild-public'), /No published/);
  rmSync(join(context.adminRoot, 'index.html'));
  assert.throws(() => deploy(context));
  assert.ok(!context.calls.some((args) => args[1] === 'put-object' || args[1] === 'sync'));
  writeFileSync(join(context.adminRoot, 'index.html'), 'admin');
  deploy(context);
  assert.throws(() => deploy(context), /already exists/);
  assert.throws(() => createDelivery({ ...config, releaseBucket: config.publicBucket }), /must differ/);
});

test('access errors are not treated as first deploy; unsafe archive paths are rejected', () => {
  const delivery = createDelivery(config, () => { throw Object.assign(new Error('denied'), { stderr: '(AccessDenied)' }); });
  assert.throws(() => delivery.resolveCode('deploy', codeSha), /denied/);
  const context = setup();
  const record = deploy(context);
  record.public.files[0].path = '../outside';
  assert.throws(() => validateManifest(record));
  assert.deepEqual(filesUnder(context.adminRoot), ['index.html']);
});
