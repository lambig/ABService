import type { Locator, Page } from '@playwright/test';

import { findAlbumByCatalogNumber } from '../support/admin-api.ts';
import {
  draft,
  quiet,
  showcase,
  showcaseTrackNames,
  showcaseTracks,
} from '../support/build-fixtures.ts';
import { capture, clickWithEvidence, focusOn } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';

/**
 * 公開サイトの作品（#123）のジャーニー。
 *
 * 見るのは #197 が確定した内容が画面に出ているかで、項目の並びや文字装飾は対象にしない。外部サービスの
 * 埋め込みは遮断されている（`fixtures.ts`）ため、埋め込み枠は「音源が渡っていること」で確かめる。
 */

/** 試聴の節の見出し。文言は画面の実装が持つ */
const AUDIO_SECTION_HEADING = '試聴';

/**
 * 曲目の一覧。
 *
 * 罫線つきの `ol`（`TrackList.astro`）で指す。役割（listitem）だけでは概要説明の箇条書きや、トラックの
 * 中のチューンまで拾ってしまい、トラックの行を数え上げられない。
 */
const TRACK_LIST = 'ol.divide-y';

/** 曲目の1行の中で、トラックの名を持つ箇所。`TrackList.astro` の構造（名の div → チューンの ol）に沿う */
const TRACK_NAME = `${TRACK_LIST} > li > div > div:first-child`;

/** トラックの行の中の、チューン1件の行。トラックの行を起点に引く */
const TUNE_ROW = 'ol > li';

/** 名で曲目の1行を指す。チューンの行にも同じ文字列が現れうるため、外側の行を取る */
const trackRowOf = (page: Page, name: string): Locator =>
  page.locator(`${TRACK_LIST} > li`).filter({ hasText: name }).first();

/** 額の整形が出す通貨の記号。額が出ていないことは、記号の不在でしか言えない */
const CURRENCY_SIGN = '￥';

const albumPathOf = async (catalogNumber: string): Promise<string> => {
  const album = await findAlbumByCatalogNumber(catalogNumber);
  return album === undefined
    ? Promise.reject(new Error(`シードした作品が見つかりません: ${catalogNumber}`))
    : `/albums/${album.albumId}`;
};

test.describe('作品の一覧', () => {
  test('一覧から詳細へたどり、作品の事実を読める', async ({ page }) => {
    await page.goto('/albums');

    const card = page.getByRole('link').filter({ hasText: showcase.title });
    await expect(card).toBeVisible();

    /* `showcase` は既定の名義のため、名義は出ない（#348） */
    await expect(card).not.toContainText(showcase.artistDisplayName);

    await expect(card).toContainText(showcase.catalogNumber);
    await expect(card).toContainText(showcase.eventName);

    /*
     * 一覧が出す日付は作品自身のリリース日だけ（#347）。イベントの開催日まで出すと、カード1枚に
     * 意味の違う日付が2つ並ぶ。時刻要素の数で見る。
     */
    await expect(card.locator('time')).toHaveCount(1);

    await capture(page, '03-albums-list');

    await clickWithEvidence(page, card, '04-albums-list-open-detail');

    await expect(page.getByRole('heading', { level: 1, name: showcase.title })).toBeVisible();
    await expect(page.getByText(showcase.artistDisplayName)).toHaveCount(0);
    await expect(page.getByText(showcase.releaseDateText)).toBeVisible();
    await expect(page.getByText(showcase.catalogNumber)).toBeVisible();
    await expect(page.getByText(showcase.eventName)).toBeVisible();
    await expect(page.getByText(showcase.originalWorkNote)).toBeVisible();
    await capture(page, '05-album-detail');
  });

  test('カタログナンバーの降順に、公開中の作品だけが並ぶ', async ({ page }) => {
    await page.goto('/albums');

    const titles = await page.getByRole('heading', { level: 2 }).allInnerTexts();

    /*
     * E2E は専用のデータベースを見る（#252）。母集団はシードしたものだけのため、並びを全体で確かめる。
     * 下書き（E2E-0003）はここに現れない。
     */
    expect(titles).toEqual([quiet.title, showcase.title]);
  });

  test('下書きは一覧に出ない', async ({ page }) => {
    await page.goto('/albums');

    await expect(page.getByText(draft.title)).toHaveCount(0);
  });

  /*
   * 額を持つ作品（`showcase`）でも、一覧と詳細には額を出さない。頒布の額が要るのは作品紹介の記事で、
   * 作品のページは作品の事実を読む場のため（#349）。
   */
  test('額を持つ作品でも、一覧と詳細に額は出ない', async ({ page }) => {
    await page.goto('/albums');
    await expect(page.getByText(CURRENCY_SIGN)).toHaveCount(0);

    await page.goto(await albumPathOf(showcase.catalogNumber));
    await expect(page.getByText(CURRENCY_SIGN)).toHaveCount(0);
  });
});

