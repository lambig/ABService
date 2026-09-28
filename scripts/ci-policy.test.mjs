import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { frontendTasks, gateFailures, jobs, selectCI, selectTracks, trackWorkspaces } from './ci-policy.mjs';
import { browserKeys, browserSuites } from './browser-policy.mjs';

const workspace = (name, location, dependencies = {}) => ({
  name, location, dependencies, scripts: { lint: 'lint', typecheck: 'types', test: 'test', build: 'build' },
});
const fixtures = [
  workspace('site', 'frontend-public', { markup: '*', presentation: '*' }),
  workspace('presentation', 'packages/public-presentation', { markup: '*' }),
  workspace('markup', 'packages/markup', { rules: '*' }),
  workspace('player', 'packages/player', { rules: '*' }),
  workspace('offline', 'packages/offline-player', { player: '*' }),
  workspace('rules', 'packages/eslint-config'),
  workspace('e2e', 'e2e'),
];

const mainSelection = (files, workspaces = fixtures) => selectCI({
  event: 'push', ref: 'refs/heads/main', files, workspaces,
});

test('main scopes ordinary player and application changes without repeating browser regressions', () => {
  for (const [file, application, listening, infrastructure] of [
    ['packages/player/src/player.ts', false, true, false],
    ['frontend-public/src/page.astro', true, false, false],
    ['backend/src/Service.java', true, false, false],
    ['infra/compute.tf', true, false, true],
    ['docs/DECISIONS.md', false, false, false],
  ]) {
    const selected = mainSelection([file]);
    assert.equal(selected.application, application, file);
    assert.equal(selected.listening, listening, file);
    assert.equal(selected.infrastructure, infrastructure, file);
    assert.ok(browserKeys.every((key) => selected[key] === false), file);
  }
});

test('main retains shared/transitive, metadata, unknown and unavailable-diff coverage', () => {
  for (const files of [[], ['package-lock.json'], ['packages/player/package.json'],
    ['scripts/new.mjs'], ['packages/eslint-config/index.js'],
    ['packages/deleted/old.ts'], ['packages/player/old.ts', 'frontend-public/new.ts']]) {
    const selected = mainSelection(files);
    assert.equal(selected.application, true, files.join(','));
    assert.equal(selected.listening, true, files.join(','));
  }
  const linked = fixtures.map((item) => item.name === 'markup'
    ? { ...item, dependencies: { player: '*' } } : item);
  assert.equal(mainSelection(['packages/player/src/player.ts'], linked).application, true);
});

test('main scenario changes still execute their browser suites and containing jobs', () => {
  for (const [file, suite, application, listening] of [
    ['packages/player/e2e/play.spec.ts', 'player', false, true],
    ['e2e/src/specs/page.spec.ts', 'e2e', true, false],
  ]) {
    const selected = mainSelection([file]);
    assert.equal(selected.application, application);
    assert.equal(selected.listening, listening);
    assert.deepEqual(JSON.parse(selected.browser_plan)[suite], [file]);
    assert.equal(selected[`browser_${suite}`], true);
  }
  assert.equal(JSON.parse(mainSelection(['packages/player/e2e/support.ts']).browser_plan).player, 'all');
});

test('browser fallback cannot select application E2E inside a skipped application job', () => {
  const orphan = [...fixtures, workspace('dsp', 'packages/audio-dsp')];
  const selected = mainSelection(['packages/audio-dsp/src/index.ts'], orphan);
  assert.equal(selected.browser_e2e, true);
  assert.equal(selected.application, true);
});

test('release and manual CI always select all tracks and complete browser suites', () => {
  for (const context of [
    { event: 'push', ref: 'refs/heads/release/1.10' },
    { event: 'workflow_dispatch', ref: 'refs/heads/main' },
    { event: 'unknown', ref: 'refs/heads/main' },
  ]) {
    const selected = selectCI({ ...context, files: ['docs/DECISIONS.md'], workspaces: fixtures });
    assert.equal(selected.application, true);
    assert.equal(selected.listening, true);
    assert.equal(selected.infrastructure, true);
    assert.ok(Object.values(JSON.parse(selected.browser_plan)).every((mode) => mode === 'all'));
  }
  const pr = selectCI({ event: 'pull_request', ref: 'refs/pull/1/merge',
    files: ['packages/player/src/player.ts'], workspaces: fixtures });
  assert.equal(pr.application, false);
  assert.equal(pr.listening, true);
  assert.equal(pr.infrastructure, false);
  assert.equal(pr.browser_player, true);
  const unknownBranch = selectCI({ event: 'push', ref: 'refs/heads/other',
    files: ['docs/DECISIONS.md'], workspaces: fixtures });
  assert.equal(unknownBranch.application, true);
  assert.equal(unknownBranch.listening, true);
  assert.equal(unknownBranch.infrastructure, true);
});

const only = (...selected) => ({
  application: selected.includes('application'),
  listening: selected.includes('listening'),
  infrastructure: selected.includes('infrastructure'),
});
const every = only('application', 'listening', 'infrastructure');
const workspacesOnly = only('application', 'listening');

