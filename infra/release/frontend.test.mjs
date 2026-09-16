import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync, cpSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { runInNewContext } from 'node:vm';
import { createDelivery, filesUnder, invalidationPaths, validateManifest } from './frontend.mjs';
import { artifactFiles } from './build-public.mjs';

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
  writeFileSync(join(adminRoot, '404.html'), 'admin missing');
  const objects = new Map();
  const archives = new Map();
  const calls = [];
  let failure = () => false;
  let generation = '11111111-1111-4111-8111-111111111111';
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
  return { root, publicRoot, adminRoot, objects, archives, calls, aws,
    buildMetadata: (target = codeSha) => ({ version: 1, codeSha: target, generation, files: artifactFiles(publicRoot) }),
    generation: () => generation, update: () => { generation = '22222222-2222-4222-8222-222222222222'; },
    delivery: createDelivery(config, aws, () => generation), failWhen: (predicate) => { failure = predicate; } };
};
const ancestor = (older, newer) => older < newer; // Mock the A -> B -> C -> D history.
const deploy = (context, id = '100-1', target = codeSha) => {
  assert.equal(context.delivery.acceptNormal(target, ancestor).deploy, true);
  return context.delivery.publish({ action: 'deploy', id, codeSha: target,
    publicRoot: context.publicRoot, adminRoot: context.adminRoot, buildMetadata: context.buildMetadata(target) });
};

test('both error pages are required before archives or live writes', () => {
  for (const site of ['public', 'admin']) {
    const context = setup();
    rmSync(join(context[`${site}Root`], '404.html'));
    assert.throws(() => deploy(context), new RegExp(`Missing ${site} 404.html`));
    assert.ok(!context.calls.some((args) => args[1] === 'sync'));
    assert.ok(!context.objects.has('pending.json'));
  }
});

test('rollback cannot remove the admin error document by restoring an older archive', () => {
  const context = setup();
  const record = deploy(context);
  record.admin.files = record.admin.files.filter((file) => file.path !== '404.html');
  context.objects.set('manifests/100-1.json', JSON.stringify(record));
  context.calls.length = 0;
  assert.throws(() => context.delivery.rollback({ targetId: '100-1', id: '102-1' }), /Archived admin has no 404.html/);
  assert.ok(!context.calls.some((args) => args[1] === 'sync'));
});

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
  const result = context.delivery.publish({ action: 'rebuild-public', id: '101-1', codeSha, publicRoot: context.publicRoot,
    buildMetadata: context.buildMetadata() });
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
  assert.throws(() => deploy(context, '102-1', 'b'.repeat(40)));
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
  assert.ok(!context.calls.some((args) => args[1] === 'sync' || (args[1] === 'put-object' && !args.includes('last-normal.json'))));
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
  assert.deepEqual(filesUnder(context.adminRoot), ['404.html', 'index.html']);
});

// Feed decisions from real release-state transitions into the actual workflow guards.
const normalJobs = (decision) => {
  const workflow = readFileSync(new URL('../../.github/workflows/deploy.yml', import.meta.url), 'utf8');
  const block = (name) => workflow.split(`\n  ${name}:\n`)[1].split(/\n  [\w-]+:\n/)[0];
  const context = { cancelled: () => false,
    github: { ref: 'refs/heads/main', event_name: 'workflow_run', repository: 'owner/repo', run_attempt: '1',
      event: { workflow_run: { conclusion: 'success', head_branch: 'main', event: 'push', head_repository: { full_name: 'owner/repo' } } } },
    vars: { AWS_DEPLOY_ROLE_ARN: 'backend-role' },
    needs: { preflight: { result: 'success', outputs: { deploy: String(decision.deploy), attempt: '1' } },
      deploy: { result: 'skipped', outputs: { attempt: '1' } } } };
  const evaluate = (expression) => runInNewContext(expression.replace(/^\$\{\{\s*|\s*\}\}$/g, ''), context);
  const backend = evaluate(block('deploy').match(/    if: >-\n((?:      .*(?:\n|$))+)/)[1].trim());
  context.needs.deploy.result = backend ? 'success' : 'skipped';
  return { backend, frontend: evaluate(block('frontend').match(/    if: (.+)/)[1]) };
};

