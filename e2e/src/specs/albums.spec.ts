import { findAlbumIdByCatalogNumber } from '../support/admin-api.ts';
import { draft, quiet, showcase } from '../support/build-fixtures.ts';
import { capture, clickWithEvidence, focusOn } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';

/**
 * 公開サイトの作品（#123）のジャーニー。
 *
 * 見るのは #197 が確定した内容が画面に出ているかで、項目の並びや文字装飾は対象にしない。外部サービスの
 * 埋め込みは遮断されている（`fixtures.ts`）ため、埋め込み枠は「音源が渡っていること」と
 * 「表示できなくても音源へ辿れること」で確かめる。
 */

/** 埋め込みが表示できない環境向けのリンク。文言は画面の実装が持つ */
const AUDIO_FALLBACK_LINK = 'SoundCloud で開く';

/** 試聴の節の見出し。文言は画面の実装が持つ */
const AUDIO_SECTION_HEADING = '試聴';

const albumPathOf = async (catalogNumber: string): Promise<string> => {
  const albumId = await findAlbumIdByCatalogNumber(catalogNumber);
  return albumId === undefined
    ? Promise.reject(new Error(`シードした作品が見つかりません: ${catalogNumber}`))
    : `/albums/${albumId}`;
};

test.describe('作品の一覧', () => {
  test('一覧から詳細へたどり、作品の事実を読める', async ({ page }) => {
    await page.goto('/albums');

    const card = page.getByRole('link').filter({ hasText: showcase.title });
    await expect(card).toBeVisible();
    await expect(card).toContainText(showcase.artistDisplayName);
    await expect(card).toContainText(showcase.catalogNumber);
    await expect(card).toContainText(showcase.eventName);
    await capture(page, '03-albums-list');

    await clickWithEvidence(page, card, '04-albums-list-open-detail');

    await expect(page.getByRole('heading', { level: 1, name: showcase.title })).toBeVisible();
    await expect(page.getByText(showcase.artistDisplayName)).toBeVisible();
    await expect(page.getByText(showcase.releaseDateText)).toBeVisible();
    await expect(page.getByText(showcase.catalogNumber)).toBeVisible();
    await expect(page.getByText(showcase.eventName)).toBeVisible();
    await capture(page, '05-album-detail');
  });

  test('カタログナンバーの降順に並ぶ', async ({ page }) => {
    await page.goto('/albums');

    const titles = await page.getByRole('heading', { level: 2 }).allInnerTexts();

    /*
     * 開発DBには他の作品も入っているため、全体の並びではなくシードした2件の前後だけを見る。
     * E2E-0002 が E2E-0001 より前に来れば降順が効いている。
     */
    expect(titles).toContain(quiet.title);
    expect(titles).toContain(showcase.title);
    expect(titles.indexOf(quiet.title)).toBeLessThan(titles.indexOf(showcase.title));
  });

  test('下書きは一覧に出ない', async ({ page }) => {
    await page.goto('/albums');

    await expect(page.getByText(draft.title)).toHaveCount(0);
  });
});

