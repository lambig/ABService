import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

import type { Page } from '@playwright/test';

const FONT_ORIGIN = 'https://fonts.gstatic.com/abservice-e2e/';
const stylesheet = (
  await Promise.all(
    ['400', '600'].map((weight) =>
      readFile(new URL(import.meta.resolve(`@fontsource/klee-one/${weight}.css`)), 'utf8'),
    ),
  )
)
  .join('\n')
  .replaceAll('./files/', FONT_ORIGIN);

/**
 * 外部CDNの応答時間を画面の成否へ持ち込まない。同じ書体の実ファイルを固定版から返し、
 * ブラウザのフォント読込とCSPは通す。各シナリオの遅延・保留・失敗のrouteは後から重ねる。
 */
export const installFonts = async (page: Page): Promise<void> => {
  /* pageの固定応答が優先される。想定外のURLも外部CDNへは接続させない。 */
  await page
    .context()
    .route(/^https:\/\/fonts\.(googleapis|gstatic)\.com\//u, (route) => route.abort());
  await page.route(
    'https://fonts.googleapis.com/css2?family=Klee+One:wght@400;600&display=swap',
    (route) => route.fulfill({ contentType: 'text/css', body: stylesheet }),
  );
  await page.route(`${FONT_ORIGIN}*`, (route) => {
    const file = new URL(route.request().url()).pathname.split('/').at(-1) ?? '';
    return /^klee-one-[a-z0-9-]+\.woff2?$/u.test(file)
      ? route.fulfill({
          path: fileURLToPath(import.meta.resolve(`@fontsource/klee-one/files/${file}`)),
          contentType: file.endsWith('.woff2') ? 'font/woff2' : 'font/woff',
          headers: { 'access-control-allow-origin': '*' },
        })
      : route.abort();
  });
};
