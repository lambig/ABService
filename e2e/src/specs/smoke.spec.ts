import { seedPublishedAlbum } from '../support/admin-api';
import { stack } from '../support/config';
import { capture, clickWithEvidence } from '../support/evidence';
import { expect, test } from '../support/fixtures';

/**
 * 基盤が組み上がっていることを確かめる最小のシナリオ。
 *
 * 画面ごとのジャーニーは対象の画面と同じ変更で足す（#164）。ここで見るのは、公開サイトが配信されること、
 * 管理API経由のシードが効くこと、証跡の撮影が動くことの3つ。
 */

test.describe('公開サイトの基盤', () => {
  test('トップページが開き、導線をたどれる', async ({ page }) => {
    await page.goto('/');

    await expect(page).toHaveTitle('ABService');
    await capture(page, '01-top');

    /*
     * 導線のラベルは画面の実装で変わるため、名前ではなく位置で選ぶ。ここで確かめたいのは
     * 「証跡を撮って操作できること」であって、文言ではない。遷移先の中身は #123 の画面が
     * 揃ってから、そのシナリオで見る。
     */
    const firstNavLink = page
      .getByRole('navigation', { name: '主要な導線' })
      .getByRole('link')
      .first();
    await expect(firstNavLink).toBeVisible();

    await clickWithEvidence(page, firstNavLink, '02-nav-first-link');

    await expect(page).not.toHaveURL(`${stack.siteBaseUrl}/`);
  });
});

test.describe('管理API経由のシード', () => {
  test('投入した作品が公開の照会に現れる', async ({ request }) => {
    const suffix = String(Date.now());
    const albumId = await seedPublishedAlbum({
      title: `シード確認アルバム ${suffix}`,
      releaseDate: '2026-05-05',
      artistDisplayName: 'シード確認アーティスト',
      artistSortKey: 'しーどかくにんああてぃすと',
      catalogNumber: `SEED-${suffix}`,
      tracks: [
        {
          trackNo: 1,
          title: 'シード確認トラック',
          tunes: [{ seq: 1, tuneTitle: 'シード確認チューン', composerCreditOverride: 'Trad.' }],
        },
      ],
      externalAudioUrls: ['https://soundcloud.com/example/seed'],
    });

    const response = await request.get(`${stack.backendBaseUrl}/api/v1/albums/${albumId}`);

    await expect(response).toBeOK();
    const album = (await response.json()) as {
      catalogNumber: string;
      tracks: readonly { tunes: readonly { tuneTitle: string }[] }[];
      externalAudios: readonly { url: string }[];
    };

    expect(album.catalogNumber).toBe(`SEED-${suffix}`);
    expect(album.tracks[0]?.tunes[0]?.tuneTitle).toBe('シード確認チューン');
    expect(album.externalAudios).toHaveLength(1);
  });
});
