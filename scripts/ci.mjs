import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { appendFileSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { frontendTasks, gateFailures, selectTracks, tracks } from './ci-policy.mjs';

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
  const selection = process.env.GITHUB_EVENT_NAME === 'pull_request'
    ? (() => {
      const base = event.pull_request.base.sha;
      const head = event.pull_request.head.sha;
      assert.match(base, /^[0-9a-f]{40}$/);
      assert.match(head, /^[0-9a-f]{40}$/);
      const files = run('git', ['diff', '--name-only', '--no-renames', '-z', `${base}...${head}`])
        .split('\0').filter(Boolean);
      console.log(`Changed paths: ${files.length}`);
      return selectTracks(files, readWorkspaces());
    })()
    : Object.fromEntries(tracks.map((track) => [track, true]));
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
  const failures = gateFailures(JSON.parse(process.env.CI_NEEDS));
  assert.deepEqual(failures, [], failures.join('\n'));
  console.log('All selected CI jobs succeeded; only unselected jobs may be skipped.');
};

const commands = { select, frontend, gate };
assert.ok(Object.hasOwn(commands, process.argv[2]), 'Use select, frontend <track>, or gate');
commands[process.argv[2]]();
