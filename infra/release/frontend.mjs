import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdtempSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { listeningFiles } from './listening-artifacts.mjs';
import { readGenerationSync, validateGeneration } from './generation.mjs';

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
  assert.ok([1, 2, 3].includes(record.version), 'Invalid release manifest version');
  if (record.version >= 2) validateGeneration(record.public.generation);
  if (record.version === 3) assert.ok(record.listening === null || (record.listening && typeof record.listening === 'object'), 'Missing listening release state');
  if (record.version < 3) assert.ok(!Object.hasOwn(record, 'listening'), 'Legacy manifest cannot carry listening');
  ['public', 'admin', ...(record.listening ? ['listening'] : [])].forEach((site) => {
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
    if (site === 'listening') {
      assert.equal(entry.codeSha, record.admin.codeSha, 'Listening/admin code generations differ');
      assert.ok(entry.files.some((file) => file.path === 'sw.js'));
      assert.ok(entry.files.every((file) => /^(?:index\.html|sw\.js|releases\/[a-f0-9]{64}\/(?:index\.html|assets\/[a-zA-Z0-9_.\/-]+))$/.test(file.path)), 'Unexpected listening artifact');
    }
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
    const key = `${site === 'admin' ? '/admin/' : site === 'listening' ? '/offline-player/' : '/'}${path}`;
    const directory = path.endsWith('index.html') ? key.slice(0, -'index.html'.length) : null;
    return [key, ...(directory === null ? [] : [directory, ...(directory === '/' ? [] : [directory.slice(0, -1)])])];
  }))].sort();

