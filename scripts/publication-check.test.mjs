import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { cpSync, mkdirSync, mkdtempSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { allowedTarget, findings } from './publication-check.mjs';
import { composeDefinition } from './workspace.mjs';

const source = dirname(dirname(fileURLToPath(import.meta.url)));
const exec = (cwd, command, args, ok = true) => {
  const result = spawnSync(command, args, { cwd, encoding: 'utf8' });
  if (ok) assert.equal(result.status, 0, `${command} ${args.join(' ')}\n${result.stdout}\n${result.stderr}`);
  return result;
};

test('recognizes tokens and private mock content without embedding operational secrets', () => {
  assert.deepEqual(findings('normal source'), []);
  assert.deepEqual(findings('ghp_' + 'a'.repeat(36)), ['GitHub token']);
  assert.equal(allowedTarget('refs/heads/main'), false);
  assert.equal(allowedTarget('refs/heads/release/1.0'), false);
  assert.equal(allowedTarget('refs/tags/v1.0'), false);
  assert.equal(allowedTarget('refs/heads/work/example'), true);
});

test('real Git and JJ pushes invoke the same checks; changed review, private mock, and main are rejected', { skip: process.platform !== 'linux' }, () => {
  const { repo, git, jj, base } = fixture();
  jj(['new', 'main', '-m', 'Fixture candidate']);
  writeFileSync(join(repo, 'example.txt'), 'safe change\n');
  jj(['bookmark', 'create', 'work/fixture', '-r', '@']);
  const head = () => jj(['log', '--no-graph', '-r', 'work/fixture', '-T', 'commit_id']).stdout.trim();
  const remoteHead = () => git(['ls-remote', '--refs', 'origin', 'refs/heads/work/fixture']).stdout.trim();
  const review = () => {
    const sha = head();
    exec(repo, 'node', ['scripts/publication-check.mjs', 'prepare', base, sha, 'origin', 'refs/heads/work/fixture']);
    const dir = join(repo, '.git/abservice-publication', sha);
    const record = JSON.parse(readFileSync(join(dir, 'review.template.json'), 'utf8'));
    // Only fixture data is marked reviewed by this test. Real publication requires content inspection.
    record.status = 'reviewed'; record.confidentiality = 'clear';
    record.files.forEach((file) => { file.finding = 'clear'; });
    record.validation = [{ command: 'fixture construction', result: 'passed' }];
    writeFileSync(join(dir, 'review.json'), JSON.stringify(record));
    return dir;
  };
  assert.notEqual(jj(['git', 'push', '--bookmark', 'work/fixture'], false).status, 0);
  assert.equal(remoteHead(), '');
  review();
  let safe = head();
  const accepted = jj(['git', 'push', '--bookmark', 'work/fixture']);
  assert.match(accepted.stderr, /Publication check passed/);
  assert.ok(remoteHead().startsWith(safe));
  jj(['new', '-m', 'Fixture safe update']);
  writeFileSync(join(repo, 'example.txt'), 'safe update\n');
  jj(['bookmark', 'set', 'work/fixture', '-r', '@']);
  review();
  safe = head();
  assert.match(jj(['git', 'push', '--bookmark', 'work/fixture']).stderr, /Publication check passed/);
  assert.ok(remoteHead().startsWith(safe));
  jj(['new', '-m', 'Fixture private candidate']);
  writeFileSync(join(repo, 'private.txt'), 'TOKEN=ghp_' + 'a'.repeat(36) + '\n');
  jj(['bookmark', 'set', 'work/fixture', '-r', '@']);
  review();
  const refused = jj(['git', 'push', '--bookmark', 'work/fixture'], false);
  assert.notEqual(refused.status, 0);
  assert.match(refused.stderr, /Possible confidential content/);
  assert.ok(remoteHead().startsWith(safe));
  const direct = git(['push', 'origin', `${head()}:refs/heads/work/fixture`], false);
  assert.notEqual(direct.status, 0);
  assert.match(direct.stderr, /Possible confidential content/);
  const protectedPush = git(['push', 'origin', `${head()}:refs/heads/main`], false);
  assert.notEqual(protectedPush.status, 0);
  assert.match(protectedPush.stderr, /Protected branch/);
  assert.ok(git(['ls-remote', '--refs', 'origin', 'refs/heads/main']).stdout.startsWith(base));
  writeFileSync(join(repo, 'example.txt'), 'changed after review\n');
  jj(['status']);
  assert.notEqual(jj(['git', 'push', '--bookmark', 'work/fixture'], false).status, 0);
  assert.ok(remoteHead().startsWith(safe));
});

function fixture() {
  const root = mkdtempSync(join(tmpdir(), 'abservice-publication-'));
  const repo = join(root, 'source');
  const remote = join(root, 'remote.git');
  mkdirSync(repo);
  const git = (args, ok) => exec(repo, 'git', ['-C', repo, ...args], ok);
  const jj = (args, ok) => exec(repo, 'jj', ['--repository', repo, ...args], ok);
  git(['init', '--initial-branch=main']);
  git(['config', '--local', 'user.name', 'Publication Fixture']);
  git(['config', '--local', 'user.email', 'fixture@example.test']);
  git(['config', '--local', 'abservice.publicEmail', 'fixture@example.test']);
  mkdirSync(join(repo, 'scripts'));
  mkdirSync(join(repo, '.githooks'));
  for (const file of ['publication-check.mjs', 'jj-git.mjs', 'setup-workspace-hooks.sh', 'setup-workspace-jj.sh', 'setup-git-hooks.sh']) cpSync(join(source, 'scripts', file), join(repo, 'scripts', file));
  writeFileSync(join(repo, '.githooks/pre-commit'), '#!/bin/sh\nexit 0\n');
  writeFileSync(join(repo, 'example.txt'), 'base\n');
  git(['add', '.']);
  git(['commit', '-m', 'Fixture base']);
  const base = git(['rev-parse', 'HEAD']).stdout.trim();
  git(['init', '--bare', '--initial-branch=main', remote]);
  git(['remote', 'add', 'origin', remote]);
  git(['push', 'origin', 'main']);
  exec(repo, 'jj', ['git', 'init', '--colocate']);
  exec(repo, 'bash', ['scripts/setup-git-hooks.sh']);
  return { repo, remote, git, jj, base };
}

test('tools startup configures a fresh JJ home from approved Git-local identity and shared checks', { skip: process.platform !== 'linux' }, () => {
  const { repo, git, jj } = fixture();
  const folder = dirname(repo);
  const tools = join(folder, 'tools-workspace');
  jj(['workspace', 'add', '--colocate', '--revision', 'main', tools]);
  const bin = join(folder, 'bin');
  mkdirSync(bin);
  writeFileSync(join(bin, 'sleep'), '#!/bin/sh\ntest "$1" = infinity\n', { mode: 0o755 });
  const env = { ...process.env, HOME: join(folder, 'tools-home'), XDG_CONFIG_HOME: join(folder, 'tools-config'), PATH: `${bin}:${process.env.PATH}` };
  delete env.JJ_CONFIG;
  const common = git(['rev-parse', '--path-format=absolute', '--git-common-dir']).stdout.trim();
  const hooks = join(common, 'abservice-hooks');
  const wrapper = join(hooks, 'jj-git.mjs');
  const state = { root: tools, git: common, shared: join(repo, '.jj/repo'), ports: {} };
  const [command, ...args] = composeDefinition(state).services.tools.command;
  const startup = () => spawnSync(command, args, { cwd: tools, env, encoding: 'utf8' });
  const get = (key) => spawnSync('jj', ['config', 'list', '--repo', '-T', 'value', key], { cwd: tools, env, encoding: 'utf8' });
  const settings = [
    ['user.name', 'Publication Fixture'],
    ['user.email', 'fixture@example.test'],
    ['git.executable-path', wrapper],
  ];
  const assertUnconfigured = () => {
    for (const [key] of settings) {
      const config = get(key);
      assert.equal(config.status, 0, config.stderr);
      assert.equal(config.stdout, '', key);
    }
  };
  const reject = (message) => {
    const result = startup();
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, message);
    assertUnconfigured();
  };
  assertUnconfigured();
  git(['config', '--local', '--unset', 'abservice.publicEmail']);
  reject(/approved Git-local/);
  git(['config', '--local', 'abservice.publicEmail', 'wrong@example.test']);
  reject(/approved Git-local/);
  git(['config', '--local', 'abservice.publicEmail', 'fixture@example.test']);
  git(['config', '--local', 'core.hooksPath', join(folder, 'not-the-shared-hooks')]);
  reject(/hook or JJ wrapper/);
  git(['config', '--local', 'core.hooksPath', hooks]);
  renameSync(wrapper, wrapper + '.saved');
  reject(/hook or JJ wrapper/);
  renameSync(wrapper + '.saved', wrapper);
  const result = startup();
  assert.equal(result.status, 0, result.stderr);
  for (const [key, value] of settings) {
    const config = get(key);
    assert.equal(config.status, 0, config.stderr);
    assert.equal(config.stdout.trim(), JSON.stringify(value));
  }
});

