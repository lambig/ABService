import { deleteAlbum, findAlbumsByCatalogNumberPrefix, seedDraftAlbum } from './admin-api.ts';

/**
 * シナリオの中だけで使う作品。
 *
 * <p>
 * SHARED-POPULATION: 公開サイトの一覧の検査は「母集団はシードしたものだけ」を前提にしている
 * （`albums.spec.ts`）。組み立ては実行の前に一度だけ走るため、検査の中で作った作品が残ると**次回の
 * 実行**の組み立てに混ざり、その前提を壊す。作った側で必ず片付ける。
 * </p>
 *
 * <p>
 * 片付けは控えではなくカタログナンバーの接頭辞で拾う。控えを持つと、検査が途中で落ちた回の分が残る。
 * 削除はべき等なので、画面から消したものへ重ねて送っても成功する。
 * </p>
 */

/** 検査のためだけに作る作品のカタログナンバーの接頭辞。シードした作品（`E2E-0001` 等）には当たらない */
const SCRATCH_CATALOG_PREFIX = 'E2E-SCRATCH-';

/**
 * 検査のためだけの作品を1つ作る（下書き）。
 *
 * @param purpose
 *            何のための作品かを表す短い語。タイトルに入る
 * @returns 一覧で行を指すためのタイトル
 */
export const seedScratchAlbum = async (purpose: string): Promise<string> => {
  const stamp = String(Date.now());
  const title = `E2E ${purpose}アルバム ${stamp}`;

  await seedDraftAlbum({
    title,
    releaseDate: '2026-09-01',
    artistDisplayName: `E2E ${purpose}アーティスト`,
    artistSortKey: `E2E ${purpose}`,
    catalogNumber: `${SCRATCH_CATALOG_PREFIX}${stamp}`,
  });

  return title;
};

/** 検査のためだけに作った作品を片付ける。作るシナリオを持つ spec の `afterEach` に置く */
export const deleteScratchAlbums = async (): Promise<void> => {
  const leftovers = await findAlbumsByCatalogNumberPrefix(SCRATCH_CATALOG_PREFIX);
  await Promise.all(leftovers.map((album) => deleteAlbum(album.albumId)));
};