export const createDelivery = (config, aws = execute, generation = () => readGenerationSync(validSite(config.apiBaseUrl))) => {
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
  const clean = () => assert.equal(read('pending.json'), null, 'Previous delivery is incomplete. Use recover or a same-generation rollback.');
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
  const recoveryTarget = () => {
    const pending = read('pending.json');
    assert.ok(pending, 'No incomplete delivery to recover');
    const target = validateManifest(pending.target);
    assert.equal(target.public.codeSha, target.admin.codeSha, 'Recovery requires matching public/admin code');
    return target;
  };
  const resolveCode = (action, requestedSha) => {
    assert.ok(['deploy', 'rebuild-public', 'recover'].includes(action));
    if (action === 'recover') return recoveryTarget().public.codeSha;
    clean();
    const deployed = current();
    assert.ok(action !== 'rebuild-public' || deployed, 'No published release to rebuild');
    if (action === 'deploy') requireAccepted(sha(requestedSha));
    return action === 'rebuild-public' ? deployed.public.codeSha : sha(requestedSha);
  };
  const archive = (site, root, id, codeSha) => {
    const files = filesUnder(root).map((path) => ({ path, sha256: digest(join(root, path)) }));
    assert.ok(files.some((file) => file.path === 'index.html'), `${site}: missing index.html`);
    assert.ok(site === 'listening' || files.some((file) => file.path === '404.html'), `Missing ${site} 404.html`);
    assert.ok(site !== 'public' || files.every((file) => !/^(admin|assets|api|offline-player)(\/|$)/.test(file.path)), 'Public artifacts overlap another origin');
    if (site === 'listening') assert.deepEqual(files.map((file) => file.path), listeningFiles(root), 'Unexpected listening build files');
    const entry = { releaseId: id, codeSha, files };
    call('s3', 'sync', root, `s3://${config.releaseBucket}/releases/${id}/${site}/`, '--only-show-errors');
    return entry;
  };
  const transfer = (site, root) => {
    if (site === 'listening') {
      const destination = `s3://${config.adminBucket}/offline-player/`;
      if (!root) {
        call('s3', 'rm', destination, '--recursive', '--only-show-errors');
        return;
      }
      // Keep immutable old shells for already-open documents; switch the worker last.
      call('s3', 'sync', join(root, 'releases'), `${destination}releases/`, '--cache-control', 'public,max-age=31536000,immutable', '--only-show-errors');
      for (const file of ['index.html', 'sw.js']) call('s3', 'cp', join(root, file), `${destination}${file}`, '--cache-control', 'no-cache', '--content-type', file === 'sw.js' ? 'text/javascript' : 'text/html', '--only-show-errors');
      return;
    }
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
    assert.ok(record.version >= 2, 'Legacy artifacts have no public data generation; rebuild current data');
    const requireCurrentData = () => assert.equal(validateGeneration(generation()), record.public.generation,
      'Public data changed; rebuild current data (use recover when pending exists)');
    requireCurrentData();
    // Record intent BEFORE the first live write. Failure leaves a visible block
    // instead of silently treating partly copied files as a deployed code SHA.
    write('pending.json', { id, previous, target: record, sites });
    requireCurrentData();
    sites.forEach((site) => transfer(site, roots[site]));
    invalidate(sites.flatMap((site) => site === 'listening'
      ? ['/offline-player', '/offline-player/*']
      : invalidationPaths([previous?.[site], record[site]], site)), id);
    requireCurrentData();
    write('current.json', record);
    call('s3api', 'delete-object', '--bucket', config.releaseBucket, '--key', 'pending.json');
    return record;
  };
  const affectedPrevious = (previous, pending) => pending ? Object.fromEntries(['public', 'admin', 'listening'].map((site) => [site, {
    files: [...(previous?.[site]?.files ?? []), ...(pending.previous?.[site]?.files ?? []),
      ...(validateManifest(pending.target)[site]?.files ?? [])],
  }])) : previous;
  const publish = ({ action, codeSha, id, publicRoot, adminRoot, listeningRoot, buildMetadata }) => {
    releaseId(id); sha(codeSha);
    if (action !== 'recover') clean();
    if (action === 'recover') assert.equal(recoveryTarget().public.codeSha, codeSha, 'Recovery must use incomplete target code');
    const previous = current();
    assert.ok(['deploy', 'rebuild-public', 'recover'].includes(action));
    if (action === 'deploy') requireAccepted(codeSha);
    assert.ok(action !== 'rebuild-public' || previous?.public.codeSha === codeSha, 'Rebuild must use the currently deployed public SHA');
    assert.equal(read(`manifests/${id}.json`), null, 'Release ID already exists');
    const sites = action === 'rebuild-public' ? ['public'] : ['public', 'admin', 'listening'];
    const roots = { public: resolve(publicRoot), admin: adminRoot ? resolve(adminRoot) : null, listening: listeningRoot ? resolve(listeningRoot) : null };
    // Validate all builds before writing any live origin.
    if (roots.listening) assert.deepEqual(filesUnder(roots.listening), listeningFiles(roots.listening), 'Unexpected listening build files');
    sites.filter((site) => site !== 'listening').forEach((site) => ['index.html', '404.html'].forEach((file) =>
      assert.ok(filesUnder(roots[site]).includes(file), `Missing ${site} ${file}`)));
    assert.equal(buildMetadata?.version, 1, 'Missing public build generation record');
    assert.equal(buildMetadata.codeSha, codeSha, 'Build code differs from selected code');
    validateGeneration(buildMetadata.generation);
    assert.deepEqual(buildMetadata.files, filesUnder(roots.public).map((path) => ({ path, sha256: digest(join(roots.public, path)) })),
      'Public artifacts differ from generation-checked build');
    assert.equal(validateGeneration(generation()), buildMetadata.generation, 'Public data changed since SSG; rebuild current data');
    const entries = Object.fromEntries(sites.map((site) => [site, roots[site] ? archive(site, roots[site], id, codeSha) : null]));
    assert.deepEqual(entries.public.files, buildMetadata.files, 'Public artifacts changed while archiving');
    const record = validateManifest({ listening: previous?.listening ?? null, ...previous, ...entries,
      public: { ...entries.public, generation: buildMetadata.generation }, version: 3, deliveredAt: new Date().toISOString() });
    write(`manifests/${id}.json`, record);
    return complete(affectedPrevious(previous, read('pending.json')), record, sites, roots, id);
  };
  const rollback = ({ targetId, id }) => {
    releaseId(targetId); releaseId(id);
    const record = validateManifest(read(`manifests/${targetId}.json`));
    assert.ok(record.version >= 2, 'Legacy artifacts have no public data generation; rebuild current data');
    assert.equal(validateGeneration(generation()), record.public.generation, 'Archived public data is obsolete; rebuild current data');
    ['public', 'admin'].forEach((site) => assert.ok(record[site].files.some((file) => file.path === '404.html'),
      `Archived ${site} has no 404.html; deploy a build with static error pages`));
    const previous = current();
    const pending = read('pending.json');
    const roots = Object.fromEntries(['public', 'admin', 'listening'].map((site) => {
      const entry = record[site];
      if (!entry) return [site, null];
      const root = join(directory, site);
      call('s3', 'sync', `s3://${config.releaseBucket}/releases/${entry.releaseId}/${site}/`, root, '--only-show-errors');
      assert.deepEqual(filesUnder(root), entry.files.map((file) => file.path).sort(), 'Archive files differ from manifest');
      entry.files.forEach((file) => assert.equal(digest(join(root, file.path)), file.sha256, `Archive checksum mismatch: ${site}/${file.path}`));
      if (site === 'listening') assert.deepEqual(filesUnder(root), listeningFiles(root));
      return [site, root];
    }));
    // A failed target may have introduced cached paths absent from current.json.
    const invalidatedPrevious = affectedPrevious(previous, pending);
    return complete(invalidatedPrevious, record, ['public', 'admin', 'listening'], roots, id);
  };
  const status = () => {
    const savedGeneration = validateGeneration(generation());
    const active = current();
    const deliveredGeneration = active?.version >= 2 ? active.public.generation : null;
    const pending = read('pending.json') !== null;
    return { savedGeneration, deliveredGeneration, codeSha: active?.public.codeSha ?? null,
      pending, needsRebuild: pending || savedGeneration !== deliveredGeneration };
  };
  const recordCandidate = (record, id, target) => {
    releaseId(id); sha(target);
    assert.equal(record.version, 1);
    assert.equal(record.codeSha, target);
    assert.match(record.repository ?? '', /^[\w.-]+\/[\w.-]+$/);
    assert.match(record.branch ?? '', /^release\/[0-9]+\.[0-9]+(?:\.[0-9]+)?$/);
    assert.match(record.tag ?? '', /^v[0-9]+\.[0-9]+\.[0-9]+$/);
    assert.match(record.ciRunId ?? '', /^[1-9][0-9]*$/);
    assert.ok(Number.isSafeInteger(record.ciAttempt) && record.ciAttempt > 0);
    assert.equal(read(`candidates/${id}.json`), null, 'Candidate evidence already exists');
    write(`candidates/${id}.json`, { version: 1, codeSha: target, repository: record.repository,
      branch: record.branch, tag: record.tag, ciRunId: record.ciRunId, ciAttempt: record.ciAttempt });
  };
  return { preflight, acceptNormal, resolveCode, publish, rollback, status, recordCandidate };
};

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const env = process.env;
  const delivery = createDelivery({ publicBucket: env.FRONTEND_PUBLIC_BUCKET, adminBucket: env.FRONTEND_ADMIN_BUCKET,
    releaseBucket: env.FRONTEND_RELEASE_BUCKET, distributionId: env.CLOUDFRONT_DISTRIBUTION_ID,
    apiBaseUrl: env.API_BASE_URL });
  const [command, action] = process.argv.slice(2);
  const operations = {
    'record-candidate': () => delivery.recordCandidate(JSON.parse(readFileSync('candidate.json', 'utf8')), env.RELEASE_ID, env.RELEASE_SHA),
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
      publicRoot: 'source/frontend-public/dist', adminRoot: 'source/frontend-admin/dist',
      listeningRoot: action === 'rebuild-public' ? undefined : 'source/listening-release',
      buildMetadata: JSON.parse(readFileSync('source/public-build.json', 'utf8')) }),
    rollback: () => delivery.rollback({ targetId: env.TARGET_RELEASE_ID, id: env.RELEASE_ID }),
    status: () => {
      validSite(env.API_BASE_URL);
      const record = delivery.status();
      console.log(JSON.stringify(record, null, 2));
      writeFileSync(env.GITHUB_STEP_SUMMARY, `Public data status (observed now):\n\n\`\`\`json\n${JSON.stringify(record, null, 2)}\n\`\`\`\n`, { flag: 'a' });
    },
  };
  assert.ok(Object.hasOwn(operations, command), 'Unknown release command');
  operations[command]();
}
