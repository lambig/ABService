import { expect } from "@playwright/test";
import type { APIRequestContext, BrowserContext, Page } from "@playwright/test";
import { fixtureToken } from "../distribution";

export const entry = "/offline-player/";
export const saved = "オフライン再生の準備ができました";
export const staged = "新しい配布物の準備ができました";
export const unready = "オフライン再生の準備が未完了です";

/** 配布元が返す版を切り替える。v1 は曲音源と作品クロスフェードの併用、v2 は作品クロスフェードのみ。 */
export const distribute = async (
  request: APIRequestContext,
  version: "v1" | "v2",
): Promise<void> => {
  await request.post(`/__distribution?version=${version}`);
};

/** 試験の仕掛けから token を渡して保存する。token は入力欄だけに置く。 */
export const prepareOnline = async (
  page: Page,
  expected: string = staged,
): Promise<void> => {
  await expect(page.locator("#prepare")).toBeEnabled();
  await page.locator("#token").fill(fixtureToken);
  await page.locator("#prepare").click();
  await expect(page.locator("#readiness")).toHaveText(expected);
};

/** 全ページを閉じ、通信なしで開き直す。起動時の保守が pending を昇格する。 */
export const restart = async (
  page: Page,
  context: BrowserContext,
): Promise<Page> => {
  await page.close();
  await context.setOffline(true);
  const next = await context.newPage();
  const response = await next.goto(entry);
  expect(response?.fromServiceWorker()).toBe(true);
  return next;
};

/** 準備して開き直し、再生できる active を作る。同じページのまま、読み込み直しで起動し直す。 */
export const prepareAndReload = async (page: Page): Promise<void> => {
  await page.goto(entry);
  await prepareOnline(page);
  await page.reload();
  await expect(page.locator("#readiness")).toHaveText(saved);
};

/** asset 実体の保存数。内容で識別するため、世代間で共有する実体は 1 つと数える。 */
export const storedAssets = (page: Page): Promise<number> =>
  page.evaluate(async () => {
    const folder = await (
      await navigator.storage.getDirectory()
    ).getDirectoryHandle("abservice-assets-v1", { create: true });
    const iterator = (
      folder as FileSystemDirectoryHandle & {
        values: () => AsyncIterator<FileSystemHandle>;
      }
    ).values();
    const count = async (seen: number): Promise<number> =>
      (await iterator.next()).done === true ? seen : count(seen + 1);
    return count(0);
  });
