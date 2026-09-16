import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildPublic } from './build-public.mjs';
import { readGeneration } from './generation.mjs';
import { stack } from '../../e2e/src/support/config.ts';
import { seedDraftAlbum, seedDraftArticle, publishAlbum, publishArticle, unpublishAlbum,
  deleteAlbum, deleteArticle, upsertSiteContent } from '../../e2e/src/support/admin-api.ts';

// Runs after Playwright against its dedicated, already-started E2E database. No AWS calls.
const root = fileURLToPath(new URL('../../', import.meta.url));
const publicRoot = join(root, 'frontend-public/dist');
const metadataPath = join(mkdtempSync(join(tmpdir(), 'generation-acceptance-')), 'build.json');
const codeSha = execFileSync('git', ['-C', root, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
const generate = () => readGeneration(stack.backendBaseUrl);
const build = () => execFileSync(process.platform === 'win32' ? 'npm.cmd' : 'npm', ['run', 'build:public'], {
  cwd: root, stdio: 'inherit', shell: process.platform === 'win32',
  env: { ...process.env, API_BASE_URL: stack.backendBaseUrl, ASTRO_TELEMETRY_DISABLED: '1' },
});
const checkedBuild = (generatePages = build) => buildPublic({ codeSha, publicRoot, metadataPath,
  generation: generate, build: generatePages });
const html = (path) => readFileSync(join(publicRoot, path), 'utf8');
const albumId = await seedDraftAlbum({ title: 'Generation acceptance album', releaseDate: '2026-01-01',
  artistDisplayName: 'Acceptance artist', artistSortKey: 'acceptance', catalogNumber: `GEN-${Date.now()}` });
const articleId = await seedDraftArticle({ articleType: 'ALBUM', title: 'Generation acceptance article',
  body: 'Generation acceptance body', bodyFormat: 'PLAIN_TEXT', introShort: 'Generation acceptance introduction', albumId });
try {
  await publishAlbum(albumId); await publishArticle(articleId);
  const first = await checkedBuild();
  assert.equal(first.generation, await generate());
  assert.match(html(`articles/${articleId}/index.html`), /Generation acceptance body/);
  assert.ok(existsSync(join(publicRoot, `albums/${albumId}/index.html`)));
  assert.ok(html('articles/index.html').includes(articleId));

  // A completed Astro build is still rejected when a save happened before its closing generation read.
  await assert.rejects(checkedBuild(async () => {
    build();
    await upsertSiteContent({ key: 'test.generation', content: 'Changed during build', contentFormat: 'PLAIN_TEXT' });
  }), /changed during SSG/);
  assert.equal(existsSync(metadataPath), false);

  await unpublishAlbum(albumId);
  const withdrawn = await generate();
  assert.notEqual(withdrawn, first.generation);
  for (const path of [`/api/v1/albums/${albumId}`, `/api/v1/articles/${articleId}`]) {
    const response = await fetch(`${stack.backendBaseUrl}${path}`);
    assert.equal(response.status, 404);
    assert.ok(response.headers.get('content-type').startsWith('application/problem+json'));
  }
  const latest = await checkedBuild();
  assert.equal(latest.generation, withdrawn);
  assert.equal(existsSync(join(publicRoot, `albums/${albumId}/index.html`)), false);
  assert.equal(existsSync(join(publicRoot, `articles/${articleId}/index.html`)), false);
  const pages = latest.files.filter(({ path }) => path.endsWith('.html')).map(({ path }) => html(path));
  assert.ok(pages.every((page) => !page.includes(albumId) && !page.includes(articleId)),
    'Withdrawn references must disappear from home, lists, pagination and detail pages');
  console.log('Public generation acceptance passed: real save -> checked Astro build -> concurrent save rejected -> withdrawal and linked article removed.');
} finally {
  await deleteArticle(articleId);
  await deleteAlbum(albumId);
}