function reviewedFixture(repo, base, head, target) {
  exec(repo, 'node', ['scripts/publication-check.mjs', 'prepare', base, head, 'origin', target]);
  const dir = join(repo, '.git/abservice-publication', head);
  const record = JSON.parse(readFileSync(join(dir, 'review.template.json'), 'utf8'));
  // Synthetic local fixtures only; never used to approve repository publication.
  record.status = 'reviewed'; record.confidentiality = 'clear';
  record.files.forEach((file) => { file.finding = 'clear'; });
  record.validation = [{ command: 'fixture construction', result: 'passed' }];
  writeFileSync(join(dir, 'review.json'), JSON.stringify(record));
  return { dir, record };
}

test('a token introduced only by a merge and removed by a later merge is still reviewed and refused', { skip: process.platform !== 'linux' }, () => {
  const { repo, remote, git, base } = fixture();
  const tree = git(['rev-parse', `${base}^{tree}`]).stdout.trim();
  const commit = (content, parents, message) => git(['commit-tree', content, ...parents.flatMap((parent) => ['-p', parent]), '-m', message]).stdout.trim();
  const left = commit(tree, [base], 'Fixture left');
  const right = commit(tree, [base], 'Fixture right');
  const token = 'ghp_' + 'b'.repeat(36);
  writeFileSync(join(repo, 'merge-only.txt'), `${token}\n`);
  git(['add', 'merge-only.txt']);
  const privateTree = git(['write-tree']).stdout.trim();
  const firstMerge = commit(privateTree, [left, right], 'Fixture merge introduces private content');
  const other = commit(tree, [right], 'Fixture other side');
  const head = commit(tree, [firstMerge, other], 'Fixture merge removes private content');
  const target = 'refs/heads/work/merge-fixture';
  const { dir, record } = reviewedFixture(repo, base, head, target);
  assert.equal(readFileSync(join(dir, 'diff.patch'), 'utf8').includes(token), false);
  const refused = git(['push', 'origin', `${head}:${target}`], false);
  assert.notEqual(refused.status, 0, 'An intermediate merge blob must not reach the remote.');
  assert.match(refused.stderr, /Possible confidential content/);
  assert.equal(git(['ls-remote', '--refs', 'origin', target]).stdout.trim(), '');
  assert.ok(record.files.some((file) => file.path === 'merge-only.txt'));
  assert.ok(readFileSync(join(dir, 'history.patch'), 'utf8').includes(token));
  assert.notEqual(exec(repo, 'git', ['-C', remote, 'cat-file', '-e', firstMerge + ':merge-only.txt'], false).status, 0);
});

