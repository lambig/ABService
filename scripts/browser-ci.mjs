import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { browserSuites } from './browser-policy.mjs';

const root = fileURLToPath(new URL('..', import.meta.url));
export const browserArgs = (id, plan) => {
  const suite = browserSuites[id];
  assert.ok(suite, 'Unknown browser suite');
  const mode = plan[id];
  if (mode === 'all') return [];
  assert.ok(Array.isArray(mode) && mode.length > 0, 'No browser scenarios selected');
  return mode.map((file) => {
    assert.ok(file.startsWith(`${suite.location}/${suite.specs}/`) && file.endsWith('.spec.ts')
      && !file.includes('..') && !/[\r\n\\]/.test(file), 'Invalid scenario path');
    return `${file.slice(suite.location.length + 1).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`;
  });
};
export const checkListedScenarios = (selected, report) => {
  const found = new Set();
  const walk = (suites) => suites.forEach((suite) => {
    if (suite.specs?.length) found.add(suite.file.replaceAll('\\', '/'));
    walk(suite.suites ?? []);
  });
  assert.ok(!report.errors?.length, 'Playwright could not collect scenarios');
  walk(report.suites ?? []);
  selected.forEach((file) => assert.ok([...found].some((path) => file.endsWith(path)), `Scenario not collected: ${file}`));
};

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const id = process.argv[2];
  const plan = JSON.parse(process.env.BROWSER_PLAN);
  const args = browserArgs(id, plan);
  const cwd = resolve(root, browserSuites[id].location);
  const cli = createRequire(resolve(cwd, 'package.json')).resolve('@playwright/test/cli');
  if (plan[id] !== 'all') {
    const report = execFileSync(process.execPath, [cli, 'test', '--list', '--reporter=json', ...args], { cwd, encoding: 'utf8' });
    checkListedScenarios(plan[id], JSON.parse(report));
  }
  // Preserve the application's three ordered projects and per-project worker counts.
  const command = id === 'e2e' ? [resolve(cwd, 'scripts/run-e2e.mjs'), ...args] : [cli, 'test', ...args];
  execFileSync(process.execPath, command, { cwd, stdio: 'inherit' });
}
