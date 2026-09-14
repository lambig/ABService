import type { Page } from '@playwright/test';

/**
 * 作品の編集画面の区画。
 *
 * <p>
 * 区画は既定で畳まれており、畳んだ姿は公開サイトと同じ読み方の要約になる（#122）。欄の振る舞いを
 * 見るシナリオは、まず開く必要がある——畳み方そのものを見るのは
 * `admin-album-sections.spec.ts` である。
 * </p>
 */

/** 上から並ぶ順。開く順が画面の並びと揃っていないと、落ちたときにどこで止まったのか読めない */
export const SECTION_HEADINGS = [
  '作品',
  '初出イベント',
  '頒布額',
  'カバー画像',
  '曲目',
  '外部音源',
] as const;

export const openSection = async (page: Page, heading: string): Promise<void> => {
  await page.getByRole('button', { name: `${heading}を開く` }).click();
};

/**
 * すべての区画を開く。
 *
 * 1つずつ順に開く——同時に押すと、畳んだ区画の高さが変わっていく途中で次を押すことになる。
 */
export const openAllSections = (page: Page): Promise<void> =>
  SECTION_HEADINGS.reduce(
    (opened, heading) => opened.then(() => openSection(page, heading)),
    Promise.resolve(),
  );