test('listening code does not select the application; application code does not select listening', () => {
  assert.deepEqual(selectTracks(['packages/player/src/player.ts'], fixtures), only('listening'));
  assert.deepEqual(selectTracks(['frontend-public/src/page.astro'], fixtures), only('application'));
  assert.deepEqual(selectTracks(['packages/public-presentation/src/index.ts'], fixtures), only('application'));
  assert.deepEqual(selectTracks(['backend/src/Service.java'], fixtures), only('application'));
});

test('only infrastructure, CI and unknown tooling changes select the infrastructure track', () => {
  ['backend/src/Service.java', 'frontend-admin/src/page.ts', 'e2e/src/specs/page.spec.ts',
    'docker/backend/Dockerfile', 'docker-compose.yml', 'packages/player/src/player.ts'].forEach((file) =>
    assert.equal(selectTracks([file], fixtures).infrastructure, false, file));
  // Application jobs run infra/ host and release checks too, so infra/ keeps the application.
  ['infra/compute.tf', 'infra/monitoring/transport/test_transport.py'].forEach((file) =>
    assert.deepEqual(selectTracks([file], fixtures), only('application', 'infrastructure'), file));
  ['scripts/check-deploy-permissions.sh', 'scripts/ci-policy.mjs', '.github/workflows/ci.yml',
    '.github/workflows/deploy.yml'].forEach((file) =>
    assert.deepEqual(selectTracks([file], fixtures), every, file));
});

test('shared dependencies and cross-track consumers select both workspace tracks transitively', () => {
  assert.deepEqual(selectTracks(['packages/eslint-config/index.js'], fixtures), workspacesOnly);
  const linked = fixtures.map((item) => item.name === 'markup'
    ? { ...item, dependencies: { player: '*' } } : item);
  assert.deepEqual(selectTracks(['packages/player/src/player.ts'], linked), workspacesOnly);
  assert.ok(trackWorkspaces(linked, 'application').some((item) => item.name === 'player'));
  assert.throws(() => trackWorkspaces(fixtures, 'infrastructure'));
});

test('metadata selects the workspace tracks; unknown files, empty diffs and unknown workspaces fail closed', () => {
  ['package-lock.json', 'packages/player/package.json'].forEach((file) =>
    assert.deepEqual(selectTracks([file], fixtures), workspacesOnly, file));
  ['.github/workflows/ci.yml', '.nvmrc', 'scripts/new.mjs', 'packages/deleted/src/file.ts'].forEach((file) =>
    assert.deepEqual(selectTracks([file], fixtures), every, file));
  assert.deepEqual(selectTracks([], fixtures), every);
  const added = [...fixtures, workspace('new', 'packages/new')];
  assert.deepEqual(selectTracks(['packages/new/index.ts'], added), workspacesOnly);
  assert.ok(frontendTasks(added, 'application').some((task) => task.workspace === 'new'));
  assert.ok(frontendTasks(added, 'listening').some((task) => task.workspace === 'new'));
});

test('both paths of a rename select both owners; documentation exclusion does not hide executable docs', () => {
  assert.deepEqual(selectTracks(['packages/player/old.ts', 'frontend-public/new.ts'], fixtures), workspacesOnly);
  assert.deepEqual(selectTracks(['README.md', 'docs/DECISIONS.md'], fixtures), only());
  assert.deepEqual(selectTracks(['docs/generator.mjs'], fixtures), every);
});

test('dependency cycles terminate and missing check scripts are rejected', () => {
  const cycle = [workspace('site', 'frontend-public', { player: '*' }), workspace('player', 'packages/player', { site: '*' })];
  assert.equal(trackWorkspaces(cycle, 'application').length, 2);
  assert.throws(() => frontendTasks([{ ...workspace('empty', 'packages/new'), scripts: {} }], 'application'));
  assert.throws(() => frontendTasks(fixtures, 'misspelled'));
});

test('frontend checks include declared package builds and tests, without starting application E2E/build', () => {
  const application = frontendTasks(fixtures, 'application');
  assert.ok(application.some((task) => task.workspace === 'markup' && task.script === 'test'));
  assert.ok(application.some((task) => task.workspace === 'presentation' && task.script === 'test'));
  assert.ok(!application.some((task) => task.workspace === 'site' && task.script === 'build'));
  assert.ok(!application.some((task) => task.workspace === 'e2e' && task.script === 'test'));
  assert.ok(!application.some((task) => task.workspace === 'player'));
  const listening = frontendTasks(fixtures, 'listening');
  assert.ok(listening.some((task) => task.workspace === 'offline' && task.script === 'build'));
});

const results = (application, listening, infrastructure = application) => ({
  changes: { result: 'success', outputs: { application: String(application), listening: String(listening),
    infrastructure: String(infrastructure),
    browser_plan: JSON.stringify(Object.fromEntries(Object.keys(browserSuites).map((id) => [id, listening ? 'all' : []]))),
    ...Object.fromEntries(browserKeys.map((key) => [key, String(listening)])) } },
  ...Object.fromEntries(Object.entries(jobs).map(([job, track]) =>
    [job, { result: (track.startsWith('browser_') ? listening : { application, listening, infrastructure }[track])
      ? 'success' : 'skipped' }])),
});

