import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { runInNewContext } from 'node:vm';
import test from 'node:test';
import { deploymentDecision } from './frontend.mjs';

// Evaluate actual workflow guards with event fixtures, not a second policy implementation.
const read = (name) => readFileSync(new URL(`../../.github/workflows/${name}.yml`, import.meta.url), 'utf8');
const deploy = read('deploy');
const frontend = read('deploy-frontend');
const job = (yaml, name) => yaml.split(`\n  ${name}:\n`)[1]?.split(/\n  [\w-]+:\n/)[0];
const guard = (block) => block.match(/    if: >-\n((?:      .*(?:\n|$))+)/)[1].trim();
const evaluate = (expression, context) => runInNewContext(expression.replace(/^\$\{\{\s*|\s*\}\}$/g, ''), {
  cancelled: () => false, format: (pattern, value) => pattern.replace('{0}', value), ...context,
}, { timeout: 1000 });
const normal = () => ({ github: { ref: 'refs/heads/main', event_name: 'workflow_dispatch', repository: 'owner/repo',
  run_id: 101, run_attempt: 2, sha: 'c'.repeat(40), event: { workflow_run: { conclusion: 'success',
    head_branch: 'main', event: 'push', head_repository: { full_name: 'owner/repo' } } } },
  vars: { AWS_DEPLOY_ROLE_ARN: 'backend-role', AWS_FRONTEND_DEPLOY_ROLE_ARN: 'frontend-role' },
  needs: { preflight: { result: 'success', outputs: { attempt: '2', deploy: 'true' } }, deploy: { result: 'success', outputs: { attempt: '2' } } },
  inputs: { action: 'release', normal_release: true, commit_sha: 'b'.repeat(40) } });

test('backend requires successful preflight in this attempt; pending/error/skip cannot reach it', () => {
  const backend = job(deploy, 'deploy');
  assert.match(backend, /needs: preflight/);
  const context = normal();
  assert.equal(evaluate(guard(backend), context), true);
  ['failure', 'cancelled', 'skipped'].forEach((result) => {
    assert.equal(evaluate(guard(backend), { ...context, needs: { preflight: { result, outputs: {} } } }), false, result);
  });
  assert.equal(evaluate(guard(backend), { ...context, needs: { preflight: { result: 'success', outputs: { attempt: '1', deploy: 'true' } } } }), false);
  assert.equal(evaluate(guard(backend), { ...context, cancelled: () => true }), false);
  const preflight = job(deploy, 'preflight');
  assert.match(preflight, /role-to-assume: \$\{\{ vars.AWS_FRONTEND_DEPLOY_ROLE_ARN \}\}/);
  assert.match(preflight, /node infra\/release\/frontend.mjs preflight/);
  assert.match(preflight, /ref: \$\{\{ inputs.controller_sha \|\| github.sha \}\}/);
  assert.match(preflight, /fetch-depth: 0/);
  assert.match(preflight, /RELEASE_SHA: \$\{\{ inputs.commit_sha \}\}/);
});

test('watermark C with a later successful CI for ancestor B skips backend and frontend', () => {
  const b = 'b'.repeat(40); const c = 'c'.repeat(40);
  const decision = deploymentDecision(c, b,
    (ancestor, descendant) => ancestor === b && descendant === c);
  assert.equal(decision.deploy, false);
  const context = normal(); context.needs.preflight.outputs.deploy = String(decision.deploy);
  assert.equal(evaluate(guard(job(deploy, 'deploy')), context), false);
  context.needs.deploy.result = 'skipped';
  assert.equal(evaluate(job(deploy, 'frontend').match(/    if: (.+)/)[1], context), false);
  delete context.needs.preflight.outputs.deploy;
  assert.equal(evaluate(guard(job(deploy, 'deploy')), context), false, 'missing decision must not allow deployment');
});

test('preflight rejects untrusted CI and missing role; manual backend recovery remains possible', () => {
  const expression = guard(job(deploy, 'preflight'));
  assert.equal(evaluate(expression, normal()), true);
  [
    (c) => { c.github.event_name = 'workflow_run'; },
    (c) => { c.github.event_name = 'push'; },
    (c) => { c.inputs.action = 'unknown'; },
    (c) => { c.github.ref = 'refs/heads/feature'; },
    (c) => { c.vars.AWS_FRONTEND_DEPLOY_ROLE_ARN = ''; },
  ].forEach((alter) => { const c = normal(); alter(c); assert.equal(evaluate(expression, c), false); });
  const manual = normal(); manual.inputs.action = 'rollback'; manual.inputs.normal_release = false; manual.needs.preflight.result = 'skipped';
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
  const manual = normal(); manual.inputs.action = 'rollback'; manual.inputs.normal_release = false;
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
  const manual = normal(); manual.inputs.action = 'rollback'; manual.inputs.normal_release = false;
  assert.equal(evaluate(expression, manual), 'c'.repeat(40));
});

