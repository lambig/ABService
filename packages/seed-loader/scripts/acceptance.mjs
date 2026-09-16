#!/usr/bin/env node
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { adminApi } from 'abservice-admin-api';

/**
 * ローダの受け入れ。実バックエンド（E2E 用スタック）に対して、空の状態から通しで流す。
 *
 * 見るのは #373 の受け入れ条件のうち機構で確かめられるもの:
 *   - dry-run は書き込みを送らない
 *   - 文言・作品（曲目・外部音源・画像）・記事（作品参照・タグ）が管理API経由で入り、公開まで進む
 *   - 途中で止まった状態（作品だけ入った／公開と画像が付かなかった）から、再実行が続きを埋める
 *   - もう一度流しても何も変わらない（冪等）
 *   - 記事の組み立ての途中でバックエンドが断っても下書きは残らず、直して再実行すると作成から通る
 *   - 投入ファイルの誤りと、解けない参照は、送る前に落ちる
 *
 * 実内容でのリハーサルは運用リポジトリの手順が持つ。ここで使う内容は合成で、終わったら消す。
 * 文言には削除の経路が無いため、専用の接頭辞のキーだけを使い、残っても他へ影響しないようにする。
 */

const baseUrl =
  process.env.SEED_API_BASE_URL ?? process.env.E2E_BACKEND_BASE_URL ?? 'http://localhost:8090';
const apiKey = process.env.ADMIN_API_KEY ?? 'dev-admin-api-key';
const api = adminApi({ baseUrl, apiKey });
const cli = join(fileURLToPath(new URL('..', import.meta.url)), 'src/cli.ts');

const CATALOG_PREFIX = 'SEED-ACC-';
const catalog = { showcase: `${CATALOG_PREFIX}0001`, draft: `${CATALOG_PREFIX}0002` };
const titles = {
  album: 'Seed acceptance: album article',
  note: 'Seed acceptance: note',
  draft: 'Seed acceptance: draft',
};
// キーは小文字の区切りをドットで繋いだ形（バックエンドの SiteContentKey）。ハイフンは通らない
const siteKeys = { plain: 'acceptance.seed.plain', markdown: 'acceptance.seed.markdown' };

// 96×96 の単色 PNG。E2E のカバー画像（e2e/src/support/cover-image.ts）と同じ実体で、
// バックエンドが先頭バイト列で形式を判定するため本物の署名を持つ必要がある。
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAGAAAABgCAIAAABt+uBvAAAAjklEQVR42u3QMQ0AAAgDsAnAFNZRhgNOriZV0EwXhygQJEiQIEGCBAlCkCBBggQJEiQIQYIECRIkSJAgBAkSJEiQIEGCBCFIkCBBggQJEoQgQYIECRIkSBCCBAkSJEiQIEGCECRIkCBBggQJQpAgQYIECRIkCEGCBAkSJEiQIEEIEiRIkCBBggQhSJCgPwtMwB47NN8y0AAAAABJRU5ErkJggg==',
  'base64',
);

const writeTree = (files) => {
  const root = mkdtempSync(join(tmpdir(), 'seed-acceptance-'));
  Object.entries(files).forEach(([path, content]) => {
    mkdirSync(dirname(join(root, path)), { recursive: true });
    writeFileSync(join(root, path), content);
  });
  return root;
};

const siteAndAlbums = {
  [`site/${siteKeys.plain}.txt`]: 'Seed acceptance plain text\n',
  [`site/${siteKeys.markdown}.md`]: '## Seed acceptance\n\nMarkdown body\n',
  [`albums/${catalog.showcase}/album.json`]: JSON.stringify({
    title: 'Seed acceptance showcase',
    releaseDate: '2026-02-01',
    artistDisplayName: 'Seed acceptance artist',
    artistSortKey: 'seed acceptance',
    event: { name: 'Seed acceptance event', date: '2026-01-31', place: 'Hall', spaceNumber: 'A-1' },
    basePrice: { amount: 1200 },
    originalWorkNote: 'Seed acceptance original work',
    tracks: [
      {
        title: 'Titled track',
        tunes: [{ tuneTitle: 'Tune one', composerCreditOverride: 'Trad.' }],
      },
      { tunes: [{ tuneTitle: 'Tune two' }, { tuneTitle: 'Tune three' }] },
    ],
    externalAudioUrls: ['https://soundcloud.com/seed-acceptance/showcase'],
    coverImage: 'cover.png',
    published: true,
  }),
  [`albums/${catalog.showcase}/description.md`]: '## Showcase\n\nDescribed in Markdown.\n',
  [`albums/${catalog.showcase}/cover.png`]: PNG,
  [`albums/${catalog.draft}/album.json`]: JSON.stringify({
    title: 'Seed acceptance draft album',
    releaseDate: '2026-02-02',
    artistDisplayName: 'Seed acceptance artist',
    artistSortKey: 'seed acceptance draft',
    published: false,
  }),
};

