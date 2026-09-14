import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { frontendTasks, gateFailures, jobs, selectTracks, trackWorkspaces } from './ci-policy.mjs';

const workspace = (name, location, dependencies = {}) => ({
  name, location, dependencies, scripts: { lint: 'lint', typecheck: 'types', test: 'test', build: 'build' },
});
const fixtures = [
  workspace('site', 'frontend-public', { markup: '*' }),
  workspace('markup', 'packages/markup', { rules: '*' }),
  workspace('player', 'packages/player', { rules: '*' }),
  workspace('offline', 'packages/offline-player', { player: '*' }),
  workspace('rules', 'packages/eslint-config'),
  workspace('e2e', 'e2e'),
];

test('listening code does not select the application; application code does not select listening', () => {
  assert.deepEqual(selectTracks(['packages/player/src/player.ts'], fixtures), { application: false, listening: true });
  assert.deepEqual(selectTracks(['frontend-public/src/page.astro'], fixtures), { application: true, listening: false });
  assert.deepEqual(selectTracks(['backend/src/Service.java'], fixtures), { application: true, listening: false });
});

test('shared dependencies and cross-track consumers select both tracks transitively', () => {
  assert.deepEqual(selectTracks(['packages/eslint-config/index.js'], fixtures), { application: true, listening: true });
  const linked = fixtures.map((item) => item.name === 'markup'
    ? { ...item, dependencies: { player: '*' } } : item);
  assert.deepEqual(selectTracks(['packages/player/src/player.ts'], linked), { application: true, listening: true });
  assert.ok(trackWorkspaces(linked, 'application').some((item) => item.name === 'player'));
});

test('metadata, unknown files, empty diffs and unknown workspaces fail closed to both tracks', () => {
  ['package-lock.json', '.github/workflows/ci.yml', '.nvmrc', 'scripts/new.mjs',
    'packages/player/package.json', 'packages/deleted/src/file.ts'].forEach((file) =>
    assert.deepEqual(selectTracks([file], fixtures), { application: true, listening: true }, file));
  assert.deepEqual(selectTracks([], fixtures), { application: true, listening: true });
  const added = [...fixtures, workspace('new', 'packages/new')];
  assert.deepEqual(selectTracks(['packages/new/index.ts'], added), { application: true, listening: true });
  assert.ok(frontendTasks(added, 'application').some((task) => task.workspace === 'new'));
  assert.ok(frontendTasks(added, 'listening').some((task) => task.workspace === 'new'));
});

test('both paths of a rename select both owners; documentation exclusion does not hide executable docs', () => {
  assert.deepEqual(selectTracks(['packages/player/old.ts', 'frontend-public/new.ts'], fixtures), { application: true, listening: true });
  assert.deepEqual(selectTracks(['README.md', 'docs/DECISIONS.md'], fixtures), { application: false, listening: false });
  assert.deepEqual(selectTracks(['docs/generator.mjs'], fixtures), { application: true, listening: true });
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
  assert.ok(!application.some((task) => task.workspace === 'site' && task.script === 'build'));
  assert.ok(!application.some((task) => task.workspace === 'e2e' && task.script === 'test'));
  assert.ok(!application.some((task) => task.workspace === 'player'));
  const listening = frontendTasks(fixtures, 'listening');
  assert.ok(listening.some((task) => task.workspace === 'offline' && task.script === 'build'));
});

const results = (application, listening) => ({
  changes: { result: 'success', outputs: { application: String(application), listening: String(listening) } },
  ...Object.fromEntries(Object.entries(jobs).map(([job, track]) =>
    [job, { result: { application, listening }[track] ? 'success' : 'skipped' }])),
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