test.describe('作品の詳細', () => {
  test('試聴は埋め込みが表示できなくても音源へ辿れる', async ({ page }) => {
    await page.goto(await albumPathOf(showcase.catalogNumber));

    await expect(
      page.getByRole('heading', { level: 2, name: AUDIO_SECTION_HEADING }),
    ).toBeVisible();

    /*
     * 埋め込み枠には音源の URL がそのまま渡る（許可リストはバックエンドの ExternalAudioUrl が持つ）。
     * プレイヤーの組み立て方そのものは画面の実装で変わるため、渡っていることだけを見る。
     */
    const embedSrc = await page.locator('iframe').getAttribute('src');
    expect(embedSrc).toContain(encodeURIComponent(showcase.audioUrl));

    const fallbackLink = page.getByRole('link', { name: AUDIO_FALLBACK_LINK });
    await expect(fallbackLink).toHaveAttribute('href', showcase.audioUrl);

    /*
     * 埋め込み枠から下は曲目まで1画面に収まる。同じ絵を複数の名前で撮ると、レビューでは同じものを
     * 二度見ることになるため、この帯の証跡はここだけで撮る。
     */
    await focusOn(fallbackLink);
    await capture(page, '06-album-detail-audio-and-tracks');
  });

  test('概要説明が Markdown として描かれる', async ({ page }) => {
    await page.goto(await albumPathOf(showcase.catalogNumber));

    await expect(
      page.getByRole('heading', { level: 2, name: showcase.description.heading }),
    ).toBeVisible();
    await expect(page.getByText(showcase.description.lead)).toBeVisible();
    await expect(
      page.getByRole('listitem').filter({ hasText: showcase.description.bullet }),
    ).toBeVisible();
    await expect(page.locator('strong')).toHaveText(showcase.description.emphasis);
  });

  test('曲目にチューンとクレジットが出る', async ({ page }) => {
    await page.goto(await albumPathOf(showcase.catalogNumber));

    const tracks = page.getByRole('listitem').filter({ hasText: showcase.trackTitle });
    await expect(tracks.first()).toContainText(showcase.tuneTitle);
    await expect(tracks.first()).toContainText(showcase.composerCredit);
  });

  test('外部音源を持つ作品のリンクプレビューはプレイヤーカードで、カバー画像を本体に出さない', async ({
    page,
  }) => {
    await page.goto(await albumPathOf(showcase.catalogNumber));

    await expect(page.locator('meta[name="twitter:card"]')).toHaveAttribute('content', 'player');

    const playerUrl = await page.locator('meta[name="twitter:player"]').getAttribute('content');
    expect(playerUrl).toContain(encodeURIComponent(showcase.audioUrl));

    /*
     * プレイヤー自身がアートワークを持つため、本体にはカバー画像を出さない（#197）。この作品は
     * 概要説明にも画像を持たないため、記事本体の画像は0件になる。
     */
    await expect(page.locator('article img')).toHaveCount(0);
  });

  test('外部音源を持たない作品では、試聴の節もプレイヤーカードも出ない', async ({ page }) => {
    await page.goto(await albumPathOf(quiet.catalogNumber));

    await expect(page.getByRole('heading', { level: 1, name: quiet.title })).toBeVisible();
    await expect(page.getByRole('heading', { level: 2, name: AUDIO_SECTION_HEADING })).toHaveCount(
      0,
    );
    await expect(page.locator('meta[name="twitter:card"]')).toHaveCount(0);
    await expect(page.locator('meta[property="og:image"]')).toHaveCount(0);

    await capture(page, '07-album-detail-without-audio');
  });

  test('品番と ISDN、初出イベントの5項目が出る', async ({ page }) => {
    await page.goto(await albumPathOf(quiet.catalogNumber));

    await expect(page.getByText(`${quiet.catalogNumber} / ${quiet.isdn}`)).toBeVisible();

    await expect(page.getByText(quiet.event.name)).toBeVisible();
    await expect(page.locator(`time[datetime="${quiet.event.date}"]`)).toBeVisible();
    await expect(page.getByText(`${quiet.event.place} ${quiet.event.spaceNumber}`)).toBeVisible();
    await expect(page.getByText(quiet.event.note)).toBeVisible();
  });

  test('プレーンテキストの概要説明は記法として解釈されない', async ({ page }) => {
    await page.goto(await albumPathOf(quiet.catalogNumber));

    await expect(page.getByText(quiet.description)).toBeVisible();
    await expect(page.locator('strong')).toHaveCount(0);
  });

  test('下書きの詳細は開けない', async ({ page }) => {
    const response = await page.goto(await albumPathOf(draft.catalogNumber));

    /*
     * 未存在と非公開を区別せず、どちらも 404 にする（#197。下書きの存在を漏らさない）。静的出力の
     * ため下書きのページはそもそも組まれず、配信が 404 を返す。404 の画面自体は #123 の後続で置く。
     */
    expect(response?.status()).toBe(404);
  });
});
