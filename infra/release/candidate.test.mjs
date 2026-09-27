import assert from 'node:assert/strict';
import test from 'node:test';
import { requiredJobs, verifyCandidate } from './candidate.mjs';

const commit = 'a'.repeat(40);
const input = { repository: 'owner/repo', commit, branch: 'release/1.10', tag: 'v1.10.0', runId: '123' };
const fixture = () => ({
  'actions/workflows/ci.yml': { id: 42 },
  'actions/runs/123': { workflow_id: 42, path: '.github/workflows/ci.yml', repository: { full_name: 'owner/repo' },
    head_repository: { full_name: 'owner/repo' }, head_sha: commit, head_branch: input.branch,
    event: 'push', status: 'completed', conclusion: 'success', run_attempt: 2 },
  'actions/runs/123/attempts/2/jobs?per_page=100&page=1': { total_count: requiredJobs.length,
    jobs: requiredJobs.map((name) => ({ name, status: 'completed', conclusion: 'success',
      steps: [{ name: 'Run E2E', status: 'completed', conclusion: 'success' }] })) },
  'git/ref/heads/release/1.10': { object: { type: 'commit', sha: commit } },
  'git/ref/tags/v1.10.0': { object: { type: 'commit', sha: commit } },
});
const verify = (responses = fixture(), patch = {}, onMain = true) => verifyCandidate({ ...input, ...patch }, async (path) => {
  assert.ok(Object.hasOwn(responses, path), `Unexpected API path: ${path}`);
  return responses[path];
}, () => onMain);

test('successful stack acceptance cannot hide omitted Playwright in a release', async () => {
  const responses = fixture();
  responses['actions/runs/123/attempts/2/jobs?per_page=100&page=1'].jobs
    .find((job) => job.name === 'E2E (Playwright / 実スタック)').steps[0].conclusion = 'skipped';
  await assert.rejects(verify(responses), /must execute Playwright/);
});

test('release records pin commit, tag and exact successful CI attempt', async () => {
  assert.deepEqual(await verify(), { version: 1, repository: input.repository, codeSha: commit,
    branch: input.branch, tag: input.tag, ciRunId: '123', ciAttempt: 2 });
  const responses = fixture();
  responses['git/ref/tags/v1.10.0'].object = { type: 'tag', sha: 'b'.repeat(40) };
  responses[`git/tags/${'b'.repeat(40)}`] = { object: { type: 'commit', sha: commit } };
  assert.equal((await verify(responses)).codeSha, commit);
});

for (const [field, value] of [
  ['workflow_id', 43], ['path', '.github/workflows/other.yml'],
  ['repository', { full_name: 'other/repo' }], ['head_repository', { full_name: 'fork/repo' }],
  ['event', 'pull_request'], ['event', 'schedule'], ['head_branch', 'main'],
  ['head_sha', 'b'.repeat(40)], ['status', 'in_progress'], ['conclusion', 'failure'],
  ['conclusion', 'cancelled'], ['run_attempt', 0],
]) test(`rejects untrusted/incomplete CI: ${field}=${JSON.stringify(value)}`, async () => {
  const responses = fixture(); responses['actions/runs/123'][field] = value;
  await assert.rejects(verify(responses));
});

test('manual CI on a pre-migration hotfix candidate is accepted without a release push trigger', async () => {
  const responses = fixture();
  responses['actions/runs/123'].event = 'workflow_dispatch';
  const record = await verify(responses);
  assert.equal(record.codeSha, commit);
  assert.equal(record.branch, input.branch);
  assert.equal(record.ciRunId, '123');
  assert.equal(record.ciAttempt, 2);
});

test('manual CI retains branch, SHA, workflow, repository, job, tag and ancestry requirements', async () => {
  const manual = () => {
    const responses = fixture();
    responses['actions/runs/123'].event = 'workflow_dispatch';
    return responses;
  };
  for (const [field, value] of [
    ['head_branch', 'main'], ['head_branch', 'v1.10.0'], ['head_branch', 'release/1.11'],
    ['head_sha', 'b'.repeat(40)], ['workflow_id', 43], ['path', '.github/workflows/other.yml'],
    ['repository', { full_name: 'other/repo' }], ['head_repository', { full_name: 'fork/repo' }],
    ['status', 'in_progress'], ['conclusion', 'failure'], ['run_attempt', 0],
  ]) {
    const responses = manual(); responses['actions/runs/123'][field] = value;
    await assert.rejects(verify(responses));
  }
  for (const conclusion of ['skipped', 'failure', 'cancelled', null]) {
    const responses = manual();
    responses['actions/runs/123/attempts/2/jobs?per_page=100&page=1'].jobs[1].conclusion = conclusion;
    await assert.rejects(verify(responses));
  }
  const partial = manual();
  partial['actions/runs/123/attempts/2/jobs?per_page=100&page=1'] = {
    total_count: 1, jobs: [{ name: 'CI gate', status: 'completed', conclusion: 'success' }],
  };
  await assert.rejects(verify(partial), /Missing or duplicate CI job/);
  for (const path of ['git/ref/heads/release/1.10', 'git/ref/tags/v1.10.0']) {
    const responses = manual(); responses[path].object.sha = 'b'.repeat(40);
    await assert.rejects(verify(responses));
  }
  await assert.rejects(verify(manual(), {}, false));
});

test('missing, skipped or failed jobs cannot masquerade as complete release CI', async () => {
  for (const result of ['skipped', 'failure', 'cancelled', null]) {
    const responses = fixture();
    responses['actions/runs/123/attempts/2/jobs?per_page=100&page=1'].jobs[1].conclusion = result;
    await assert.rejects(verify(responses));
  }
  const responses = fixture();
  responses['actions/runs/123/attempts/2/jobs?per_page=100&page=1'].jobs = [];
  await assert.rejects(verify(responses));
});

test('all pages of the pinned CI attempt are validated', async () => {
  const responses = fixture();
  responses['actions/runs/123/attempts/2/jobs?per_page=100&page=1'].jobs.pop();
  responses['actions/runs/123/attempts/2/jobs?per_page=100&page=2'] = {
    total_count: 2, jobs: [{ name: 'late failure', status: 'completed', conclusion: 'failure' }],
  };
  await assert.rejects(verify(responses));
});

test('moved/deleted branches and tags fail closed for new releases', async () => {
  for (const path of ['git/ref/heads/release/1.10', 'git/ref/tags/v1.10.0']) {
    const moved = fixture(); moved[path].object.sha = 'b'.repeat(40);
    await assert.rejects(verify(moved));
    const deleted = fixture(); delete deleted[path];
    await assert.rejects(verify(deleted));
  }
  await assert.rejects(verify(fixture(), {}, false));
  await assert.rejects(verifyCandidate(input, async () => { throw new Error('API unavailable'); }, () => true));
});

test('invalid ref, SHA and run inputs are rejected before use', async () => {
  for (const patch of [{ commit: 'main' }, { branch: 'main' }, { branch: 'release/../main' },
    { tag: 'v1.11.0' }, { tag: 'v1.10.0\nforged' }, { tag: 'v1.10.0\n' }, { commit: `${commit}\n` },
    { runId: '../123' }, { repository: '../repo' }]) {
    await assert.rejects(verify(fixture(), patch));
  }
});

test('a successful partial rerun cannot substitute for all release checks', async () => {
  const responses = fixture();
  responses['actions/runs/123/attempts/2/jobs?per_page=100&page=1'] = {
    total_count: 1, jobs: [{ name: 'CI gate', status: 'completed', conclusion: 'success' }],
  };
  await assert.rejects(verify(responses), /Missing or duplicate CI job/);
});
