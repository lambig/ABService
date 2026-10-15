import assert from 'node:assert/strict';
import { appendFileSync, readFileSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// The controller's CI contract is authoritative. A partial rerun must not omit a required job.
const workflowSource = readFileSync(new URL('../../.github/workflows/ci.yml', import.meta.url), 'utf8').replaceAll('\r\n', '\n');
// Only the workflow's current static jobs and single, literal OS matrix are supported.
// Reject new matrix shapes until their expansion is explicitly covered here.
export const requiredJobNames = (source) => {
  const jobs = source.replaceAll('\r\n', '\n').split('\njobs:\n');
  assert.equal(jobs.length, 2, 'Expected one CI jobs mapping');
  const blocks = [...jobs[1].matchAll(/^  [\w-]+:\n([\s\S]*?)(?=^  [\w-]+:|$(?![\s\S]))/gm)];
  assert.ok(blocks.length > 0, 'Missing CI jobs');
  return blocks.flatMap(([, block]) => {
    const names = [...block.matchAll(/^    name: (.+)$/gm)];
    assert.equal(names.length, 1, 'Every CI job needs one explicit name');
    const name = names[0][1].trim();
    const strategies = [...block.matchAll(/^    strategy:\n((?:^ {6,}[^\n]*\n|^\n)*)/gm)];
    if (strategies.length === 0) {
      assert.ok(!name.includes('${{'), 'Dynamic CI job names require a supported matrix');
      return [name];
    }
    assert.equal(strategies.length, 1, 'Expected one CI strategy');
    const strategy = strategies[0][1];
    const matrix = strategy.match(/^(?:      fail-fast: (?:true|false)\n)?      matrix:\n        os: \[([a-z0-9-]+(?:, [a-z0-9-]+)*)\]\n$/);
    assert.ok(matrix, 'Unsupported CI matrix; update the candidate contract');
    const platforms = matrix[1].split(', ');
    assert.equal(new Set(platforms).size, platforms.length, 'Duplicate CI matrix OS');
    assert.equal(name.split('${{ matrix.os }}').length, 2, 'Matrix name must identify its OS');
    const expanded = platforms.map((os) => name.replace('${{ matrix.os }}', os));
    assert.ok(expanded.every((value) => !value.includes('${{')), 'Unsupported dynamic CI name');
    return expanded;
  });
};
export const requiredJobs = requiredJobNames(workflowSource);
assert.ok(requiredJobs.includes('CI gate'));
assert.equal(new Set(requiredJobs).size, requiredJobs.length);

/** Validate a pinned release, using read-only repository APIs before obtaining AWS credentials. */
export const verifyCandidate = async (input, read, isOnMain) => {
  const { repository, commit, branch, tag, runId } = input;
  for (const value of [repository, commit, branch, tag, runId]) {
    assert.equal(typeof value, 'string');
    assert.equal(value.trim(), value, 'Whitespace is not valid in candidate inputs');
  }
  assert.match(repository ?? '', /^[\w.-]+\/[\w.-]+$/);
  assert.match(commit ?? '', /^[a-f0-9]{40}$/);
  assert.match(branch ?? '', /^release\/[0-9]+\.[0-9]+(?:\.[0-9]+)?$/);
  assert.match(tag ?? '', /^v[0-9]+\.[0-9]+\.[0-9]+$/);
  assert.ok(tag.slice(1) === branch.slice(8) || tag.slice(1).startsWith(`${branch.slice(8)}.`), 'Tag must belong to the release line');
  assert.match(runId ?? '', /^[1-9][0-9]*$/);
  const run = await read(`actions/runs/${runId}`);
  const workflow = await read('actions/workflows/ci.yml');
  assert.equal(run.workflow_id, workflow.id, 'Not the CI workflow');
  assert.equal(run.path, '.github/workflows/ci.yml');
  assert.equal(run.repository?.full_name, repository);
  assert.equal(run.head_repository?.full_name, repository, 'Fork CI is not release evidence');
  // Older production tags predate the release push trigger; dispatch CI on the candidate branch.
  assert.ok(['push', 'workflow_dispatch'].includes(run.event), 'Only candidate push/manual CI is release evidence');
  assert.equal(run.head_branch, branch);
  assert.equal(run.head_sha, commit);
  assert.equal(run.status, 'completed');
  assert.equal(run.conclusion, 'success');
  assert.ok(Number.isSafeInteger(run.run_attempt) && run.run_attempt > 0);
  const jobs = [];
  for (let page = 1; ; page += 1) {
    const response = await read(`actions/runs/${runId}/attempts/${run.run_attempt}/jobs?per_page=100&page=${page}`);
    assert.ok(Array.isArray(response.jobs));
    assert.ok(Number.isSafeInteger(response.total_count) && response.total_count > 0);
    jobs.push(...response.jobs);
    if (jobs.length >= response.total_count) break;
    assert.ok(response.jobs.length > 0 && page < 100, 'Incomplete CI job listing');
  }
  for (const name of requiredJobs) {
    assert.equal(jobs.filter((job) => job.name === name).length, 1, `Missing or duplicate CI job: ${name}; rerun all CI jobs`);
  }
  assert.ok(jobs.every((job) => job.status === 'completed' && job.conclusion === 'success'), 'Release CI must run every job successfully');
  const e2e = jobs.find((job) => job.name === 'E2E (Playwright / 実スタック)');
  assert.ok(e2e?.steps?.some((step) => step.name === 'Run E2E' && step.status === 'completed'
    && step.conclusion === 'success'), 'Release CI must execute Playwright, not only stack acceptance');
  const head = await read(`git/ref/heads/${branch}`);
  assert.equal(head.object?.type, 'commit');
  assert.equal(head.object.sha, commit, 'Release branch moved; validate a new candidate');
  let target = (await read(`git/ref/tags/${tag}`)).object;
  for (let depth = 0; target?.type === 'tag' && depth < 5; depth += 1) {
    assert.match(target.sha, /^[a-f0-9]{40}$/);
    target = (await read(`git/tags/${target.sha}`)).object;
  }
  assert.equal(target?.type, 'commit', 'Tag must resolve to a commit');
  assert.equal(target.sha, commit, 'Tag does not identify the tested candidate');
  assert.ok(await isOnMain(commit), 'Candidate must already be integrated into main');
  return { version: 1, repository, codeSha: commit, branch, tag, ciRunId: runId, ciAttempt: run.run_attempt };
};

// A separate operations repository has no token scoped to the public source.
// Read public evidence anonymously in that case; never fall back after an API error.
export const candidateApi = (env) => {
  const repository = env.SOURCE_REPOSITORY || env.GITHUB_REPOSITORY;
  assert.match(repository ?? '', /^[\w.-]+\/[\w.-]+$/);
  const headers = { Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28' };
  if (repository === env.GITHUB_REPOSITORY) {
    assert.ok(env.GITHUB_TOKEN, 'Missing GitHub token');
    headers.Authorization = `Bearer ${env.GITHUB_TOKEN}`;
  }
  return { repository, headers };
};

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  assert.equal(process.env.GITHUB_REF, 'refs/heads/main', 'Run Deploy from main');
  assert.equal(process.env.GITHUB_EVENT_NAME, 'workflow_dispatch');
  const api = candidateApi(process.env);
  const input = { repository: api.repository, commit: process.env.COMMIT_SHA,
    branch: process.env.RELEASE_BRANCH, tag: process.env.RELEASE_TAG, runId: process.env.CI_RUN_ID };
  assert.match(input.repository ?? '', /^[\w.-]+\/[\w.-]+$/);
  const read = async (path) => {
    const response = await fetch(`https://api.github.com/repos/${input.repository}/${path}`, {
      headers: api.headers, redirect: 'error', signal: AbortSignal.timeout(15000),
    });
    assert.ok(response.ok, `GitHub validation failed (${response.status})`);
    return response.json();
  };
  const record = await verifyCandidate(input, read, (commit) => {
    execFileSync('git', ['merge-base', '--is-ancestor', commit, 'origin/main'], { stdio: 'pipe' });
    return true;
  });
  writeFileSync('candidate.json', JSON.stringify(record, null, 2));
  appendFileSync(process.env.GITHUB_STEP_SUMMARY, `## Validated release candidate\n\n${record.tag}: ${record.codeSha}\n\nCI run ${record.ciRunId}, attempt ${record.ciAttempt}. This records validation, not deployment success.\n`);
}
