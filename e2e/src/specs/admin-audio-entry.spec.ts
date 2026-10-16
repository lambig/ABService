import { stack } from '../support/config.ts';
import { captureFocused } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';
import { deleteScratchAlbums, seedScratchAlbum } from '../support/scratch-albums.ts';

test.afterEach(deleteScratchAlbums);

/** 実APIの管理認証と作品を使う。通常入口で登録用接続が無い場合は送信を許可しない。 */
test('作品からFLAC登録を開き、登録用接続がない通常入口では送信を止める', async ({ page }) => {
  const title = await seedScratchAlbum('試聴用FLAC');
  await page.goto(stack.adminBaseUrl);
  await page.getByLabel('管理APIの鍵').fill(stack.adminApiKey);
  await page.getByRole('button', { name: '開く' }).click();
  await page
    .getByRole('row')
    .filter({ hasText: title })
    .getByRole('link', { name: '試聴用FLAC' })
    .click();
  await expect(page.getByRole('heading', { name: title })).toBeVisible();
  await expect(
    page.getByText('音源登録用の接続から、この管理画面を開いてください。'),
  ).toBeVisible();
  await expect(page.getByLabel('作成済みFLAC')).toBeDisabled();
  await expect(page.getByRole('button', { name: 'FLACを登録する' })).toBeDisabled();
  await captureFocused(
    page,
    page.getByRole('region', { name: '試聴用FLACの登録' }),
    '42-admin-audio-connection-required',
  );
});