const articles = {
  'articles/01-note/article.json': JSON.stringify({
    articleType: 'NOTE',
    title: titles.note,
    introShort: 'A note loaded by the acceptance run.',
    published: true,
  }),
  'articles/01-note/body.txt': 'Plain body.\n',
  'articles/02-album/article.json': JSON.stringify({
    articleType: 'ALBUM',
    title: titles.album,
    introShort: 'Introduces the showcase album.',
    album: catalog.showcase,
    tags: ['seed', 'acceptance'],
    published: true,
  }),
  'articles/02-album/body.md': '## About the album\n\nLoaded through the admin API.\n',
  'articles/03-draft/article.json': JSON.stringify({
    articleType: 'NEWS',
    title: titles.draft,
    published: false,
  }),
};

const load = (dir, ...flags) => {
  const result = spawnSync(process.execPath, [cli, '--dir', dir, ...flags], {
    encoding: 'utf8',
    env: { ...process.env, SEED_API_BASE_URL: baseUrl, ADMIN_API_KEY: apiKey },
  });
  return { status: result.status, stdout: result.stdout, stderr: result.stderr };
};

const succeeded = (run, label) => {
  assert.equal(run.status, 0, `${label}: ${run.stdout}\n${run.stderr}`);
  return run;
};

const albumDetail = async (catalogNumber) => {
  const album = await api.findAlbumByCatalogNumber(catalogNumber);
  return album === undefined ? undefined : api.getAdminAlbumDetail(album.albumId);
};

const articleDetail = async (title) => {
  const article = await api.findArticleByTitle(title);
  return article === undefined ? undefined : api.getAdminArticleDetail(article.articleId);
};

const siteContent = async (key) => (await api.listSiteContents()).find((item) => item.key === key);

const cleanup = async () => {
  for (const title of Object.values(titles)) {
    const article = await api.findArticleByTitle(title);
    await (article === undefined ? Promise.resolve() : api.deleteArticle(article.articleId));
  }
  for (const album of await api.findAlbumsByCatalogNumberPrefix(CATALOG_PREFIX)) {
    await api.deleteAlbum(album.albumId);
  }
};

