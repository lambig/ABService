import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { appendFileSync, existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { frontendTasks, gateFailures, selectTracks, tracks } from './ci-policy.mjs';
import { browserSelection, browserOutputs } from './browser-policy.mjs';

const root = fileURLToPath(new URL('..', import.meta.url));
const run = (command, args) => execFileSync(command, args, {
  cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'inherit'],
});

// npm is the source of workspace membership; the lockfile supplies locations.
// No npm ci is needed to decide which jobs should run.
const readWorkspaces = () => {
  const manifests = JSON.parse(run('npm', ['pkg', 'get', '--workspaces', '--json']));
  const lock = JSON.parse(readFileSync(new URL('../package-lock.json', import.meta.url), 'utf8'));
  return Object.entries(manifests).map(([name, manifest]) => {
    const link = lock.packages[`node_modules/${name}`];
    assert.equal(link?.link, true, `Missing workspace link for ${name}`);
    assert.equal(manifest.name, name);
    assert.ok(typeof link.resolved === 'string' && !link.resolved.startsWith('../'));
    return { ...manifest, location: link.resolved };
  });
};

const select = () => {
  const event = JSON.parse(readFileSync(process.env.GITHUB_EVENT_PATH, 'utf8'));
  const workspaces = readWorkspaces();
  const files = process.env.GITHUB_EVENT_NAME === 'pull_request'
    ? (() => {
      const base = event.pull_request.base.sha;
      const head = event.pull_request.head.sha;
      assert.match(base, /^[0-9a-f]{40}$/);
      assert.match(head, /^[0-9a-f]{40}$/);
      return run('git', ['diff', '--name-only', '--no-renames', '-z', `${base}...${head}`])
        .split('\0').filter(Boolean);
    })()
    : /^[a-f0-9]{40}$/.test(event.before ?? '') && event.before !== '0'.repeat(40)
      ? run('git', ['diff', '--name-only', '--no-renames', '-z', event.before, process.env.GITHUB_SHA]).split('\0').filter(Boolean)
      : [];
  const plan = browserSelection({ event: process.env.GITHUB_EVENT_NAME, ref: process.env.GITHUB_REF,
    files, workspaces, exists: (file) => existsSync(new URL(file, new URL('../', import.meta.url))) });
  const selection = { ...(process.env.GITHUB_EVENT_NAME === 'pull_request' ? selectTracks(files, workspaces)
    : Object.fromEntries(tracks.map((track) => [track, true]))), ...browserOutputs(plan), browser_plan: JSON.stringify(plan) };
  const lines = Object.entries(selection).map(([track, selected]) => `${track}=${selected}`).join('\n');
  console.log(lines);
  appendFileSync(process.env.GITHUB_OUTPUT, `${lines}\n`);
  process.env.GITHUB_STEP_SUMMARY && appendFileSync(process.env.GITHUB_STEP_SUMMARY,
    `## CI scope\n\n${lines.replaceAll('\n', '  \n')}\n`);
};

const frontend = () => {
  const tasks = frontendTasks(readWorkspaces(), process.argv[3]);
  console.log(JSON.stringify(tasks, null, 2));
  // Explicit per-workspace commands keep coverage automatic as workspaces grow.
  // E2E builds the application against its real backend in its own job.
  tasks.forEach(({ workspace, script }) => execFileSync('npm',
    ['run', script, '--workspace', workspace], { cwd: root, stdio: 'inherit' }));
};

const gate = () => {
  const fullBrowsers = process.env.GITHUB_EVENT_NAME === 'workflow_dispatch'
    || (process.env.GITHUB_EVENT_NAME === 'push' && process.env.GITHUB_REF?.startsWith('refs/heads/release/'));
  const failures = gateFailures(JSON.parse(process.env.CI_NEEDS), fullBrowsers);
  assert.deepEqual(failures, [], failures.join('\n'));
  console.log('All selected CI jobs succeeded; only unselected jobs may be skipped.');
};

const commands = { select, frontend, gate };
assert.ok(Object.hasOwn(commands, process.argv[2]), 'Use select, frontend <track>, or gate');
commands[process.argv[2]]();
