// Run against the archive emitted by free-compatible.tftest.hcl (mock AWS,
// real archive provider). Source-module tests alone cannot catch missing files
// or wrong relative imports in the deployed zip.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

assert.ok(process.argv[2], 'Pass the directory containing the extracted test zip');
const directory = resolve(process.argv[2]);
const { handler } = await import(pathToFileURL(resolve(directory, 'index.mjs')));
const config = JSON.parse(await readFile(resolve(directory, 'security-config.json'), 'utf8'));
const storage = JSON.parse(await readFile(resolve(directory, 'origins.json'), 'utf8'));
assert.deepEqual(Object.values(config.origins).sort(), ['admin', 'api', 'assets', 'public']);
assert.equal(Object.keys(storage.origins).length, 2);
for (const [domain, kind] of Object.entries(config.origins)) {
  if (['public', 'admin'].includes(kind)) assert.ok(storage.origins[domain]);
  // Avoid a live S3 read. Static missing-artifact handling is covered by the
  // injected-storage tests; here the real packaged handler loads its config.
  for (const status of ['200', '403', '503']) {
    const event = { Records: [{ cf: {
      request: { method: 'GET', uri: '/test.html', origin: {
        [kind === 'api' ? 'custom' : 's3']: { domainName: domain, path: '' },
      } },
      response: { status, headers: { etag: [{ value: 'keep' }] } },
    } }] };
    const result = await handler(event);
    assert.equal(result.status, status);
    assert.equal(result.headers.etag[0].value, 'keep');
    assert.equal(result.headers['content-security-policy'][0].value, config.security.policies[kind]);
    assert.equal(result.headers['x-robots-tag']?.[0].value,
      config.noindex[kind] ? 'noindex, nofollow' : undefined);
  }
}
console.log('Packaged edge handler: imports, origin/config mapping and 12 responses passed');