test('release/manual gate refuses omitted browser suites even when PR/main would accept the skip', () => {
  assert.deepEqual(gateFailures(results(true, false)), []);
  assert.ok(gateFailures(results(true, false), true).length > 0);
  assert.deepEqual(gateFailures(results(true, true), true), []);
  const scoped = results(true, true);
  const plan = JSON.parse(scoped.changes.outputs.browser_plan);
  plan.e2e = ['e2e/src/specs/smoke.spec.ts'];
  scoped.changes.outputs.browser_plan = JSON.stringify(plan);
  assert.deepEqual(gateFailures(scoped), []);
  assert.ok(gateFailures(scoped, true).length > 0);
});

test('release/manual gate rejects omitted tracks even with all browser suites successful', () => {
  for (const track of ['application', 'listening', 'infrastructure']) {
    const needs = results(true, true);
    needs.changes.outputs[track] = 'false';
    Object.entries(jobs).filter(([, owner]) => owner === track)
      .forEach(([job]) => { needs[job].result = 'skipped'; });
    assert.ok(gateFailures(needs, true).length > 0, track);
    for (const [event, ref, success] of [
      ['push', 'refs/heads/main', true],
      ['push', 'refs/heads/release/1.10', false],
      ['workflow_dispatch', 'refs/heads/main', false],
    ]) {
      const result = spawnSync(process.execPath, [fileURLToPath(new URL('./ci.mjs', import.meta.url)), 'gate'], {
        env: { ...process.env, GITHUB_EVENT_NAME: event, GITHUB_REF: ref, CI_NEEDS: JSON.stringify(needs) },
        encoding: 'utf8',
      });
      assert.equal(result.status, success ? 0 : 1, result.stderr);
    }
  }
});

test('the gate accepts only intentional skips, including documentation-only PRs', () => {
  assert.deepEqual(gateFailures(results(false, true)), []);
  assert.deepEqual(gateFailures(results(true, false)), []);
  assert.deepEqual(gateFailures(results(true, true)), []);
  assert.deepEqual(gateFailures(results(false, false)), []);
});

test('selected jobs cannot fail, disappear, cancel or skip and still pass the gate', () => {
  ['failure', 'cancelled', 'skipped', undefined].forEach((result) => {
    const needs = { ...results(false, true), 'offline-player-browser': { result } };
    assert.equal(gateFailures(needs).length, 1, String(result));
  });
  assert.equal(gateFailures({ ...results(true, false), 'player-browser': { result: 'failure' } }).length, 1);
});

test('the IaC check is required only when the infrastructure track is selected', () => {
  assert.equal(jobs['iac-check'], 'infrastructure');
  assert.deepEqual(gateFailures(results(true, false, false)), []);
  assert.deepEqual(gateFailures(results(false, false, true)), []);
  ['failure', 'cancelled', 'skipped', undefined].forEach((result) => {
    const needs = { ...results(false, false, true), 'iac-check': { result } };
    assert.deepEqual(gateFailures(needs), [`iac-check: ${result ?? 'missing'} (required=true)`], String(result));
  });
  assert.equal(gateFailures({ ...results(true, false, false), 'iac-check': { result: 'failure' } }).length, 1);
  const missing = results(true, true);
  delete missing.changes.outputs.infrastructure;
  assert.equal(gateFailures(missing).length, 1);
});

test('failed selection or missing/invalid selection outputs never produce a green gate', () => {
  ['failure', 'cancelled', 'skipped'].forEach((result) =>
    assert.equal(gateFailures({ ...results(true, true), changes: { result } }).length, 1));
  assert.equal(gateFailures({ ...results(true, true), changes: { result: 'success', outputs: {} } }).length, 1);
  assert.equal(gateFailures({}).length, 1);
});

test('every workflow job is selected, awaited and checked by the gate', () => {
  const yaml = readFileSync(new URL('../.github/workflows/ci.yml', import.meta.url), 'utf8');
  const actual = [...yaml.matchAll(/^  ([\w-]+):$/gm)].map((match) => match[1])
    .filter((name) => !['push', 'pull_request', 'workflow_dispatch'].includes(name));
  assert.deepEqual(actual.sort(), ['changes', 'ci-gate', ...Object.keys(jobs)].sort());
  const gate = yaml.slice(yaml.indexOf('  ci-gate:'));
  const awaited = gate.match(/needs: \[([^\]]+)\]/)[1].split(',').map((name) => name.trim());
  assert.deepEqual(awaited.sort(), ['changes', ...Object.keys(jobs)].sort());
  assert.ok(gate.includes('if: ${{ always() }}'));
  Object.entries(jobs).forEach(([job, track]) => {
    assert.ok(yaml.includes(`  ${job}:\n    needs: changes\n    if: \${{ needs.changes.outputs.${track} == 'true' }}`), job);
  });
});
