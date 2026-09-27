import assert from 'node:assert/strict';
import { appendFileSync, readFileSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// The controller's CI contract is authoritative. A partial rerun must not omit a required job.
const workflowSource = readFileSync(new URL('../../.github/workflows/ci.yml', import.meta.url), 'utf8').replaceAll('\r\n', '\n');
export const requiredJobs = [...workflowSource.matchAll(/^    name: (.+)\r?$/gm)].map((match) => match[1].trim());
assert.ok(requiredJobs.includes('CI gate'));
assert.equal(new Set(requiredJobs).size, requiredJobs.length);
assert.equal(requiredJobs.length, [...workflowSource.slice(workflowSource.indexOf('\njobs:\n') + 7).matchAll(/^  [\w-]+:\r?$/gm)].length,
  'Every CI job needs a unique static name; update the candidate contract when changing workflow structure');

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

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  assert.equal(process.env.GITHUB_REF, 'refs/heads/main', 'Run Deploy from main');
  assert.equal(process.env.GITHUB_EVENT_NAME, 'workflow_dispatch');
  const input = { repository: process.env.GITHUB_REPOSITORY, commit: process.env.COMMIT_SHA,
    branch: process.env.RELEASE_BRANCH, tag: process.env.RELEASE_TAG, runId: process.env.CI_RUN_ID };
  assert.match(input.repository ?? '', /^[\w.-]+\/[\w.-]+$/);
  assert.ok(process.env.GITHUB_TOKEN, 'Missing GitHub token');
  const read = async (path) => {
    const response = await fetch(`https://api.github.com/repos/${input.repository}/${path}`, {
      headers: { Authorization: `Bearer ${process.env.GITHUB_TOKEN}`, Accept: 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28' }, redirect: 'error', signal: AbortSignal.timeout(15000),
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
