import assert from 'node:assert/strict';
import test from 'node:test';
import { browserArgs, checkListedScenarios } from './browser-ci.mjs';

test('scenario paths become literal CLI filters without shell interpolation', () => {
  assert.deepEqual(browserArgs('player', { player: ['packages/player/e2e/changed.spec.ts'] }), ['e2e/changed\\.spec\\.ts$']);
  assert.deepEqual(browserArgs('player', { player: 'all' }), []);
  for (const file of ['../other.spec.ts', 'packages/player/e2e/../other.spec.ts', 'packages/player/e2e/bad\n.spec.ts']) {
    assert.throws(() => browserArgs('player', { player: [file] }));
  }
  assert.throws(() => browserArgs('player', { player: [] }));
});
test('every selected file must contain a collected scenario; zero matches cannot pass', () => {
  const selected = ['e2e/src/specs/new.spec.ts'];
  checkListedScenarios(selected, { suites: [{ suites: [{ file: 'new.spec.ts', specs: [{}] }] }] });
  assert.throws(() => checkListedScenarios(selected, { suites: [] }), /not collected/);
  assert.throws(() => checkListedScenarios(selected, { suites: [{ file: 'other.spec.ts', specs: [{}] }] }));
  assert.throws(() => checkListedScenarios(selected, { errors: [{}], suites: [] }), /could not collect/);
});
