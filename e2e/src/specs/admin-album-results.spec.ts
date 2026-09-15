import {
  deleteArticle,
  publishAlbum,
  publishArticle,
  seedDraftArticle,
} from '../support/admin-api.ts';
import { stack } from '../support/config.ts';
import { captureFocused } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';
import { deleteScratchAlbums, seedScratchAlbumDetail } from '../support/scratch-albums.ts';
import { deleteScratchArticles, SCRATCH_TITLE_PREFIX } from '../support/scratch-articles.ts';

const addReference = async (albumId: string, title: string): Promise<string> =>
  seedDraftArticle({
    articleType: 'ALBUM',
    title,
    albumId,
    body: '実行結果のE2E本文',
    bodyFormat: 'PLAIN_TEXT',
  });

test.afterEach(async () => {
  await deleteScratchArticles();
  await deleteScratchAlbums();
});

for (const scenario of ['delete', 'unpublish', 'empty'] as const) {
  test(`${scenario}: 確認後に参照が変わっても実行時の影響記事を表示する`, async ({ page }) => {
    const album = await seedScratchAlbumDetail('実行結果');
    await publishAlbum(album.albumId);
    const previewTitle = `${SCRATCH_TITLE_PREFIX} 事前確認だけの記事`;
    const previewId = await addReference(album.albumId, previewTitle);
    await publishArticle(previewId);
    await page.goto(stack.adminBaseUrl);
    await page.getByLabel('管理APIの鍵').fill(stack.adminApiKey);
    await page.getByRole('button', { name: '開く' }).click();
    const label = scenario === 'delete' ? '削除する' : '非公開にする';
    await page
      .getByRole('row')
      .filter({ hasText: album.title })
      .getByRole('button', { name: label })
      .click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText(previewTitle);

    // CHANGED-REFERENCES: 事前照会と実行の間で実APIの参照状態を変え、応答は差し替えない。
    await deleteArticle(previewId);
    const actualTitle = `${SCRATCH_TITLE_PREFIX} 実行時に公開中の記事`;
    const draftTitle = `${SCRATCH_TITLE_PREFIX} 実行時に下書きの記事`;
    const actualId = scenario === 'empty' ? null : await addReference(album.albumId, actualTitle);
    await (actualId === null ? Promise.resolve() : publishArticle(actualId));
    await (scenario === 'empty' ? Promise.resolve() : addReference(album.albumId, draftTitle));
    const listUrl = `${stack.backendBaseUrl}/api/v1/admin/albums?*`;
    await (scenario === 'empty'
      ? page.route(listUrl, (route) => route.abort())
      : Promise.resolve());
    await dialog.getByRole('button', { name: label }).click();

    const result = page.getByRole('region', { name: '作品操作の実行結果' });
    await expect(result).toBeVisible();
    await expect(result).not.toContainText(previewTitle);
    await expect(result.getByRole('link')).toHaveCount(
      scenario === 'delete' ? 2 : scenario === 'unpublish' ? 1 : 0,
    );
    await expect(result).toContainText(
      scenario === 'delete'
        ? '作品への参照を失効し、非公開にしました。'
        : scenario === 'unpublish'
          ? '連動して非公開にしました。'
          : '影響を受けた記事はありません。',
    );
    await (scenario === 'delete'
      ? expect(result).toContainText('作品への参照を失効しました。')
      : expect(result).not.toContainText(draftTitle));
    await captureFocused(page, result, `37-${scenario}-operation-result`);

    await (scenario === 'empty' ? page.unroute(listUrl) : Promise.resolve());
    await (scenario === 'empty'
      ? page.getByRole('button', { name: '再試行' }).click()
      : Promise.resolve());
    await expect(page.getByRole('table')).toBeVisible();
    await expect(result).toBeVisible();
    await (actualId === null
      ? page.getByRole('button', { name: '実行結果を閉じる' }).click()
      : result.getByRole('link', { name: actualTitle }).click());
    await (actualId === null
      ? expect(result).toHaveCount(0)
      : expect(page.getByLabel('タイトル', { exact: true })).toHaveValue(actualTitle));
  });
}