test.describe('作品の詳細', () => {
  test('試聴は埋め込みで完結し、取得元へ出る導線を置かない', async ({ page }) => {
    await page.goto(await albumPathOf(showcase.catalogNumber));

    const audioSection = page.getByRole('heading', { level: 2, name: AUDIO_SECTION_HEADING });
    await expect(audioSection).toBeVisible();

    /*
     * 埋め込み枠には音源の URL がそのまま渡る（許可リストはバックエンドの ExternalAudioUrl が持つ）。
     * プレイヤーの組み立て方そのものは画面の実装で変わるため、渡っていることだけを見る。
     */
    const embed = page.locator('iframe');
    const embedSrc = await embed.getAttribute('src');
    expect(embedSrc).toContain(encodeURIComponent(showcase.audioUrl));

    /*
     * 取得元へ出るリンクは置かない（#356）。埋め込みが表示できないのにリンクだけ辿れる状態が
     * 起こるのはプレイヤー側の障害くらいで、そのために常設の導線を置く理由がない。音源の URL を
     * href に持つリンクの不在で見る（文言はいつでも変わりうる）。
     */
    await expect(page.locator(`a[href="${showcase.audioUrl}"]`)).toHaveCount(0);

    /*
     * 埋め込み枠から下は曲目まで1画面に収まる。同じ絵を複数の名前で撮ると、レビューでは同じものを
     * 二度見ることになるため、この帯の証跡はここだけで撮る。
     */
    await focusOn(embed);
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

    const track = trackRowOf(page, showcaseTracks.titledWithTune.name);
    await expect(track).toContainText(showcaseTracks.titledWithTune.tuneTitle);
    await expect(track).toContainText(showcaseTracks.titledWithTune.composerCredit);
  });

  test('作曲と編曲の両方のクレジットが並ぶ', async ({ page }) => {
    await page.goto(await albumPathOf(showcase.catalogNumber));

    const track = trackRowOf(page, showcaseTracks.titledWithArrangedTune.name);
    await expect(track).toContainText(showcaseTracks.titledWithArrangedTune.composerCredit);
    await expect(track).toContainText(showcaseTracks.titledWithArrangedTune.arrangerCredit);
  });

  test('トラック名を持たないトラックは、チューン名を繋いだものが名になる', async ({ page }) => {
    await page.goto(await albumPathOf(showcase.catalogNumber));

    /*
     * 名の綴りそのものを見る（#360）。「チューン名が出ている」だけでは、繋ぎ方も順序も確かめられない。
     */
    const joined = showcaseTracks.untitledWithTunes;
    const track = trackRowOf(page, joined.name);
    await expect(track).toBeVisible();
    await expect(track).toContainText(joined.firstTuneTitle);
    await expect(track).toContainText(joined.secondTuneTitle);
  });

  test('名もクレジットも持たないチューンは、名にも曲目の行にも出ない', async ({ page }) => {
    await page.goto(await albumPathOf(showcase.catalogNumber));

    /*
     * 間奏（名もクレジットも持たないチューン）を挟んだトラック。名に現れないだけでなく、曲目の行にも
     * 出ない——位置だけを表す空の行になり、何も読めないため（#360）。名の元にならないチューンが
     * あることが、不変条件を「チューンを持つこと」ではなく「名を持つチューンを持つこと」にしている理由。
     *
     * 行の数まで見る。名だけを見ていると、空の行が増えていても通ってしまう。
     */
    const interlude = showcaseTracks.untitledWithUnnamedTune;
    const track = trackRowOf(page, interlude.name);
    await expect(track).toBeVisible();
    await expect(track.locator(TUNE_ROW)).toHaveText([
      interlude.firstTuneTitle,
      interlude.lastTuneTitle,
    ]);

    /*
     * 落とすのは「名もクレジットも無い」行だけ。名が無くてもクレジットがあれば読めるため残す
     * （このトラックのチューンは語りのクレジットだけを持つ）。
     */
    const narration = showcaseTracks.titledWithUnnamedTune;
    const narrationTunes = trackRowOf(page, narration.name).locator(TUNE_ROW);
    await expect(narrationTunes).toHaveCount(1);
    await expect(narrationTunes).toContainText(narration.tuneCredit);
  });

  test('曲目はトラック番号の順に、組み合わせごとの名で並ぶ', async ({ page }) => {
    await page.goto(await albumPathOf(showcase.catalogNumber));

    /*
     * 曲目の見出しの下の一覧を、名だけ取り出して並びごと突き合わせる。組み合わせは8通りあり、
     * 1件ずつ見ると「どれが抜けているか」が分からない（#360）。
     */
    const names = await page.locator(TRACK_NAME).allInnerTexts();

    expect(names).toEqual(showcaseTrackNames);

    /* 曲目は詳細（05）とは別の見どころのため、その枝番に置く。8件あるので一覧の先頭へ寄せて撮る */
    await focusOn(page.locator(TRACK_LIST));
    await capture(page, '05a-album-detail-tracks');
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

  test('原作の出典は、書かれた綴りのまま出る', async ({ page }) => {
    await page.goto(await albumPathOf(showcase.catalogNumber));

    /*
     * 綴りをそのまま見る（#365）。作品名だけを持って画面で言い回しを組み立てる形にすると、
     * 「より各曲」と言っていない盤でも同じ文が出てしまう。ここで見ているのは、入れた一文が
     * 加工されずに出ることそのもの。
     */
    await expect(page.getByText(showcase.originalWorkNote, { exact: true })).toBeVisible();

    /*
     * トラックの行には出ない。トラックと原作の対応は述べていない（述べない意図がある）ため、
     * 曲目の側に置くと、システムが対応を主張したことになる（#89）。
     */
    await expect(page.locator(TRACK_LIST)).not.toContainText(showcase.originalWorkNote);
  });

  test('原作の出典を持たない作品には、その行が出ない', async ({ page }) => {
    await page.goto(await albumPathOf(quiet.catalogNumber));

    /*
     * 未入力を空欄として見せない。「原作:」のようなラベルごと出さないため、記述を持つ作品の
     * 綴りが画面のどこにも無いことで見る。
     */
    await expect(page.getByText(showcase.originalWorkNote)).toHaveCount(0);
  });

  test('既定と違う名義は出る', async ({ page }) => {
    await page.goto(await albumPathOf(quiet.catalogNumber));

    /* 既定の名義（`showcase` 側）と違うため、こちらは出る（#348） */
    await expect(page.getByText(quiet.artistDisplayName)).toBeVisible();
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
