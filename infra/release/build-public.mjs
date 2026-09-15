import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { filesUnder } from './frontend.mjs';
import { readGeneration, validateGeneration } from './generation.mjs';

export const artifactFiles = (root) => filesUnder(root).map((path) => ({ path,
  sha256: createHash('sha256').update(readFileSync(join(root, path))).digest('hex') }));

export const buildPublic = async ({ codeSha, publicRoot, metadataPath, generation, build }) => {
  rmSync(metadataPath, { force: true });
  assert.match(codeSha ?? '', /^[a-f0-9]{40}$/);
  const before = validateGeneration(await generation());
  await build();
  assert.equal(validateGeneration(await generation()), before, 'Public data changed during SSG; rebuild with current data');
  const record = { version: 1, codeSha, generation: before, files: artifactFiles(publicRoot) };
  assert.ok(record.files.some((file) => file.path === 'index.html'));
  assert.ok(record.files.some((file) => file.path === '404.html'));
  writeFileSync(metadataPath, JSON.stringify(record, null, 2));
  return record;
};

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await buildPublic({ codeSha: process.env.RELEASE_SHA, publicRoot: 'frontend-public/dist',
    metadataPath: 'public-build.json', generation: () => readGeneration(process.env.API_BASE_URL),
    build: () => execFileSync(process.platform === 'win32' ? 'npm.cmd' : 'npm', ['run', 'build:public'],
      { stdio: 'inherit', shell: process.platform === 'win32' }) });
}
