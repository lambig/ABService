/* eslint-disable functional/immutable-data -- Product integration tests control their own management panel and storage. */
import { test, expect } from "@playwright/test";
import type { Page } from "@playwright/test";
import { distribute, entry, prepareOnline, restart } from "./support";

const manage = async (page: Page): Promise<void> => {
  await page.locator("#management-panel").evaluate((node) => {
    node.removeAttribute("hidden");
  });
  await page.locator("#exhibition-menu").evaluate((node) => {
    node.setAttribute("open", "");
  });
  await page.locator("#device-analysis-track").selectOption("play-first");
};
const scratchFiles = (page: Page): Promise<string[]> =>
  page.evaluate(async () => {
    const directory = await (
      await navigator.storage.getDirectory()
    ).getDirectoryHandle("abservice-analysis-scratch-v1", { create: true });
    const iterator = (
      directory as FileSystemDirectoryHandle & {
        keys: () => AsyncIterator<string>;
      }
    ).keys();
    const names: string[] = [];
    const read = async (): Promise<string[]> => {
      const next = await iterator.next();
      return next.done === true ? names : (names.push(next.value), read());
    };
    return read();
  });

test("offlineの音源から両解析を保存し、再起動後の演出に使う", async ({
  page,
  context,
  request,
}) => {
  test.setTimeout(240000);
  await distribute(request, "v1");
  await page.goto(entry);
  await prepareOnline(page);
  const offline = await restart(page, context);
  await expect(offline.locator("#device-analysis-start")).toBeEnabled();
  await manage(offline);
  await offline.locator("#device-analysis-start").click();
  await expect(offline.locator("#device-analysis-status")).toHaveText(
    "主旋律・和音を保存しました。再生に利用できます。",
    { timeout: 210000 },
  );
  expect(await scratchFiles(offline)).toEqual([]);
  await offline.reload();
  await offline
    .getByRole("button", { name: "Reel study", exact: true })
    .click();
  await expect(offline.locator("#visualizer")).toHaveAttribute(
    "data-music-status",
    "ready",
  );
  await expect(offline.locator("#visualizer")).toHaveAttribute(
    "data-music-version",
    /^[a-f0-9]{64}$/,
  );
});

test("解析途中の取消は保存せず、一時ファイルを清掃して再試行できる", async ({
  page,
  request,
}) => {
  await distribute(request, "v1");
  await page.goto(entry);
  await prepareOnline(page);
  await page.reload();
  await expect(page.locator("#device-analysis-start")).toBeEnabled();
  await manage(page);
  await page.locator("#device-analysis-start").click();
  await expect(page.locator("#device-analysis-status")).toContainText(
    /和音|主旋律/,
  );
  await page.locator("#device-analysis-cancel").click();
  await expect(page.locator("#device-analysis-status")).toHaveText(
    "解析を取り消しました。",
  );
  await expect(page.locator("#device-analysis-start")).toBeEnabled();
  expect(await scratchFiles(page)).toEqual([]);
  await page.getByRole("button", { name: "Reel study", exact: true }).click();
  await expect(page.locator("#visualizer")).toHaveAttribute(
    "data-music-status",
    "missing",
  );
  await manage(page);
  await page.locator("#device-analysis-start").click();
  await expect(page.locator("#device-analysis-cancel")).toBeVisible();
  await page.locator("#management-panel").evaluate((node) => {
    node.setAttribute("hidden", "");
  });
  await expect(page.locator("#device-analysis-status")).toHaveText(
    "解析を取り消しました。",
  );
  expect(await scratchFiles(page)).toEqual([]);
});
