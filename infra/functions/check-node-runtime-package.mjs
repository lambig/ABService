// Check the actual Terraform archive in a fresh process, as on a cold start.
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';

assert.ok(process.argv[2], 'Pass the directory containing the extracted test zip');
const result = spawnSync(process.execPath, ['--input-type=module', '--eval', `
  import { pathToFileURL } from 'node:url';
  const entry = pathToFileURL(process.argv[1]).href;
  const { handler } = await import(entry);
  if (typeof handler !== 'function') throw new Error('Missing Lambda handler');
  await import(entry);
`, resolve(process.argv[2], 'index.mjs')], { encoding: 'utf8', timeout: 10000 });
assert.ifError(result.error);
assert.equal(result.status, 0, result.stderr);
assert.equal(result.stderr, '');
assert.equal(result.stdout.trim(), `NODE_RUNTIME_VERSION ${process.version}`,
  'The packaged entry must log only the marker and actual Node version exactly once');
console.log('Packaged runtime observation: exact version logged once across repeated imports');
