import { deleteAlbum, findAlbumsByCatalogNumberPrefix, seedDraftAlbum } from './admin-api.ts';
import { longTextOf } from './long-text.ts';

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

/**
 * 検査のためだけに作る作品のカタログナンバーの接頭辞。シードした作品（`E2E-0001` 等）には当たらない。
 *
 * <p>
 * 画面から作品を作るシナリオも、片付けの対象に入るようこの接頭辞を入力する。
 * </p>
 */
export const SCRATCH_CATALOG_PREFIX = 'E2E-SCRATCH-';

/** 作った作品。画面から指すためのタイトルと、APIから操作するためのIDを持つ */
export interface ScratchAlbum {
  readonly albumId: string;
  readonly title: string;
}

/** 検査のためだけに作る作品の基準額。編集画面が読み込んだ値を欄へ入れることを見るために持たせる */
export const SCRATCH_BASE_PRICE = 1200;

/**
 * 検査のためだけの作品を1つ作る（下書き）。
 *
 * <p>
 * 画面の外から同じ作品を操作するシナリオ（別のタブが保存した状態を作る等）はIDを要するため、こちらを使う。
 * </p>
 *
 * @param purpose
 *            何のための作品かを表す短い語。タイトルに入る
 * @returns 作った作品のIDとタイトル
 */
export const seedScratchAlbumDetail = async (purpose: string): Promise<ScratchAlbum> => {
  const stamp = String(Date.now());
  const title = `E2E ${purpose}アルバム ${stamp}`;

  const albumId = await seedDraftAlbum({
    title,
    releaseDate: '2026-09-01',
    artistDisplayName: `E2E ${purpose}アーティスト`,
    artistSortKey: `E2E ${purpose}`,
    catalogNumber: `${SCRATCH_CATALOG_PREFIX}${stamp}`,
    basePrice: { amount: SCRATCH_BASE_PRICE },
  });

  return { albumId, title };
};

/**
 * 検査のためだけの作品を1つ作る（下書き）。
 *
 * @param purpose
 *            何のための作品かを表す短い語。タイトルに入る
 * @returns 一覧で行を指すためのタイトル
 */
export const seedScratchAlbum = async (purpose: string): Promise<string> =>
  (await seedScratchAlbumDetail(purpose)).title;

/**
 * 作品のタイトルに使える長さの上限。
 *
 * ドメインの `AlbumTitle` と列（`AlbumTableRecord` の `title`）が持つ値と揃える。ずれても検査は
 * 落ちない（作れる長さのままなので）が、そのときここは「起こりうる上限」を指していない。
 */
export const ALBUM_TITLE_MAX_LENGTH = 255;

/**
 * 上限いっぱいのタイトルを持つ作品を1つ作る（下書き）。
 *
 * @returns 一覧で行を指すためのタイトル
 */
export const seedScratchAlbumWithLongestTitle = async (): Promise<string> => {
  const stamp = String(Date.now());
  const title = longTextOf(`E2E 長いタイトルのアルバム ${stamp} `, ALBUM_TITLE_MAX_LENGTH);

  await seedDraftAlbum({
    title,
    releaseDate: '2026-09-01',
    artistDisplayName: longTextOf('E2E 長い名義のアーティスト ', ALBUM_TITLE_MAX_LENGTH),
    artistSortKey: 'E2E ながいたいとる',
    catalogNumber: `${SCRATCH_CATALOG_PREFIX}${stamp}`,
  });

  return title;
};

/** 検査のためだけに作った作品を片付ける。作るシナリオを持つ spec の `afterEach` に置く */
export const deleteScratchAlbums = async (): Promise<void> => {
  const leftovers = await findAlbumsByCatalogNumberPrefix(SCRATCH_CATALOG_PREFIX);
  await Promise.all(leftovers.map((album) => deleteAlbum(album.albumId)));
};
