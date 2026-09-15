import assert from 'node:assert/strict';

import {
  findAlbumByCatalogNumber,
  findArticleByTitle,
  publishArticle,
  seedDraftArticle,
  setAlbumCoverImage,
} from '../src/support/admin-api.ts';
import {
  pagination,
  retiredPaginationArticleTitle,
  seedForBuild,
  showcase,
} from '../src/support/build-fixtures.ts';
import { coverImageAsset } from '../src/support/cover-image.ts';
import { stack } from '../src/support/config.ts';

const get = async (path) => {
  const response = await fetch(`${stack.backendBaseUrl}${path}`, {
    headers: { Authorization: `Bearer ${stack.adminApiKey}` },
  });
  assert.equal(response.status, 200, path);
  return response.json();
};

// Compare all unrelated fields, including child IDs, order, event and publication.
const withoutCoverRevision = ({ revision, coverImageKey, coverImageUrl, ...rest }) => rest;

/** Runs before SSG against the same live backend/DB/MinIO, without restarting or clearing it. */
export const verifyBuildFixtureReuse = async () => {
  const album = await findAlbumByCatalogNumber(showcase.catalogNumber);
  assert.ok(album);
  const path = `/api/v1/admin/albums/${album.albumId}`;
  const before = await get(path);
  assert.ok(before.tracks.length > 0 && before.externalAudios.length > 0);
  assert.ok(before.coverImageKey);

  // Reproduce the pre-PR database: showcase exists with no cover, plus old filler 18.
  await setAlbumCoverImage(album.albumId, null);
  const legacy = await get(path);
  assert.equal(legacy.coverImageKey, null);
  assert.deepEqual(withoutCoverRevision(legacy), withoutCoverRevision(before));
  await publishArticle(
    await seedDraftArticle({
      articleType: 'NOTE',
      title: retiredPaginationArticleTitle,
    }),
  );

  await seedForBuild();
  const repaired = await get(path);
  assert.ok(repaired.coverImageKey);
  assert.deepEqual(withoutCoverRevision(repaired), withoutCoverRevision(before));
  const image = await fetch(`${stack.assetOrigin}/${repaired.coverImageKey}`);
  assert.equal(image.status, 200);
  assert.deepEqual(
    Buffer.from(await image.arrayBuffer()),
    Buffer.from(await coverImageAsset.body.arrayBuffer()),
  );
  assert.equal(await findArticleByTitle(retiredPaginationArticleTitle), undefined);
  assert.equal((await get('/api/v1/articles?size=100')).totalElements, pagination.perPage + 1);

  // A further run must reuse the asset and leave the album revision/children unchanged.
  await seedForBuild();
  assert.deepEqual(await get(path), repaired);
  assert.equal((await get('/api/v1/articles?size=100')).totalElements, pagination.perPage + 1);
  console.log(
    'Build fixture reuse: legacy cover repaired, album fields preserved, retired article removed, second run unchanged',
  );
};
