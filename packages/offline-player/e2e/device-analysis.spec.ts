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
  await page.getByRole("button", { name: "Reel study", exact: true }).click();
  await expect(page.locator("#play-status")).toHaveText("再生できます");
  await manage(page);
  await page.locator("#device-analysis-start").click();
  await expect(page.locator("#device-analysis-status")).toContainText(
    /和音|主旋律/,
  );
  await expect(page.locator("#play")).toBeDisabled();
  await expect(page.locator("#retry")).toBeDisabled();
  await expect(page.locator("#seek")).toBeDisabled();
  const other = page.getByRole("button", { name: "Air study", exact: true });
  await expect(other).toBeDisabled();
  /* Exercise event guards as well as disabled UI while the real Worker runs. */
  await page.locator("#play").dispatchEvent("click");
  await page.locator("#retry").dispatchEvent("click");
  await other.dispatchEvent("click");
  await expect(page.locator("#play-status")).toHaveText("再生できます");
  await expect(page.locator("#track-title")).toHaveText("Reel study");
  await expect(page.locator("#device-analysis-cancel")).toBeVisible();
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
  await expect(page.locator("#play")).toBeEnabled();
  await page.locator("#play").click();
  await expect(page.locator("#play-status")).toHaveText("再生中");
});

test("読み込み中・再生中は端末解析を始めず、一時停止後に開始できる", async ({
  page,
  request,
}) => {
  await distribute(request, "v1");
  await page.goto(entry);
  await prepareOnline(page);
  await page.reload();
  await expect(page.locator("#device-analysis-start")).toBeEnabled();
  /* Hold local asset reads to observe loading without a timing-dependent race. */
  await page.evaluate(() => {
    /* eslint-disable-next-line @typescript-eslint/unbound-method -- Restore the exact prototype method; calls below explicitly supply its receiver. */
    const original = FileSystemFileHandle.prototype.getFile;
    const gate = new Promise<void>((resolve) => {
      window.addEventListener(
        "release-asset-read",
        () => {
          FileSystemFileHandle.prototype.getFile = original;
          resolve();
        },
        { once: true },
      );
    });
    FileSystemFileHandle.prototype.getFile = async function () {
      await gate;
      return original.call(this);
    };
  });
  await page.getByRole("button", { name: "Reel study", exact: true }).click();
  await expect(page.locator("#play-status")).toHaveText("読み込み中");
  await manage(page);
  const rejected = async (): Promise<void> => {
    await expect(page.locator("#device-analysis-start")).toBeDisabled();
    await page.locator("#device-analysis-start").dispatchEvent("click");
    await expect(page.locator("#device-analysis-status")).toHaveText(
      "再生・読み込み・計測を終えてから解析してください。",
    );
    await expect(page.locator("#device-analysis-cancel")).toBeHidden();
    expect(await scratchFiles(page)).toEqual([]);
  };
  await rejected();
  await page.evaluate(() =>
    window.dispatchEvent(new Event("release-asset-read")),
  );
  await expect(page.locator("#play-status")).toHaveText("再生できます");
  await page.locator("#play").click();
  await expect(page.locator("#play-status")).toHaveText("再生中");
  await rejected();
  await page.locator("#pause").click();
  await expect(page.locator("#play-status")).toHaveText("一時停止");
  await manage(page);
  await page.locator("#device-analysis-start").click();
  await expect(page.locator("#device-analysis-cancel")).toBeVisible();
  await page.locator("#device-analysis-cancel").click();
  await expect(page.locator("#device-analysis-start")).toBeEnabled();
  await expect(page.locator("#play")).toBeEnabled();
});
