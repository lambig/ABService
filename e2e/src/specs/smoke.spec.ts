import { showcase, siteContent } from '../support/build-fixtures.ts';
import { stack } from '../support/config.ts';
import { capture, clickWithEvidence } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';

/**
 * 基盤が組み上がっていることを確かめる最小のシナリオ。
 *
 * 画面ごとのジャーニーは対象の画面と同じ変更で足す（#164）。ここで見るのは、公開サイトが配信されること、
 * ビルド前のシードが効くこと、証跡の撮影が動くことの3つ。
 */

test.describe('公開サイトの基盤', () => {
  test('トップページが開き、導線をたどれる', async ({ page }) => {
    await page.goto('/');

    /* トップはページの名前を持たないため、タイトルはサイト名だけになる（#230） */
    await expect(page).toHaveTitle(siteContent.name);
    await capture(page, '01-top');

    /*
     * 導線のラベルは画面の実装で変わるため、名前ではなく行き先で選ぶ。ここで確かめたいのは
     * 「証跡を撮って操作できること」であって、文言ではない。導線にはトップ自身も載る（#197）ため、
     * 移動が起きる先を選ぶ。遷移先の中身は #123 の画面のシナリオで見る。
     */
    const firstNavLink = page
      .getByRole('navigation', { name: '主要な導線' })
      .locator('a[href]:not([href="/"])')
      .first();
    await expect(firstNavLink).toBeVisible();

    await clickWithEvidence(page, firstNavLink, '02-nav-first-link');

    await expect(page).not.toHaveURL(`${stack.siteBaseUrl}/`);
  });
});

test.describe('ビルド前のシード', () => {
  test('画面確認用の作品が、トラックと外部音源まで揃って公開されている', async ({ request }) => {
    const response = await request.get(
      `${stack.backendBaseUrl}/api/v1/albums?size=100&sort=catalogNumber`,
    );

    await expect(response).toBeOK();
    const page = (await response.json()) as {
      items: readonly { catalogNumber: string | null; albumId: string }[];
    };

    const seeded = page.items.find((item) => item.catalogNumber === showcase.catalogNumber);
    expect(seeded).toBeDefined();

    const detail = await request.get(
      `${stack.backendBaseUrl}/api/v1/albums/${seeded?.albumId ?? ''}`,
    );
    await expect(detail).toBeOK();

    const album = (await detail.json()) as {
      tracks: readonly { tunes: readonly { tuneTitle: string }[] }[];
      externalAudios: readonly { url: string }[];
    };

    expect(album.tracks[0]?.tunes[0]?.tuneTitle).toBe(showcase.tuneTitle);
    expect(album.externalAudios).toHaveLength(1);
  });
});