test('normal C then frontend rollback A retains watermark C: late B skips both jobs, D advances', () => {
  const context = setup();
  const c = 'c'.repeat(40); const b = 'b'.repeat(40); const d = 'd'.repeat(40);
  const original = deploy(context);
  deploy(context, '101-1', c);
  const watermark = context.objects.get('last-normal.json');
  context.delivery.rollback({ targetId: '100-1', id: '102-1' });
  assert.deepEqual(JSON.parse(context.objects.get('current.json')), original);
  assert.equal(context.objects.get('last-normal.json'), watermark);
  // Rebuilding the rolled-back public must not lower the watermark either.
  context.delivery.publish({ action: 'rebuild-public', id: '103-1', codeSha, publicRoot: context.publicRoot,
    buildMetadata: context.buildMetadata() });
  assert.equal(context.objects.get('last-normal.json'), watermark);
  const before = new Map(context.objects);
  context.calls.length = 0;
  const stale = context.delivery.acceptNormal(b, ancestor);
  assert.deepEqual(normalJobs(stale), { backend: false, frontend: false });
  assert.deepEqual(context.objects, before);
  assert.ok(context.calls.every((args) => args[1] === 'get-object'));
  const forward = context.delivery.acceptNormal(d, ancestor);
  assert.deepEqual(normalJobs(forward), { backend: true, frontend: true });
  assert.equal(JSON.parse(context.objects.get('last-normal.json')).codeSha, d);
});

test('accepted C survives backend failure before any frontend record; B cannot become an initial deployment', () => {
  const context = setup();
  const c = 'c'.repeat(40); const b = 'b'.repeat(40);
  assert.equal(context.delivery.acceptNormal(c, ancestor).deploy, true);
  assert.equal(context.objects.has('current.json'), false);
  assert.equal(context.objects.has('pending.json'), false);
  const restarted = createDelivery(config, context.aws);
  assert.deepEqual(normalJobs(restarted.acceptNormal(b, ancestor)), { backend: false, frontend: false });
  assert.equal(restarted.acceptNormal(c, ancestor).deploy, true, 'same accepted SHA remains retryable');
});

test('failed frontend C and rollback A preserve C even though current never reached C', () => {
  const context = setup();
  deploy(context);
  const c = 'c'.repeat(40);
  context.failWhen((args) => args[1] === 'wait');
  assert.throws(() => deploy(context, '101-1', c), /injected/);
  assert.equal(JSON.parse(context.objects.get('last-normal.json')).codeSha, c);
  context.failWhen(() => false);
  context.delivery.rollback({ targetId: '100-1', id: '102-1' });
  assert.equal(JSON.parse(context.objects.get('current.json')).public.codeSha, codeSha);
  assert.equal(context.objects.has('pending.json'), false);
  assert.deepEqual(normalJobs(context.delivery.acceptNormal('b'.repeat(40), ancestor)), { backend: false, frontend: false });
});

test('watermark read/write errors block acceptance; missing or corrupt state never falls back to active SHA', () => {
  const context = setup();
  context.failWhen((args) => args[1] === 'put-object' && args.includes('last-normal.json'));
  assert.throws(() => context.delivery.acceptNormal(codeSha, ancestor), /injected/);
  assert.equal(context.objects.size, 0);
  context.failWhen(() => false);
  deploy(context);
  context.failWhen((args) => args[1] === 'get-object' && args.includes('last-normal.json'));
  assert.throws(() => context.delivery.acceptNormal(codeSha, ancestor), /injected/);
  context.failWhen(() => false);
  context.objects.delete('last-normal.json');
  assert.throws(() => context.delivery.acceptNormal('b'.repeat(40), ancestor), /watermark is missing/);
  context.objects.set('last-normal.json', '{broken');
  assert.throws(() => context.delivery.acceptNormal(codeSha, ancestor), SyntaxError);
  context.objects.set('last-normal.json', JSON.stringify({ version: 2, codeSha }));
  assert.throws(() => context.delivery.acceptNormal(codeSha, ancestor), /Invalid normal/);
  context.objects.set('last-normal.json', JSON.stringify({ version: 1, codeSha: 'invalid' }));
  assert.throws(() => context.delivery.acceptNormal(codeSha, ancestor));
});

