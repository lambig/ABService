import { seedPublishedAlbum } from './admin-api.ts';
import { stack } from './config.ts';

/**
 * 公開サイトを組む前に入れておくデータ。
 *
 * <p>
 * 公開サイトは静的出力で、ビルド時の内容がそのまま HTML になる（DECISIONS 24）。シナリオの中で投入した
 * データは**その回のビルドには入らない**ため、画面に現れてほしいものはここへ置く。
 * </p>
 *
 * <p>
 * 投入は冪等にする。ローカルでは同じ DB へ繰り返し実行するため、毎回作ると同じ作品が並んで証跡が
 * 読みにくくなる。カタログナンバーで存在を見て、無いときだけ作る。
 * </p>
 */

/** 画面確認用の作品。カタログナンバーが同定の鍵になる */
const SHOWCASE_CATALOG_NUMBER = 'E2E-0001';

interface AlbumListItem {
  readonly albumId: string;
  readonly catalogNumber: string | null;
}

const findByCatalogNumber = async (catalogNumber: string): Promise<AlbumListItem | undefined> => {
  const response = await fetch(`${stack.backendBaseUrl}/api/v1/albums?size=100&sort=catalogNumber`);
  const body = (await response.json()) as { items: readonly AlbumListItem[] };
  return body.items.find((item) => item.catalogNumber === catalogNumber);
};

/**
 * 画面確認用のデータを揃える。
 *
 * @returns 画面確認用の作品のドメインID
 */
export const seedForBuild = async (): Promise<string> => {
  const existing = await findByCatalogNumber(SHOWCASE_CATALOG_NUMBER);

  return existing === undefined
    ? seedPublishedAlbum({
        title: 'E2E 確認アルバム',
        releaseDate: '2026-08-15',
        artistDisplayName: 'E2E 確認アーティスト',
        artistSortKey: 'いーつーいーかくにん',
        catalogNumber: SHOWCASE_CATALOG_NUMBER,
        description: '## 概要\n\nE2E で画面を確認するための作品。\n\n- 箇条書き\n- **強調**\n',
        descriptionFormat: 'MARKDOWN',
        event: {
          name: 'E2E 確認イベント',
          date: '2026-08-15',
          place: 'E2E 会場',
          spaceNumber: 'A-01',
        },
        tracks: [
          {
            trackNo: 1,
            title: 'E2E 確認トラック',
            tunes: [{ seq: 1, tuneTitle: 'E2E 確認チューン', composerCreditOverride: 'Trad.' }],
          },
        ],
        externalAudioUrls: ['https://soundcloud.com/example/e2e'],
      })
    : existing.albumId;
};

export { SHOWCASE_CATALOG_NUMBER };
