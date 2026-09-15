import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const validateGeneration = (value) => {
  assert.match(value ?? '', /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/, 'Missing or invalid public data generation');
  return value;
};

export const readGeneration = async (baseUrl, request = fetch) => {
  const origin = new URL(baseUrl);
  assert.ok(['https:', 'http:'].includes(origin.protocol) && !origin.username && !origin.password
    && origin.pathname === '/' && !origin.search && !origin.hash, 'API base must be an origin');
  const response = await request(new URL('/api/v1/public-data-generation', origin), {
    cache: 'no-store', redirect: 'error', signal: AbortSignal.timeout(15000),
    headers: { 'Cache-Control': 'no-cache' },
  });
  assert.equal(response.status, 200, `Generation query failed: HTTP ${response.status}`);
  assert.equal(response.headers.get('cache-control'), 'no-store', 'Generation response must not be cached');
  return validateGeneration((await response.json()).generation);
};

// Keep the synchronous AWS release boundary; the child owns fetch and its bounded timeout.
export const readGenerationSync = (baseUrl) => validateGeneration(execFileSync(process.execPath,
  [fileURLToPath(import.meta.url), baseUrl], { encoding: 'utf8', timeout: 20000 }).trim());

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  console.log(await readGeneration(process.argv[2]));
}
