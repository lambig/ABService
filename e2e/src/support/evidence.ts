import { mkdir } from 'node:fs/promises';
import { dirname, join } from 'node:path';

import type { Locator, Page } from '@playwright/test';

/**
 * レビュー証跡の撮影。
 *
 * 画面の変更は差分を読んでも見えないため、操作と結果を目で追える画像を残す（#164）。
 * 撮る位置と印の付け方をここに集約し、シナリオ側は「何を操作したか」だけを書く。
 */

/** 証跡の出力先。リポジトリには入れず、公開するものだけを別ブランチへ送る */
const EVIDENCE_DIR = new URL('../../evidence/', import.meta.url).pathname;

/** クリックポイントの印に使う要素のID */
const MARKER_ID = 'e2e-click-marker';

/**
 * 操作箇所を viewport の中央 1/3 の帯へ寄せる。
 *
 * 画面のどこを操作しているかが一目で分かるようにするため、撮る前に必ず通す。帯へ収まらない大きさの
 * 要素は、上端を帯の上端に合わせる（全体を写すより、操作の起点を見せる方が伝わる）。
 */
export const focusOn = async (locator: Locator): Promise<void> => {
  await locator.scrollIntoViewIfNeeded();
  await locator.evaluate((element) => {
    const bandHeight = window.innerHeight / 3;
    const rect = element.getBoundingClientRect();
    const desiredTop =
      rect.height <= bandHeight ? (window.innerHeight - rect.height) / 2 : bandHeight;
    window.scrollBy({ top: rect.top - desiredTop, behavior: 'instant' });
  });
};

/**
 * いまの画面を証跡として撮る。
 *
 * @param page
 *            対象のページ
 * @param name
 *            ファイル名（`01-albums-list` のように順序が分かる形にする）
 */
export const capture = async (page: Page, name: string): Promise<void> => {
  const path = join(EVIDENCE_DIR, `${name}.png`);
  await mkdir(dirname(path), { recursive: true });
  await page.screenshot({ path, animations: 'disabled' });
};

/**
 * 操作箇所へ寄せ、クリックポイントに印を付けて撮ってから、クリックする。
 *
 * 印は撮影のためだけに置き、クリック前に取り除く（印がクリックを受け取ってしまうのを避ける）。
 */
export const clickWithEvidence = async (
  page: Page,
  locator: Locator,
  name: string,
): Promise<void> => {
  await focusOn(locator);
  const box = await locator.boundingBox();
  const point = centerOf(box);

  await showMarker(page, point);
  await capture(page, name);
  await hideMarker(page);

  await locator.click();
};

const centerOf = (
  box: { x: number; y: number; width: number; height: number } | null,
): { x: number; y: number } =>
  box === null
    ? (() => {
        throw new Error('操作対象が画面に無いため、クリックポイントを描けません');
      })()
    : { x: box.x + box.width / 2, y: box.y + box.height / 2 };

const showMarker = async (page: Page, point: { x: number; y: number }): Promise<void> => {
  await page.evaluate(
    ({ x, y, id }) => {
      const marker = document.createElement('div');
      marker.id = id;
      marker.setAttribute('aria-hidden', 'true');
      /*
       * 中は塗らない。操作対象の文字を覆うと「どこを押したか」は見えても「何を押したか」が読めなくなる。
       * 輪と外側の波紋だけで位置を示す。
       */
      marker.style.cssText = [
        'position:fixed',
        `left:${String(x)}px`,
        `top:${String(y)}px`,
        'width:36px',
        'height:36px',
        'margin:-18px 0 0 -18px',
        'border:3px solid rgba(220,38,38,0.95)',
        'border-radius:9999px',
        'background:transparent',
        'box-shadow:0 0 0 8px rgba(220,38,38,0.18)',
        'pointer-events:none',
        'z-index:2147483647',
      ].join(';');
      document.body.append(marker);
    },
    { ...point, id: MARKER_ID },
  );
};

const hideMarker = async (page: Page): Promise<void> => {
  await page.evaluate((id) => {
    document.getElementById(id)?.remove();
  }, MARKER_ID);
};
