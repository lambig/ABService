import { openAllSections } from '../support/album-editor.ts';
import { stack } from '../support/config.ts';
import { captureFocused } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';
import { deleteScratchAlbums, seedScratchAlbum } from '../support/scratch-albums.ts';

/** 外部通信は共通fixtureで遮断し、保存と検証は実APIを使う（#389 / #164）。 */
test.afterEach(deleteScratchAlbums);

test('追加前に試聴し、URLを修正してから追加・保存できる', async ({ page }) => {
  const title = await seedScratchAlbum('音源プレビュー');
  await page.goto(stack.adminBaseUrl);
  await page.getByLabel('管理APIの鍵').fill(stack.adminApiKey);
  await page.getByRole('button', { name: '開く' }).click();
  await page
    .getByRole('row')
    .filter({ hasText: title })
    .getByRole('link', { name: '編集する' })
    .click();
  await openAllSections(page);

  const input = page.getByLabel('音源のURL');
  const preview = page.getByRole('button', { name: 'プレビューする' });
  const frame = page.getByTitle('追加前の音源の試聴');
  const region = page.getByRole('region', { name: '外部音源のプレビュー' });
  const rows = page.locator('[data-external-audios]').getByRole('listitem');
  const url = 'https://soundcloud.com/example/e2e-preview';

  await expect(preview).toBeDisabled();
  await input.fill('https://soundcloud.com/example/e2e-typo');
  await expect(frame).toHaveCount(0);
  await preview.click();
  await expect(frame).toBeVisible();
  await expect(rows).toHaveCount(0);
  await expect(region).toContainText('再生できない場合はURLや音源の公開設定');
  await captureFocused(page, region, '39r-admin-audio-preview-blocked');

  await input.fill(url);
  await expect(frame).toHaveCount(0);
  await preview.click();
  const embed = new URL((await frame.getAttribute('src')) ?? '');
  expect(embed.origin).toBe('https://w.soundcloud.com');
  expect(embed.searchParams.get('url')).toBe(url);
  expect(embed.searchParams.get('auto_play')).toBe('false');
  await expect(frame).toHaveAttribute('referrerpolicy', 'no-referrer');
  await page.getByRole('button', { name: 'プレビューを閉じる' }).click();
  await expect(frame).toHaveCount(0);
  await expect(input).toHaveValue(url);

  /* 試聴だけでは作品も入力行も保存されない。 */
  await page.reload();
  await openAllSections(page);
  await expect(rows).toHaveCount(0);
  await input.fill(url);
  await preview.click();
  await page.getByRole('button', { name: '音源を追加する' }).click();
  await expect(frame).toHaveCount(0);
  await expect(rows).toHaveCount(1);
  await page.getByRole('button', { name: '保存する' }).click();
  await page
    .getByRole('row')
    .filter({ hasText: title })
    .getByRole('link', { name: '編集する' })
    .click();
  await openAllSections(page);
  await expect(rows).toHaveCount(1);
  await expect(rows.first()).toContainText(url);
  await expect(frame).toHaveCount(0);
});

test('プレビュー後も未対応URLの保存はbackendが断り、入力を保持する', async ({ page }) => {
  const title = await seedScratchAlbum('音源プレビュー検証');
  await page.goto(stack.adminBaseUrl);
  await page.getByLabel('管理APIの鍵').fill(stack.adminApiKey);
  await page.getByRole('button', { name: '開く' }).click();
  await page
    .getByRole('row')
    .filter({ hasText: title })
    .getByRole('link', { name: '編集する' })
    .click();
  await openAllSections(page);
  const url = 'https://example.com/e2e-not-embeddable';
  await page.getByLabel('音源のURL').fill(url);
  await page.getByRole('button', { name: 'プレビューする' }).click();
  await expect(page.getByTitle('追加前の音源の試聴')).toHaveAttribute(
    'src',
    new RegExp(encodeURIComponent(url)),
  );
  await page.getByRole('button', { name: '音源を追加する' }).click();
  await page.getByRole('button', { name: '保存する' }).click();
  const row = page.locator('[data-external-audios]').getByRole('listitem');
  await expect(row.getByRole('alert')).toBeVisible();
  await expect(row).toContainText(url);
  await expect(page.getByLabel('タイトル')).toHaveValue(title);
});
