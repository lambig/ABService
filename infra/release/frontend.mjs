import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdtempSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// The archive is private. Only the two live buckets are CloudFront origins.
const execute = (args) => execFileSync('aws', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
const digest = (file) => createHash('sha256').update(readFileSync(file)).digest('hex');
export const filesUnder = (root, prefix = '') => readdirSync(join(root, prefix), { withFileTypes: true })
  .flatMap((entry) => {
    const path = `${prefix}${entry.name}`;
    assert.ok(!entry.isSymbolicLink(), `Symlink is not a release artifact: ${path}`);
    return entry.isDirectory() ? filesUnder(root, `${path}/`) : [path];
  }).sort();
const sha = (value) => { assert.match(value ?? '', /^[a-f0-9]{40}$/); return value; };
const releaseId = (value) => { assert.match(value ?? '', /^[0-9]+-[0-9]+$/); return value; };
const validSite = (value) => {
  const url = new URL(value);
  assert.ok(url.protocol === 'https:' && !url.username && !url.password && url.pathname === '/' && !url.search && !url.hash,
    'Site/API base must be an HTTPS origin');
  return url.origin;
};
export const validateManifest = (record) => {
  assert.equal(record.version, 1);
  ['public', 'admin'].forEach((site) => {
    const entry = record[site];
    sha(entry.codeSha); releaseId(entry.releaseId);
    assert.ok(Array.isArray(entry.files) && entry.files.length > 0);
    assert.equal(new Set(entry.files.map((file) => file.path)).size, entry.files.length);
    entry.files.forEach((file) => {
      assert.match(file.path, /^(?!\/)(?!.*(?:^|\/)\.\.?\/)[a-zA-Z0-9_.\/-]+$/);
      assert.ok(!file.path.endsWith('/') && !file.path.split('/').some((part) => ['.', '..', ''].includes(part)));
      assert.match(file.sha256, /^[a-f0-9]{64}$/);
    });
    assert.ok(entry.files.some((file) => file.path === 'index.html'));
  });
  return record;
};

export const gitAncestor = (ancestor, descendant, cwd) => {
  sha(ancestor); sha(descendant);
  try {
    execFileSync('git', ['merge-base', '--is-ancestor', ancestor, descendant], { cwd, stdio: 'pipe' });
    return true;
  } catch (error) {
    if (error.status === 1) return false;
    throw new Error('Cannot compare deployment commits: missing history or Git failure');
  }
};

// Called inside the release lock. Queue order is CI completion order, not commit order.
export const deploymentDecision = (previous, target, isAncestor = gitAncestor) => {
  sha(target);
  if (previous !== null) sha(previous);
  const deploy = previous === null || previous === target || isAncestor(previous, target);
  assert.ok(deploy || isAncestor(target, previous), 'Deployment history diverged; use an explicit recovery procedure');
  return { deploy, previous, target };
};

// Invalidate original viewer paths AND rewritten S3 keys. Do not invalidate
// /admin or /assets when refreshing only public content.
export const invalidationPaths = (entries, site) => [...new Set(entries.flatMap((entry) => entry?.files ?? [])
  .flatMap(({ path }) => {
    const key = `${site === 'admin' ? '/admin/' : '/'}${path}`;
    const directory = path.endsWith('index.html') ? key.slice(0, -'index.html'.length) : null;
    return [key, ...(directory === null ? [] : [directory, ...(directory === '/' ? [] : [directory.slice(0, -1)])])];
  }))].sort();

export const createDelivery = (config, aws = execute) => {
  [config.publicBucket, config.adminBucket, config.releaseBucket].forEach((bucket) => assert.match(bucket ?? '', /^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$/));
  assert.equal(new Set([config.publicBucket, config.adminBucket, config.releaseBucket]).size, 3, 'Release/live buckets must differ');
  assert.match(config.distributionId ?? '', /^[A-Z0-9]+$/);
  const directory = mkdtempSync(join(tmpdir(), 'abservice-release-'));
  const call = (...args) => aws([...args, '--no-cli-pager']);
  const read = (key) => {
    const target = join(directory, 'read.json');
    try {
      call('s3api', 'get-object', '--bucket', config.releaseBucket, '--key', key, target);
      return JSON.parse(readFileSync(target, 'utf8'));
    } catch (error) {
      // AccessDenied, network failure and malformed JSON are not "first deploy".
      if (/\(NoSuchKey\)|\(404\)/.test(String(error.stderr ?? ''))) return null;
      throw error;
    }
  };
  const write = (key, record) => {
    const target = join(directory, 'write.json');
    writeFileSync(target, JSON.stringify(record, null, 2));
    call('s3api', 'put-object', '--bucket', config.releaseBucket, '--key', key, '--body', target, '--content-type', 'application/json');
  };
  const current = () => { const value = read('current.json'); return value === null ? null : validateManifest(value); };
  const clean = () => assert.equal(read('pending.json'), null, 'Previous delivery is incomplete. Restore a recorded release before rebuilding.');
  // Called under the workflow's release lock, before any backend build/deploy.
  // Missing current is valid for an initial deployment; unreadable/corrupt state is not.
  const preflight = () => { clean(); return current(); };
  const lastNormal = () => {
    const record = read('last-normal.json');
    if (record === null) return null;
    assert.equal(record.version, 1, 'Invalid normal deployment watermark');
    return sha(record.codeSha);
  };
  const acceptNormal = (target, isAncestor = gitAncestor) => {
    const active = preflight();
    const previous = lastNormal();
    // Never seed a lost watermark from active artifacts: they may be rolled back.
    assert.ok(active === null || previous !== null, 'Normal deployment watermark is missing; restore release state');
    assert.ok(active === null || active.public.codeSha === active.admin.codeSha,
      'Public/admin code generations differ; recover before normal deployment');
    assert.ok(active === null || active.public.codeSha === previous || isAncestor(active.public.codeSha, previous),
      'Active release is outside normal deployment history; restore release state');
    const decision = deploymentDecision(previous, target, isAncestor);
    // Reserve before backend can run, including when backend/frontend later fails.
    // Manual rollback/rebuild never writes this object. The workflow owns the lock.
    if (decision.deploy && previous !== target) write('last-normal.json', { version: 1, codeSha: target });
    return decision;
  };
  const requireAccepted = (target) => assert.equal(lastNormal(), target, 'Normal deployment requires its accepted watermark');
  const resolveCode = (action, requestedSha) => {
    assert.ok(['deploy', 'rebuild-public'].includes(action));
    clean();
    const deployed = current();
    assert.ok(action !== 'rebuild-public' || deployed, 'No published release to rebuild');
    if (action === 'deploy') requireAccepted(sha(requestedSha));
    return action === 'rebuild-public' ? deployed.public.codeSha : sha(requestedSha);
  };
  const archive = (site, root, id, codeSha) => {
    const files = filesUnder(root).map((path) => ({ path, sha256: digest(join(root, path)) }));
    assert.ok(files.some((file) => file.path === 'index.html'), `${site}: missing index.html`);
    assert.ok(site !== 'public' || files.some((file) => file.path === '404.html'), 'Missing public 404.html');
    assert.ok(site !== 'public' || files.every((file) => !/^(admin|assets|api)(\/|$)/.test(file.path)), 'Public artifacts overlap another origin');
    const entry = { releaseId: id, codeSha, files };
    call('s3', 'sync', root, `s3://${config.releaseBucket}/releases/${id}/${site}/`, '--only-show-errors');
    return entry;
  };
  const transfer = (site, root) => {
    const destination = `s3://${site === 'public' ? config.publicBucket : config.adminBucket}/${site === 'admin' ? 'admin/' : ''}`;
    // Upload dependencies before HTML. The final sync removes withdrawn pages.
    call('s3', 'sync', root, destination, '--exclude', '*', '--include', '_astro/*', '--cache-control', 'public,max-age=31536000,immutable', '--only-show-errors');
    call('s3', 'sync', root, destination, '--exclude', '_astro/*', '--delete', '--cache-control', 'public,max-age=0,must-revalidate', '--only-show-errors');
    // Old content-hashed assets remain so already-open documents keep working.
  };
  const invalidate = (paths, id) => {
    const groups = Array.from({ length: Math.ceil(paths.length / 1000) }, (_, index) => paths.slice(index * 1000, (index + 1) * 1000));
    groups.forEach((group, index) => {
      const batch = join(directory, 'invalidation.json');
      writeFileSync(batch, JSON.stringify({ CallerReference: `${id}-${index}`, Paths: { Quantity: group.length, Items: group } }));
      const result = JSON.parse(call('cloudfront', 'create-invalidation', '--distribution-id', config.distributionId, '--invalidation-batch', `file://${batch}`));
      call('cloudfront', 'wait', 'invalidation-completed', '--distribution-id', config.distributionId, '--id', result.Invalidation.Id);
    });
  };
  const complete = (previous, record, sites, roots, id) => {
    // Record intent BEFORE the first live write. Failure leaves a visible block
    // instead of silently treating partly copied files as a deployed code SHA.
    write('pending.json', { id, previous, target: record, sites });
    sites.forEach((site) => transfer(site, roots[site]));
    invalidate(sites.flatMap((site) => invalidationPaths([previous?.[site], record[site]], site)), id);
    write('current.json', record);
    call('s3api', 'delete-object', '--bucket', config.releaseBucket, '--key', 'pending.json');
    return record;
  };
  const publish = ({ action, codeSha, id, publicRoot, adminRoot }) => {
    releaseId(id); sha(codeSha);
    clean();
    const previous = current();
    assert.ok(['deploy', 'rebuild-public'].includes(action));
    if (action === 'deploy') requireAccepted(codeSha);
    assert.ok(action !== 'rebuild-public' || previous?.public.codeSha === codeSha, 'Rebuild must use the currently deployed public SHA');
    assert.equal(read(`manifests/${id}.json`), null, 'Release ID already exists');
    const sites = action === 'deploy' ? ['public', 'admin'] : ['public'];
    const roots = { public: resolve(publicRoot), admin: adminRoot ? resolve(adminRoot) : null };
    // Validate both builds before writing either live origin.
    sites.forEach((site) => assert.ok(filesUnder(roots[site]).includes('index.html')));
    const entries = Object.fromEntries(sites.map((site) => [site, archive(site, roots[site], id, codeSha)]));
    const record = validateManifest({ ...previous, ...entries, version: 1, deliveredAt: new Date().toISOString() });
    write(`manifests/${id}.json`, record);
    return complete(previous, record, sites, roots, id);
  };
  const rollback = ({ targetId, id }) => {
    releaseId(targetId); releaseId(id);
    const record = validateManifest(read(`manifests/${targetId}.json`));
    const previous = current();
    const pending = read('pending.json');
    const roots = Object.fromEntries(['public', 'admin'].map((site) => {
      const entry = record[site];
      const root = join(directory, site);
      call('s3', 'sync', `s3://${config.releaseBucket}/releases/${entry.releaseId}/${site}/`, root, '--only-show-errors');
      assert.deepEqual(filesUnder(root), entry.files.map((file) => file.path).sort(), 'Archive files differ from manifest');
      entry.files.forEach((file) => assert.equal(digest(join(root, file.path)), file.sha256, `Archive checksum mismatch: ${site}/${file.path}`));
      return [site, root];
    }));
    // A failed target may have introduced cached paths absent from current.json.
    const invalidatedPrevious = pending ? Object.fromEntries(['public', 'admin'].map((site) => [site, {
      files: [...(previous?.[site]?.files ?? []), ...(validateManifest(pending.target)[site].files)],
    }])) : previous;
    return complete(invalidatedPrevious, record, ['public', 'admin'], roots, id);
  };
  return { preflight, acceptNormal, resolveCode, publish, rollback };
};

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const env = process.env;
  const delivery = createDelivery({ publicBucket: env.FRONTEND_PUBLIC_BUCKET, adminBucket: env.FRONTEND_ADMIN_BUCKET,
    releaseBucket: env.FRONTEND_RELEASE_BUCKET, distributionId: env.CLOUDFRONT_DISTRIBUTION_ID });
  const [command, action] = process.argv.slice(2);
  const operations = {
    preflight: () => {
      const decision = delivery.acceptNormal(env.RELEASE_SHA);
      writeFileSync(env.GITHUB_OUTPUT, `deploy=${decision.deploy}\n`, { flag: 'a' });
      const message = decision.deploy
        ? `Forward deployment allowed: ${decision.previous ?? 'initial'} -> ${decision.target}`
        : `Skipped stale successful CI: ${decision.target}; normal deployment watermark remains ${decision.previous}. Backend/frontend were not deployed.`;
      console.log(message);
      writeFileSync(env.GITHUB_STEP_SUMMARY, `${message}\n`, { flag: 'a' });
    },
    resolve: () => {
      validSite(env.PUBLIC_SITE_URL); validSite(env.API_BASE_URL);
      const codeSha = delivery.resolveCode(action, env.RELEASE_SHA);
      writeFileSync(env.GITHUB_OUTPUT, `code_sha=${codeSha}\n`, { flag: 'a' });
    },
    publish: () => delivery.publish({ action, codeSha: env.RELEASE_SHA, id: env.RELEASE_ID,
      publicRoot: 'source/frontend-public/dist', adminRoot: 'source/frontend-admin/dist' }),
    rollback: () => delivery.rollback({ targetId: env.TARGET_RELEASE_ID, id: env.RELEASE_ID }),
  };
  assert.ok(Object.hasOwn(operations, command), 'Unknown release command');
  operations[command]();
}
