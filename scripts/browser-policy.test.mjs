import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { browserSelection, browserSuites, browserOutputs } from './browser-policy.mjs';
const workspaces = Object.entries(browserSuites).map(([name, s]) => ({ name, location: s.location,
  scripts: name === 'e2e' ? {} : { 'test:browser': 'playwright test' },
  dependencies: name === 'offline-player' ? { player: '*', storage: '*', shell: '*' } : {} }));
const select = (files, extra = {}) => browserSelection({ event: 'pull_request', ref: 'refs/pull/1/merge', files, workspaces, ...extra });
const only = (plan, ids) => assert.deepEqual(Object.entries(browserOutputs(plan)).filter(([, v]) => v).map(([k]) => k).sort(),
  ids.map((id) => `browser_${id.replaceAll('-', '_')}`).sort());

test('scenario-only PRs select exact files; scenario changes on main remain an exception', () => {
  for (const [id, suite] of Object.entries(browserSuites)) {
    const file = `${suite.location}/${suite.specs}/changed.spec.ts`;
    for (const context of [{}, { event: 'push', ref: 'refs/heads/main' }]) {
      const plan = select([file], context);
      only(plan, [id]); assert.deepEqual(plan[id], [file]);
    }
  }
});
test('shared fixtures/configuration/startup changes run the affected suite, not just an arbitrary scenario', () => {
  for (const file of ['e2e/src/support/worker.ts', 'e2e/scripts/serve-app.mjs', 'e2e/playwright.config.ts']) {
    const plan = select([file]); only(plan, ['e2e']); assert.equal(plan.e2e, 'all');
  }
  for (const file of ['packages/player/e2e/fixtures.ts', 'packages/player/vite.config.ts']) {
    const plan = select([file]); only(plan, ['player']); assert.equal(plan.player, 'all');
  }
});
test('browser-sensitive source selects transitive consumers in PRs, without repeating them on main', () => {
  only(select(['packages/player/src/player.ts']), ['player', 'offline-player']);
  only(select(['packages/player/src/player.ts'], { event: 'push', ref: 'refs/heads/main' }), []);
  assert.equal(select(['frontend-public/src/page.astro'], { workspaces: [...workspaces,
    { name: 'site', location: 'frontend-public' }] }).e2e, 'all');
});
test('deleted/renamed scenarios run the remaining suite and cannot silently match zero files', () => {
  const plan = select(['packages/player/e2e/old.spec.ts', 'packages/player/e2e/new.spec.ts'],
    { exists: (file) => !file.endsWith('old.spec.ts') });
  only(plan, ['player']); assert.equal(plan.player, 'all');
});
test('release and manual runs are always full; unknown scope and toolchain changes fail closed', () => {
  for (const context of [{ event: 'push', ref: 'refs/heads/release/1.10' }, { event: 'workflow_dispatch' }, { event: 'unknown' }]) {
    assert.ok(Object.values(select(['README.md'], context)).every((v) => v === 'all'));
  }
  for (const files of [[], ['package-lock.json'], ['.github/workflows/ci.yml'], ['scripts/ci.mjs'], ['packages/removed/file.ts']]) {
    assert.ok(Object.values(select(files)).every((v) => v === 'all'));
  }
  only(select(['docs/RELEASE_WORKFLOW.md']), []);
  assert.throws(() => select(['README.md'], { workspaces: [...workspaces,
    { name: 'new', location: 'packages/new', scripts: { 'test:browser': 'playwright test' } }] }), /Register browser workspace/);
});

test('stack preparation and non-browser acceptance remain unconditional within the application job', () => {
  const yaml = readFileSync(new URL('../.github/workflows/ci.yml', import.meta.url), 'utf8');
  const job = yaml.split('\n  e2e:\n')[1].split('\n  player-browser:\n')[0];
  assert.match(job, /Prepare application acceptance stack[\s\S]*run: node e2e\/scripts\/prepare-stack.mjs/);
  assert.match(job, /name: Run E2E\n        if: needs.changes.outputs.browser_e2e == 'true'/);
  const acceptance = job.slice(job.indexOf('      - name: Verify generation-checked'));
  assert.match(acceptance, /run: node infra\/release\/public-generation-acceptance.mjs/);
  assert.match(acceptance, /run: npm run acceptance -w abservice-seed-loader/);
  assert.doesNotMatch(acceptance, /browser_e2e/);
});