test('private caller checks out source code, not its own operations commit', () => {
  const c = normal();
  c.github.repository = 'owner/operations';
  c.inputs.source_repository = 'owner/source';
  c.inputs.controller_sha = 'd'.repeat(40);
  const checkouts = (yaml) => [...yaml.matchAll(/uses: actions\/checkout@v4\n([\s\S]*?)(?=\n      - |$)/g)].map(m => m[1]);
  for (const block of [...checkouts(deploy), ...checkouts(frontend)]) {
    assert.equal(evaluate(block.match(/repository: (.+)/)[1], c), 'owner/source');
    const direct = normal();
    assert.equal(evaluate(block.match(/repository: (.+)/)[1], direct), 'owner/repo');
  }
  const controller = job(deploy, 'preflight').match(/ref: (.+)/)[1];
  assert.equal(evaluate(controller, c), 'd'.repeat(40));
  const helper = frontend.split('path: delivery\n')[1].match(/ref: (.+)/)[1];
  assert.equal(evaluate(helper, c), 'b'.repeat(40), 'normal helper remains pinned to deployed candidate');
  c.inputs.normal_release = false;
  assert.equal(evaluate(helper, c), 'd'.repeat(40), 'manual recovery uses reviewed source controller');
  for (const name of ['source_repository', 'controller_sha']) {
    assert.match(job(deploy, 'frontend'), new RegExp(`${name}: \\$\\{\\{ inputs\\.${name} \\|\\| github\\.`));
  }
  assert.match(job(deploy, 'preflight'), /SOURCE_REPOSITORY: \$\{\{ inputs.source_repository \|\| github.repository \}\}/);
  // The private caller must still dispatch from main and satisfy all existing release guards.
  assert.equal(evaluate(guard(job(deploy, 'preflight')), c), true);
  c.github.ref = 'refs/heads/feature';
  assert.equal(evaluate(guard(job(deploy, 'preflight')), c), false);
});

test('controller refs and source names fail closed before checkout and AWS access', () => {
  const blocks = [...(deploy + frontend).matchAll(/- name: Validate source and controller inputs before checkout\n[\s\S]*?run: \|\n((?:          .*\n)+)/g)];
  assert.equal(blocks.length, 3);
  for (const match of blocks) {
    const script = match[1].replace(/^          /gm, '');
    for (const [repository, sha, pass] of [
      ['', '', true], ['owner/source', 'a'.repeat(40), true],
      ['owner/source', 'main', false], ['owner/source', '', false],
      ['', 'a'.repeat(40), false], ['https://example.test/source', 'a'.repeat(40), false],
    ]) {
      const result = spawnSync(process.env.TEST_BASH || 'bash', ['--noprofile', '--norc', '-e', '-c', script], {
        env: { ...process.env, SOURCE_REPOSITORY: repository, CONTROLLER_SHA: sha }, encoding: 'utf8',
      });
      assert.equal(result.status === 0, pass, result.error?.message || `${repository} / ${sha}`);
    }
  }
});

test('every public build uses checked generation metadata; recovery builds both sites at pending target SHA', () => {
  assert.match(frontend, /options: \[rebuild-public, rollback, recover\]/);
  const build = frontend.split('- name: Build the selected code with current public data')[1].split('- name: Publish')[0];
  assert.match(build, /RELEASE_SHA: \$\{\{ steps.source.outputs.code_sha \}\}/);
  assert.match(build, /node ..\/delivery\/infra\/release\/build-public.mjs/);
  assert.doesNotMatch(build, /npm run build:public/);
  assert.match(build, /if \[ "\$ACTION" != rebuild-public \]; then\s+npm run build:admin\s+npm run build:offline-player\s+node ..\/delivery\/infra\/release\/listening-artifacts.mjs packages\/offline-player\/dist listening-release\s+fi/);
  assert.match(frontend, /node delivery\/infra\/release\/frontend.mjs status/);
});

test('main never deploys automatically; release CI is available without AWS access', () => {
  assert.doesNotMatch(deploy.split('permissions:')[0], /workflow_run:|push:/);
  const ci = read('ci');
  assert.equal((ci.match(/branches: \[main, "release\/\*\*"\]/g) ?? []).length, 2);
  const preflight = job(deploy, 'preflight');
  assert.ok(preflight.indexOf('node infra/release/candidate.mjs') < preflight.indexOf('configure-aws-credentials'));
  assert.ok(preflight.indexOf('node infra/release/frontend.mjs record-candidate') < preflight.indexOf('echo "attempt='));
  assert.match(deploy, /actions: read/);
  assert.match(job(deploy, 'frontend'), /normal_release: true/);
  assert.match(frontend, /normal_release:\n        type: boolean\n        default: false/);
});

test('frontend call and standalone recovery require main and explicit actions', () => {
  const expression = guard(job(frontend, 'frontend'));
  assert.equal(evaluate(expression, normal()), true);
  for (const action of ['rebuild-public', 'rollback', 'recover']) {
    const c = normal(); c.inputs = { normal_release: false, action };
    assert.equal(evaluate(expression, c), true);
  }
  const c = normal(); c.inputs = { normal_release: false, action: 'release' };
  assert.equal(evaluate(expression, c), false);
  c.inputs.normal_release = true; c.github.ref = 'refs/heads/release/1.10';
  assert.equal(evaluate(expression, c), false);
});