await cleanup();
// 文言は削除できないため、前回の実行が残していれば「登録済み」として飛ばされる。初回と2回目以降で
// 期待する件数が変わる分だけを、実行前の状態から決める
const siteContentsAlreadyLoaded = (await api.listSiteContents()).filter((item) =>
  Object.values(siteKeys).includes(item.key),
).length;
try {
  // 1. dry-run は計画を出すだけで、何も作らない
  const dryRun = succeeded(
    load(writeTree({ ...siteAndAlbums, ...articles }), '--dry-run'),
    'dry-run',
  );
  assert.match(dryRun.stdout, new RegExp(`${catalog.showcase}: 作成 → 公開`, 'u'));
  assert.match(dryRun.stdout, new RegExp(`${catalog.draft}: 作成$`, 'mu'));
  assert.match(dryRun.stdout, /dry-run のため書き込みは送っていません/u);
  assert.equal(await albumDetail(catalog.showcase), undefined);
  assert.equal(await articleDetail(titles.note), undefined);

  // 2. 記事へ進む前に止まった実行を模す（文言と作品だけ）
  const partial = succeeded(load(writeTree(siteAndAlbums)), 'partial run');
  assert.match(
    partial.stdout,
    new RegExp(
      `完了: 作成 ${String(4 - siteContentsAlreadyLoaded)} / 公開 1 / 画像 0 / 飛ばした ${String(siteContentsAlreadyLoaded)}`,
      'u',
    ),
  );
  const showcase = await albumDetail(catalog.showcase);
  assert.ok(showcase.publishedAt !== null, 'showcase must be published');
  assert.ok(showcase.coverImageKey !== null, 'showcase must have a confirmed cover image');
  assert.equal(showcase.description, '## Showcase\n\nDescribed in Markdown.');
  assert.equal(showcase.descriptionFormat, 'MARKDOWN');
  assert.equal(showcase.tracks.length, 2);
  assert.deepEqual(
    showcase.tracks.map((track) => track.tunes.map((tune) => tune.tuneTitle)),
    [['Tune one'], ['Tune two', 'Tune three']],
  );
  assert.equal(showcase.externalAudios.length, 1);
  assert.equal(showcase.basePrice.amount, 1200);
  assert.equal(showcase.eventName, 'Seed acceptance event');
  const draft = await albumDetail(catalog.draft);
  assert.equal(draft.publishedAt, null);
  assert.equal(draft.coverImageKey, null);
  assert.equal((await siteContent(siteKeys.plain)).content, 'Seed acceptance plain text');
  assert.equal((await siteContent(siteKeys.markdown)).contentFormat, 'MARKDOWN');

  // 3. 「作ったが公開と画像が付かなかった」状態を作り、再実行が埋めることを見る
  await api.unpublishAlbum(showcase.albumId);
  await api.setAlbumCoverImage(showcase.albumId, null);
  const resumed = succeeded(load(writeTree({ ...siteAndAlbums, ...articles })), 'resumed run');
  assert.match(resumed.stdout, new RegExp(`${catalog.showcase}: 画像を付けた・公開した`, 'u'));
  assert.match(resumed.stdout, new RegExp(`${catalog.draft}: 飛ばした`, 'u'));
  assert.match(resumed.stdout, /完了: 作成 3 \/ 公開 3 \/ 画像 1 \/ 飛ばした 3/u);
  const repaired = await albumDetail(catalog.showcase);
  assert.ok(repaired.publishedAt !== null);
  assert.ok(repaired.coverImageKey !== null);
  assert.equal(repaired.tracks.length, 2, 'children survive the cover image repair');
  const albumArticle = await articleDetail(titles.album);
  assert.equal(albumArticle.albumId, repaired.albumId);
  assert.deepEqual(albumArticle.tags.map((tag) => tag.name).sort(), ['acceptance', 'seed']);
  assert.ok(albumArticle.publishedAt !== null);
  assert.equal(albumArticle.body, '## About the album\n\nLoaded through the admin API.');
  assert.ok((await articleDetail(titles.note)).publishedAt !== null);
  assert.equal((await articleDetail(titles.draft)).publishedAt, null);

  // 4. もう一度流しても、投入先は変わらない
  const before = {
    showcase: repaired,
    draft: await albumDetail(catalog.draft),
    albumArticle,
    note: await articleDetail(titles.note),
    draftArticle: await articleDetail(titles.draft),
    plain: await siteContent(siteKeys.plain),
    markdown: await siteContent(siteKeys.markdown),
    articles: await api.countArticles(),
  };
  const again = succeeded(load(writeTree({ ...siteAndAlbums, ...articles })), 'second run');
  assert.match(again.stdout, /完了: 作成 0 \/ 公開 0 \/ 画像 0 \/ 飛ばした 7/u);
  assert.doesNotMatch(again.stdout, /作成した|公開した|画像を付けた|登録した/u);
  assert.deepEqual(
    {
      showcase: await albumDetail(catalog.showcase),
      draft: await albumDetail(catalog.draft),
      albumArticle: await articleDetail(titles.album),
      note: await articleDetail(titles.note),
      draftArticle: await articleDetail(titles.draft),
      plain: await siteContent(siteKeys.plain),
      markdown: await siteContent(siteKeys.markdown),
      articles: await api.countArticles(),
    },
    before,
  );

  // 5. 記事の組み立ての途中（2件目のタグ）でバックエンドが断ると、下書きは残らず、直して再実行すると作成から通る
  const rejectedTagTitle = 'Seed acceptance: rejected tag';
  const rejectedTagArticle = (tag) => ({
    'articles/01-rejected/article.json': JSON.stringify({
      articleType: 'ALBUM',
      title: rejectedTagTitle,
      album: catalog.showcase,
      tags: ['accepted', tag],
      published: true,
    }),
  });
  const stoppedInsideArticle = load(writeTree(rejectedTagArticle('x'.repeat(101))));
  assert.equal(stoppedInsideArticle.status, 1);
  assert.match(
    stoppedInsideArticle.stderr,
    /記事「Seed acceptance: rejected tag」 の作成 で止まりました/u,
  );
  assert.match(stoppedInsideArticle.stderr, /HTTP 400/u);
  assert.equal(
    await articleDetail(rejectedTagTitle),
    undefined,
    'the partial draft must be rolled back',
  );
  const retried = succeeded(
    load(writeTree(rejectedTagArticle('fixed'))),
    'retry after fixing the tag',
  );
  assert.match(retried.stdout, new RegExp(`${rejectedTagTitle}: 作成 → 公開`, 'u'));
  const retriedArticle = await articleDetail(rejectedTagTitle);
  assert.equal(retriedArticle.albumId, repaired.albumId);
  assert.deepEqual(retriedArticle.tags.map((tag) => tag.name).sort(), ['accepted', 'fixed']);
  assert.ok(retriedArticle.publishedAt !== null);
  await api.deleteArticle(retriedArticle.articleId);

  // 6. 誤りは送る前に落ちる
  const brokenFile = load(writeTree({ 'albums/SEED-ACC-BROKEN/album.json': '{ "title": ' }));
  assert.equal(brokenFile.status, 1);
  assert.match(brokenFile.stderr, /投入ファイルの誤り/u);
  const danglingReference = load(
    writeTree({
      'articles/x/article.json': JSON.stringify({
        articleType: 'ALBUM',
        title: 'Seed acceptance: dangling',
        album: 'SEED-ACC-NOWHERE',
        published: true,
      }),
    }),
  );
  assert.equal(danglingReference.status, 1);
  assert.match(danglingReference.stderr, /計画に問題があるため実行しません/u);
  assert.equal(await articleDetail('Seed acceptance: dangling'), undefined);
  assert.equal(await albumDetail('SEED-ACC-BROKEN'), undefined);

  console.log(
    'Seed loader acceptance passed: dry-run without writes -> partial load -> resume fills publish/cover/articles -> second run unchanged -> article rejected mid-assembly leaves no draft and succeeds once fixed -> malformed file and dangling reference rejected before any write.',
  );
} finally {
  await cleanup();
}
