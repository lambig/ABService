import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import test from 'node:test';

// Evaluate actual workflow guards with event fixtures, not a second policy implementation.
const read = (name) => readFileSync(new URL(`../../.github/workflows/${name}.yml`, import.meta.url), 'utf8');
const deploy = read('deploy');
const frontend = read('deploy-frontend');
const job = (yaml, name) => yaml.split(`\n  ${name}:\n`)[1]?.split(/\n  [\w-]+:\n/)[0];
const guard = (block) => block.match(/    if: >-\n((?:      .*(?:\n|$))+)/)[1].trim();
const evaluate = (expression, context) => runInNewContext(expression.replace(/^\$\{\{\s*|\s*\}\}$/g, ''), {
  cancelled: () => false, format: (pattern, value) => pattern.replace('{0}', value), ...context,
}, { timeout: 1000 });
const normal = () => ({ github: { ref: 'refs/heads/main', event_name: 'workflow_run', repository: 'owner/repo',
  run_id: 101, run_attempt: 2, sha: 'c'.repeat(40), event: { workflow_run: { conclusion: 'success',
    head_branch: 'main', event: 'push', head_repository: { full_name: 'owner/repo' } } } },
  vars: { AWS_DEPLOY_ROLE_ARN: 'backend-role', AWS_FRONTEND_DEPLOY_ROLE_ARN: 'frontend-role' },
  needs: { preflight: { result: 'success', outputs: { attempt: '2' } }, deploy: { result: 'success', outputs: { attempt: '2' } } },
  inputs: { commit_sha: 'b'.repeat(40) } });

test('backend requires successful preflight in this attempt; pending/error/skip cannot reach it', () => {
  const backend = job(deploy, 'deploy');
  assert.match(backend, /needs: preflight/);
  const context = normal();
  assert.equal(evaluate(guard(backend), context), true);
  ['failure', 'cancelled', 'skipped'].forEach((result) => {
    assert.equal(evaluate(guard(backend), { ...context, needs: { preflight: { result, outputs: {} } } }), false, result);
  });
  assert.equal(evaluate(guard(backend), { ...context, needs: { preflight: { result: 'success', outputs: { attempt: '1' } } } }), false);
  assert.equal(evaluate(guard(backend), { ...context, cancelled: () => true }), false);
  const preflight = job(deploy, 'preflight');
  assert.match(preflight, /role-to-assume: \$\{\{ vars.AWS_FRONTEND_DEPLOY_ROLE_ARN \}\}/);
  assert.match(preflight, /node infra\/release\/frontend.mjs preflight/);
  assert.match(preflight, /ref: \$\{\{ github.event.workflow_run.head_sha \}\}/);
});

test('preflight rejects untrusted CI and missing role; manual backend recovery remains possible', () => {
  const expression = guard(job(deploy, 'preflight'));
  assert.equal(evaluate(expression, normal()), true);
  [
    (c) => { c.github.event.workflow_run.event = 'pull_request'; },
    (c) => { c.github.event.workflow_run.conclusion = 'failure'; },
    (c) => { c.github.event.workflow_run.head_branch = 'feature'; },
    (c) => { c.github.event.workflow_run.head_repository.full_name = 'fork/repo'; },
    (c) => { c.github.ref = 'refs/heads/feature'; },
    (c) => { c.vars.AWS_FRONTEND_DEPLOY_ROLE_ARN = ''; },
  ].forEach((alter) => { const c = normal(); alter(c); assert.equal(evaluate(expression, c), false); });
  const manual = normal(); manual.github.event_name = 'workflow_dispatch'; manual.needs.preflight.result = 'skipped';
  assert.equal(evaluate(expression, manual), false);
  assert.equal(evaluate(guard(job(deploy, 'deploy')), manual), true);
});

test('normal release holds shared lock through frontend; manual work waits without child deadlock', () => {
  const concurrency = (yaml) => yaml.split('\nconcurrency:\n')[1].split('\njobs:')[0];
  const parent = concurrency(deploy); const child = concurrency(frontend);
  [parent, child].forEach((block) => {
    assert.match(block, /cancel-in-progress: false/);
    assert.match(block, /queue: max/);
  });
  const group = (block) => block.match(/^  group: (.+)$/m)[1];
  assert.equal(group(parent), 'deploy-production');
  assert.equal(evaluate(group(child), normal()), 'deploy-frontend-101');
  const manual = normal(); manual.github.event_name = 'workflow_dispatch';
  assert.equal(evaluate(group(child), manual), group(parent));
  const childJob = job(deploy, 'frontend');
  assert.match(childJob, /needs: deploy/);
  assert.match(childJob, /uses: .\/.github\/workflows\/deploy-frontend.yml/);
  const expression = childJob.match(/    if: (.+)/)[1];
  assert.equal(evaluate(expression, normal()), true);
  const retry = normal(); retry.needs.deploy.outputs.attempt = '1';
  assert.equal(evaluate(expression, retry), false);
});

test('helper uses deployed SHA for normal calls and dispatch SHA for manual operations', () => {
  const expression = frontend.split('path: delivery\n')[1].match(/ref: (.+)/)[1];
  assert.equal(evaluate(expression, normal()), 'b'.repeat(40));
  const manual = normal(); manual.github.event_name = 'workflow_dispatch';
  assert.equal(evaluate(expression, manual), 'c'.repeat(40));
});
