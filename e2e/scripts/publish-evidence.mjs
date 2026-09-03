#!/usr/bin/env node

/**
 * レビュー証跡（画像）を `evidence` ブランチへ公開する。
 *
 * main には証跡を入れない。孤立ブランチへ PR 番号ごとに置き、PR 本文から raw URL で参照する（#164）。
 * マージ直前に消す運用は取らず、閉じた PR の分を不定期にまとめて掃除する。
 *
 *   npm run evidence:publish -- --pr 249
 *
 * 掃除は `evidence` ブランチから該当ディレクトリを削除して push する。溜まったらブランチごと作り直す
 * （手順は CONTRIBUTION.md）。
 */

import { execFileSync } from 'node:child_process';
import { cpSync, existsSync, mkdirSync, readdirSync, rmSync } from 'node:fs';
import { join } from 'node:path';

const EVIDENCE_BRANCH = 'evidence';
const WORKTREE_PATH = '.git/evidence-worktree';
const EVIDENCE_DIR = 'e2e/evidence';

const repositoryRoot = new URL('../../', import.meta.url).pathname;

const git = (args, cwd = repositoryRoot) =>
  execFileSync('git', args, { cwd, encoding: 'utf8' }).trim();

const prNumberOf = (argv) => {
  const index = argv.indexOf('--pr');
  const value = index === -1 ? undefined : argv[index + 1];
  return value === undefined
    ? fail('PR 番号を指定してください: npm run evidence:publish -- --pr <番号>')
    : value;
};

const fail = (message) => {
  console.error(message);
  process.exit(1);
};

const imagesIn = (dir) =>
  existsSync(dir) ? readdirSync(dir).filter((name) => name.endsWith('.png')) : [];

/** `git@github.com:owner/repo.git` と `https://github.com/owner/repo.git` の両方から owner/repo を取る */
const repositorySlug = () => {
  const url = git(['remote', 'get-url', 'origin']);
  const matched = /github\.com[:/](?<slug>[^/]+\/[^/]+?)(?:\.git)?$/u.exec(url);
  return matched?.groups?.slug ?? fail(`origin の URL から owner/repo を取れません: ${url}`);
};

const prepareWorktree = () => {
  rmSync(join(repositoryRoot, WORKTREE_PATH), { recursive: true, force: true });
  git(['worktree', 'prune']);

  const remoteExists = git(['ls-remote', '--heads', 'origin', EVIDENCE_BRANCH]).length > 0;

  remoteExists
    ? git(['worktree', 'add', '-B', EVIDENCE_BRANCH, WORKTREE_PATH, `origin/${EVIDENCE_BRANCH}`])
    : createOrphanWorktree();
};

/*
 * ORPHAN-BRANCH: 証跡は main の履歴と無関係に置く。孤立ブランチにすることで、掃除のときに
 * ブランチごと作り直せば過去のオブジェクトも残らない。
 */
const createOrphanWorktree = () => {
  git(['worktree', 'add', '--detach', WORKTREE_PATH]);
  const worktree = join(repositoryRoot, WORKTREE_PATH);
  git(['checkout', '--orphan', EVIDENCE_BRANCH], worktree);
  git(['rm', '-rf', '--quiet', '.'], worktree);
};

const publish = (prNumber, images) => {
  const worktree = join(repositoryRoot, WORKTREE_PATH);
  const prDirectory = `pr-${prNumber}`;
  const destination = join(worktree, prDirectory);

  rmSync(destination, { recursive: true, force: true });
  mkdirSync(destination, { recursive: true });

  images.forEach((name) => {
    cpSync(join(repositoryRoot, EVIDENCE_DIR, name), join(destination, name));
  });

  git(['add', prDirectory], worktree);
  git(['commit', '-m', `evidence: PR #${prNumber}`], worktree);
  git(['push', '--force-with-lease', 'origin', EVIDENCE_BRANCH], worktree);
};

const printMarkdown = (prNumber, images) => {
  const slug = repositorySlug();
  console.log('\nPR 本文へ貼る Markdown:\n');
  images.forEach((name) => {
    const url = `https://raw.githubusercontent.com/${slug}/${EVIDENCE_BRANCH}/pr-${prNumber}/${name}`;
    console.log(`![${name.replace(/\.png$/u, '')}](${url})`);
  });
  console.log('');
};

const main = () => {
  const prNumber = prNumberOf(process.argv.slice(2));
  const images = imagesIn(join(repositoryRoot, EVIDENCE_DIR)).toSorted();

  images.length === 0
    ? fail(
        `公開する画像がありません（${EVIDENCE_DIR}）。先に npm run test:e2e を実行してください。`,
      )
    : undefined;

  prepareWorktree();
  publish(prNumber, images);
  git(['worktree', 'remove', '--force', WORKTREE_PATH]);

  printMarkdown(prNumber, images);
};

main();
