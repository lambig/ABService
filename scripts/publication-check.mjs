#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const zero = '0'.repeat(40);
const hash = (value) => createHash('sha256').update(value).digest('hex');
function git(args, options = {}) {
  const result = spawnSync('/usr/bin/git', ['--no-replace-objects', '-C', process.cwd(), ...args], { encoding: 'utf8', maxBuffer: 128 * 1024 * 1024, ...options });
  if (result.error || result.status !== 0) throw new Error(`Git check failed: ${result.error?.message ?? result.stderr}`);
  return result.stdout;
}
export function findings(text) {
  const rules = [
    ['GitHub token', /\b(?:gh[pousr]_[A-Za-z0-9]{36,255}|github_pat_[A-Za-z0-9_]{60,255})\b/],
    ['AWS access key', /\b(?:AKIA|ASIA)[A-Z0-9]{16}\b/],
    ['private key', /-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----/],
    ['credential URL', /https?:\/\/[^\s/:]+:[^\s/@]+@[^\s/]+/],
  ];
  return rules.filter(([, pattern]) => pattern.test(text)).map(([name]) => name);
}
export function allowedTarget(ref) {
  return /^refs\/heads\/(?:feat|fix|chore|docs|refactor|test|work)\/[A-Za-z0-9][A-Za-z0-9._/-]*$/.test(ref) && !ref.includes('..');
}
function evidence(base, head) {
  if (!/^[0-9a-f]{40}$/.test(base) || !/^[0-9a-f]{40}$/.test(head)) throw new Error('Use full base/head commit IDs.');
  git(['merge-base', '--is-ancestor', base, head]);
  const diff = git(['diff', '--binary', '--no-ext-diff', '--no-textconv', base, head, '--']);
  const history = git(['log', '--format=fuller', '--binary', '--no-ext-diff', '--no-textconv', '--root', '--diff-merges=separate', '-p', `${base}..${head}`, '--']);
  const paths = [...new Set(git(['log', '--format=', '--root', '--diff-merges=separate', '--name-only', '-z', `${base}..${head}`, '--']).split(/[\0\n]/).filter(Boolean))].sort();
  return { diff, history, paths, diffSha256: hash(diff), historySha256: hash(history) };
}
// The review base must already be public, but an unrelated main advance does not
// change the reviewed task history. Fetch a missing tip without moving any ref.
function verifyPublicBase(base, remote) {
  if (!/^[0-9a-f]{40}$/.test(base)) throw new Error('Use a full public base commit ID.');
  const tip = git(['ls-remote', '--refs', remote, 'refs/heads/main']).trim().split(/\s+/)[0];
  if (!/^[0-9a-f]{40}$/.test(tip)) throw new Error('Remote main is unavailable.');
  const local = spawnSync('/usr/bin/git', ['--no-replace-objects', '-C', process.cwd(), 'cat-file', '-e', `${tip}^{commit}`], { stdio: 'ignore' });
  if (local.status !== 0) git(['fetch', '--no-tags', '--no-write-fetch-head', remote, tip]);
  git(['merge-base', '--is-ancestor', base, tip]);
}
function directory(head) {
  const common = git(['rev-parse', '--path-format=absolute', '--git-common-dir']).trim();
  return join(common, 'abservice-publication', head);
}
function publicIdentity(base, head) {
  const expected = git(['config', '--local', '--get', 'abservice.publicEmail']).trim();
  const email = git(['config', '--local', '--get', 'user.email']).trim();
  const name = git(['config', '--local', '--get', 'user.name']).trim();
  if (!expected || email !== expected || !name) throw new Error('Local public identity is missing or inconsistent.');
  const authors = git(['log', '--format=%ae%n%ce', `${base}..${head}`]).trim().split('\n');
  if (authors.some((author) => author !== expected)) throw new Error('Outgoing commit identity differs from the reviewed local public identity.');
}
function prepare([base, head, remote, target]) {
  if (!allowedTarget(target)) throw new Error('Only a task branch can be published by this entry point.');
  if (!/^[0-9a-f]{40}$/.test(head)) throw new Error('Use a full head commit ID.');
  verifyPublicBase(base, remote);
  base = git(['merge-base', base, head]).trim();
  const data = evidence(base, head);
  const dir = directory(head);
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, 'diff.patch'), data.diff);
  writeFileSync(join(dir, 'history.patch'), data.history);
  const remoteUrl = git(['remote', 'get-url', '--push', remote]).trim();
  const targetBefore = git(['ls-remote', '--refs', remote, target]).trim().split(/\s+/)[0] || zero;
  const record = {
    status: 'unreviewed', base, head, remote, remoteUrl, target, targetBefore,
    diffSha256: data.diffSha256, historySha256: data.historySha256,
    files: data.paths.map((path) => ({ path, finding: 'unreviewed' })),
    confidentiality: 'unreviewed', validation: [], materials: [],
  };
  const template = join(dir, 'review.template.json');
  writeFileSync(template, `${JSON.stringify(record, null, 2)}\n`);
  console.log(`Inspect diff.patch and history.patch completely; write review.json beside ${template}.`);
}
function check(remote, remoteUrl, input) {
  const rows = input.trim().split('\n').filter(Boolean);
  if (!rows.length) return;
  for (const row of rows) {
    const [, head, target, targetBefore] = row.trim().split(/\s+/);
    if (!allowedTarget(target) || head === zero) throw new Error('Protected branch, tag, or deletion: separate authorization path required.');
    const dir = directory(head);
    const path = join(dir, 'review.json');
    if (!existsSync(path)) throw new Error(`Missing publication review: ${path}`);
    const review = JSON.parse(readFileSync(path, 'utf8'));
    if (review.status !== 'reviewed' || review.confidentiality !== 'clear' || review.head !== head ||
        review.remote !== remote || review.remoteUrl !== remoteUrl || review.target !== target || review.targetBefore !== targetBefore) {
      throw new Error('Publication review does not match the exact push.');
    }
    verifyPublicBase(review.base, remote);
    const data = evidence(review.base, head);
    if (data.diffSha256 !== review.diffSha256 || data.historySha256 !== review.historySha256 ||
        hash(readFileSync(join(dir, 'diff.patch'))) !== data.diffSha256 ||
        hash(readFileSync(join(dir, 'history.patch'))) !== data.historySha256) throw new Error('Publication evidence changed.');
    if (JSON.stringify(review.files?.map((file) => file.path)) !== JSON.stringify(data.paths) ||
        review.files.some((file) => file.finding !== 'clear')) throw new Error('File review coverage is incomplete.');
    if (!Array.isArray(review.validation) || !review.validation.length ||
        review.validation.some((entry) => !entry.command || entry.result !== 'passed')) throw new Error('Validation evidence is incomplete.');
    for (const material of review.materials ?? []) {
      if (!material.path || material.finding !== 'clear' || hash(readFileSync(material.path)) !== material.sha256) {
        throw new Error('Publication material changed or is not reviewed.');
      }
    }
    const detected = findings(data.history);
    if (detected.length) throw new Error(`Possible confidential content: ${detected.join(', ')}. Nothing sent.`);
    publicIdentity(review.base, head);
    console.error(`Publication check passed: ${review.base}..${head} -> ${remote}:${target}`);
  }
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const [command, ...args] = process.argv.slice(2);
    if (command === 'prepare') prepare(args);
    else if (command === 'check') check(args[0], args[1], readFileSync(0, 'utf8'));
    else throw new Error('Use prepare <base> <head> <remote> <target-ref> or check <remote> <url>.');
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