test('independent main advances keep reviewed JJ pushes valid, while task target changes still fail', { skip: process.platform !== 'linux' }, () => {
  const { repo, remote, git, jj, base } = fixture();
  const target = 'refs/heads/work/independent';
  const remoteGit = (args) => exec(repo, 'git', ['-C', remote, ...args]);
  const advanceMain = () => {
    const parent = remoteGit(['rev-parse', 'refs/heads/main']).stdout.trim();
    const next = remoteGit(['-c', 'user.name=Other Fixture', '-c', 'user.email=other@example.test', 'commit-tree', `${parent}^{tree}`, '-p', parent, '-m', 'Independent main change']).stdout.trim();
    remoteGit(['update-ref', 'refs/heads/main', next]);
    return next;
  };
  jj(['new', 'main', '-m', 'Fixture independent task']);
  writeFileSync(join(repo, 'example.txt'), 'independent change\n');
  jj(['bookmark', 'create', 'work/independent', '-r', '@']);
  const head = () => jj(['log', '--no-graph', '-r', 'work/independent', '-T', 'commit_id']).stdout.trim();
  const first = head();
  reviewedFixture(repo, base, first, target);
  advanceMain();
  const pushed = jj(['git', 'push', '--bookmark', 'work/independent']);
  assert.match(pushed.stderr, /Publication check passed/);
  assert.equal(head(), first);
  assert.equal(remoteGit(['rev-parse', target]).stdout.trim(), first);

  jj(['new', '-m', 'Fixture independent update']);
  writeFileSync(join(repo, 'example.txt'), 'independent update\n');
  jj(['bookmark', 'set', 'work/independent', '-r', '@']);
  const second = head();
  const newestMain = advanceMain();
  const { record } = reviewedFixture(repo, newestMain, second, target);
  assert.equal(record.base, base, 'A diverged public tip uses the common ancestor for content review.');
  assert.match(jj(['git', 'push', '--bookmark', 'work/independent']).stderr, /Publication check passed/);
  assert.equal(head(), second);

  jj(['new', '-m', 'Fixture target race']);
  writeFileSync(join(repo, 'example.txt'), 'next task change\n');
  jj(['bookmark', 'set', 'work/independent', '-r', '@']);
  const third = head();
  const unpublishedBase = exec(repo, 'node', ['scripts/publication-check.mjs', 'prepare', third, third, 'origin', target], false);
  assert.notEqual(unpublishedBase.status, 0, 'A private commit cannot exclude its own content from review.');
  reviewedFixture(repo, base, third, target);
  remoteGit(['update-ref', target, base]);
  const rejected = git(['push', 'origin', `${third}:${target}`], false);
  assert.notEqual(rejected.status, 0);
  assert.match(rejected.stderr, /Publication review does not match the exact push/);
  assert.equal(remoteGit(['rev-parse', target]).stdout.trim(), base);
});
