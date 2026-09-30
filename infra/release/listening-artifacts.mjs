import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { copyFileSync, mkdirSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const hash = (bytes) => createHash('sha256').update(bytes).digest('hex');
export const listeningFiles = (root) => {
  const worker = readFileSync(join(root, 'sw.js'), 'utf8');
  const match = worker.match(/^const SHELL = (\{[^\n]+\});\r?\n/);
  assert.ok(match, 'Missing listening shell manifest');
  const shell = JSON.parse(match[1]);
  assert.match(shell.revision ?? '', /^[a-f0-9]{64}$/);
  assert.ok(Array.isArray(shell.entries) && shell.entries.length > 0);
  const prefix = `releases/${shell.revision}/`;
  const paths = shell.entries.map((entry) => {
    assert.ok(typeof entry.path === 'string' && entry.path.startsWith(prefix));
    assert.match(entry.path.slice(prefix.length), /^(?:index\.html|assets\/[a-zA-Z0-9_.\/-]+)$/);
    assert.ok(!entry.path.split('/').some((part) => ['', '.', '..'].includes(part)));
    assert.match(entry.sha256 ?? '', /^[a-f0-9]{64}$/);
    assert.equal(hash(readFileSync(join(root, entry.path))), entry.sha256, 'Listening shell checksum mismatch');
    return entry.path;
  });
  assert.equal(new Set(paths).size, paths.length, 'Duplicate shell path');
  assert.ok(paths.includes(`${prefix}index.html`));
  assert.deepEqual(readFileSync(join(root, 'index.html')), readFileSync(join(root, `${prefix}index.html`)),
    'Listening entry point differs from shell generation');
  return ['index.html', 'sw.js', ...paths].sort();
};

// Build output contains preview fixtures and unversioned assets. Publish only
// the verified shell, never the preview's audio/ or distribution fixture.
export const stageListening = (source, target) => {
  const paths = listeningFiles(source);
  mkdirSync(target, { recursive: true });
  assert.equal(readdirSync(target).length, 0, 'Listening staging directory must be empty');
  for (const path of paths) {
    mkdirSync(dirname(join(target, path)), { recursive: true });
    copyFileSync(join(source, path), join(target, path));
  }
};

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  assert.equal(process.argv.length, 4, 'Use listening-artifacts.mjs source target');
  stageListening(process.argv[2], process.argv[3]);
}
