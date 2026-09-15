import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { deploymentDecision, gitAncestor } from './frontend.mjs';

test('real Git history permits initial/equal/forward/revert and skips stale, rejecting divergence and missing commits', (t) => {
  const cwd = mkdtempSync(join(tmpdir(), 'release-history-'));
  t.after(() => rmSync(cwd, { recursive: true, force: true }));
  const git = (...args) => execFileSync('git', ['-c', 'user.name=Release test', '-c', 'user.email=release@example.invalid',
    '-c', 'commit.gpgsign=false', ...args], { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
  git('init', '--initial-branch=main');
  const commit = (name) => {
    writeFileSync(join(cwd, 'value'), name);
    git('add', 'value'); git('commit', '-m', name);
    return git('rev-parse', 'HEAD');
  };
  const a = commit('A'); const b = commit('B'); const c = commit('C');
  git('revert', '--no-edit', c);
  const revert = git('rev-parse', 'HEAD');
  git('checkout', '-b', 'fork', a);
  const fork = commit('fork');
  const record = (codeSha) => {
    const entry = { codeSha, releaseId: '100-1', files: [{ path: 'index.html', sha256: 'a'.repeat(64) }] };
    return { version: 1, public: entry, admin: entry };
  };
  const ancestor = (older, newer) => gitAncestor(older, newer, cwd);
  assert.equal(deploymentDecision(null, a, ancestor).deploy, true);
  assert.equal(deploymentDecision(record(c), c, ancestor).deploy, true);
  assert.equal(deploymentDecision(record(b), c, ancestor).deploy, true);
  assert.equal(deploymentDecision(record(c), b, ancestor).deploy, false);
  assert.equal(deploymentDecision(record(c), revert, ancestor).deploy, true);
  assert.throws(() => deploymentDecision(record(c), fork, ancestor), /diverged/);
  assert.throws(() => deploymentDecision(record('f'.repeat(40)), c, ancestor), /Cannot compare/);
  assert.throws(() => deploymentDecision({ ...record(c), admin: record(b).admin }, c, ancestor), /generations differ/);
});