test('normal build and publish require accepted SHA, and inconsistent active generations fail closed', () => {
  const context = setup();
  assert.throws(() => context.delivery.resolveCode('deploy', codeSha), /accepted watermark/);
  assert.throws(() => context.delivery.publish({ action: 'deploy', id: '100-1', codeSha,
    publicRoot: context.publicRoot, adminRoot: context.adminRoot }), /accepted watermark/);
  const record = deploy(context);
  assert.throws(() => context.delivery.resolveCode('deploy', 'b'.repeat(40)), /accepted watermark/);
  const inconsistent = { ...record, admin: { ...record.admin, codeSha: 'b'.repeat(40) } };
  context.objects.set('current.json', JSON.stringify(inconsistent));
  assert.throws(() => context.delivery.acceptNormal('c'.repeat(40), ancestor), /generations differ/);
  context.objects.set('current.json', JSON.stringify(record));
  context.objects.set('last-normal.json', JSON.stringify({ version: 1, codeSha: '0'.repeat(40) }));
  assert.throws(() => context.delivery.acceptNormal('c'.repeat(40), ancestor), /outside normal/);
});

const rebuild = (context, id = '101-1', metadata = context.buildMetadata()) => context.delivery.publish({
  action: 'rebuild-public', id, codeSha, publicRoot: context.publicRoot, buildMetadata: metadata,
});
const liveWrites = (context) => context.calls.filter((args) => args[1] === 'sync' && /^s3:\/\/(public-site|admin-site)\//.test(args[3]));

test('saved generation and delivered generation differ after editing, then converge after rebuild', () => {
  const context = setup();
  assert.deepEqual(context.delivery.status(), { savedGeneration: context.generation(), deliveredGeneration: null,
    codeSha: null, pending: false, needsRebuild: true });
  const current = deploy(context);
  assert.equal(context.delivery.status().needsRebuild, false);
  context.update();
  const status = context.delivery.status();
  assert.equal(status.deliveredGeneration, current.public.generation);
  assert.equal(status.savedGeneration, context.generation());
  assert.equal(status.needsRebuild, true);
  const result = rebuild(context);
  assert.equal(result.public.generation, context.generation());
  assert.equal(context.delivery.status().needsRebuild, false);
});

test('edit after SSG rejects obsolete files before archives or live writes; no pending is created', () => {
  const context = setup(); deploy(context);
  const metadata = context.buildMetadata(); context.update(); context.calls.length = 0;
  assert.throws(() => rebuild(context, '101-1', metadata), /changed since SSG/);
  assert.equal(context.objects.has('pending.json'), false);
  assert.equal(context.calls.some((args) => args[1] === 'sync'), false);
});

test('missing metadata, altered bytes and wrong source SHA cannot claim a checked generation', () => {
  const context = setup(); deploy(context);
  const metadata = context.buildMetadata();
  writeFileSync(join(context.publicRoot, 'index.html'), 'tampered');
  assert.throws(() => rebuild(context, '101-1', metadata), /artifacts differ/);
  assert.throws(() => rebuild(context, '101-1', { ...context.buildMetadata(), codeSha: 'b'.repeat(40) }), /Build code differs/);
  assert.throws(() => rebuild(context, '101-1', {}), /Missing public build/);
  assert.equal(context.objects.has('pending.json'), false);
});

test('edit during archive fails the final pre-copy check without changing current or live buckets', () => {
  const context = setup(); const current = deploy(context); context.calls.length = 0;
  context.failWhen((args) => {
    if (args[1] === 'sync' && args[3].startsWith('s3://release-records/')) context.update();
    return false;
  });
  assert.throws(() => rebuild(context), /Public data changed/);
  assert.equal(liveWrites(context).length, 0);
  assert.equal(context.objects.has('pending.json'), false);
  assert.deepEqual(JSON.parse(context.objects.get('current.json')), current);
});

test('edit during delivery leaves pending, rejects obsolete rollback and recovers with current data', () => {
  const context = setup(); const current = deploy(context);
  writeFileSync(join(context.publicRoot, 'new.html'), 'new page');
  context.failWhen((args) => { if (args[1] === 'wait') context.update(); return false; });
  assert.throws(() => rebuild(context), /Public data changed/);
  assert.deepEqual(JSON.parse(context.objects.get('current.json')), current);
  assert.equal(context.delivery.status().pending, true);
  assert.equal(context.delivery.status().needsRebuild, true);
  assert.throws(() => context.delivery.resolveCode('rebuild-public'), /incomplete/);
  context.calls.length = 0;
  assert.throws(() => context.delivery.rollback({ targetId: '100-1', id: '102-1' }), /obsolete/);
  assert.equal(liveWrites(context).length, 0);
  assert.equal(context.delivery.resolveCode('recover'), codeSha);
  context.failWhen(() => false);
  rmSync(join(context.publicRoot, 'new.html')); rmSync(join(context.publicRoot, 'articles'), { recursive: true });
  const invalidations = [];
  context.failWhen((args) => {
    if (args[1] === 'create-invalidation') invalidations.push(...JSON.parse(readFileSync(args[args.indexOf('--invalidation-batch') + 1].slice(7))).Paths.Items);
    return false;
  });
  context.delivery.publish({ action: 'recover', id: '103-1', codeSha, publicRoot: context.publicRoot,
    adminRoot: context.adminRoot, buildMetadata: context.buildMetadata() });
  assert.ok(invalidations.includes('/new.html') && invalidations.includes('/articles/old/'));
  assert.equal(context.objects.has('pending.json'), false);
  assert.equal(context.delivery.status().needsRebuild, false);
});

test('initial incomplete deployment can be rebuilt after data changes without an older current record', () => {
  const context = setup(); context.failWhen((args) => args[1] === 'wait');
  assert.throws(() => deploy(context), /injected/);
  context.update(); context.failWhen(() => false);
  assert.equal(context.objects.has('current.json'), false);
  assert.equal(context.delivery.resolveCode('recover'), codeSha);
  context.delivery.publish({ action: 'recover', id: '101-1', codeSha, publicRoot: context.publicRoot,
    adminRoot: context.adminRoot, buildMetadata: context.buildMetadata() });
  assert.equal(context.delivery.status().needsRebuild, false);
});

test('legacy manifests remain readable for migration but cannot be restored as generation-checked content', () => {
  const context = setup(); const current = deploy(context);
  const legacy = { ...current, version: 1, public: { ...current.public } }; delete legacy.public.generation;
  context.objects.set('current.json', JSON.stringify(legacy));
  context.objects.set('manifests/90-1.json', JSON.stringify(legacy));
  assert.equal(context.delivery.status().needsRebuild, true);
  assert.equal(context.delivery.resolveCode('rebuild-public'), codeSha);
  context.calls.length = 0;
  assert.throws(() => context.delivery.rollback({ targetId: '90-1', id: '101-1' }), /Legacy/);
  assert.equal(liveWrites(context).length, 0);
  assert.equal(rebuild(context).version, 2);
});

test('unavailable generation API fails closed before copy and after invalidation', () => {
  const context = setup(); deploy(context);
  const unavailable = createDelivery(config, context.aws, () => { throw new Error('generation API unavailable'); });
  context.calls.length = 0;
  assert.throws(() => unavailable.rollback({ targetId: '100-1', id: '101-1' }), /API unavailable/);
  assert.equal(liveWrites(context).length, 0);
  let online = true;
  const interrupted = createDelivery(config, context.aws, () => {
    assert.ok(online, 'generation API unavailable'); return context.generation();
  });
  context.failWhen((args) => { if (args[1] === 'wait') online = false; return false; });
  assert.throws(() => interrupted.publish({ action: 'rebuild-public', id: '102-1', codeSha,
    publicRoot: context.publicRoot, buildMetadata: context.buildMetadata() }), /API unavailable/);
  assert.equal(context.objects.has('pending.json'), true);
});

test('repeated failed recovery retains all earlier possibly cached paths for final invalidation', () => {
  const context = setup();
  context.failWhen((args) => args[1] === 'wait');
  assert.throws(() => deploy(context));
  rmSync(join(context.publicRoot, 'articles'), { recursive: true });
  writeFileSync(join(context.publicRoot, 'middle.html'), 'middle');
  assert.throws(() => context.delivery.publish({ action: 'recover', id: '101-1', codeSha,
    publicRoot: context.publicRoot, adminRoot: context.adminRoot, buildMetadata: context.buildMetadata() }));
  rmSync(join(context.publicRoot, 'middle.html'));
  const paths = [];
  context.failWhen((args) => {
    if (args[1] === 'create-invalidation') paths.push(...JSON.parse(readFileSync(args[args.indexOf('--invalidation-batch') + 1].slice(7))).Paths.Items);
    return false;
  });
  context.delivery.publish({ action: 'recover', id: '102-1', codeSha,
    publicRoot: context.publicRoot, adminRoot: context.adminRoot, buildMetadata: context.buildMetadata() });
  assert.ok(paths.includes('/articles/old/') && paths.includes('/middle.html'));
});
