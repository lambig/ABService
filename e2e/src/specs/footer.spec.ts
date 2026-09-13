import { siteContent } from '../support/build-fixtures.ts';
import { captureFocused } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';

/**
 * ページの末尾（#343）。
 *
 * <p>
 * 出るのはコピーライト表示だけ。主体はデータ側が持ち（#230 の線引きの「内容のある文言」側）、年と記号は
 * 画面が組み立てる。
 * </p>
 *
 * <p>
 * 見るのは**組み立てた形**であって、年の値そのものではない。年は組み立てた時点のもので、静的出力の
 * ため次に作り直すまで変わらない（#374）。値を期待値にすると、年が明けた日に組み立てと検査で食い違う。
 * </p>
 */

const composedCopyright = new RegExp(`^© \\d{4} ${siteContent.copyrightHolder}$`, 'u');

test.describe('ページの末尾', () => {
  test('コピーライト表示が、主体に年と記号を足した形で出る', async ({ page }) => {
    await page.goto('/');

    const footer = page.locator('footer');
    await expect(footer).toHaveText(composedCopyright);

    await captureFocused(page, footer, '01d-site-footer');
  });

  test('末尾はどのページにも出る', async ({ page }) => {
    await page.goto('/articles');
    await expect(page.locator('footer')).toHaveText(composedCopyright);

    await page.goto('/albums');
    await expect(page.locator('footer')).toHaveText(composedCopyright);
  });
});
