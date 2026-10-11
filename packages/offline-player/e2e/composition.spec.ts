import { expect, test } from "@playwright/test";
import { distribute, prepareAndReload } from "./support";

for (const viewport of [
  { width: 1280, height: 800 },
  { width: 390, height: 844 },
]) {
  test(`全画面構成: ${String(viewport.width)}pxでも作品情報・操作が独立し、描画失敗から再生を継続できる`, async ({
    page,
    request,
  }, testInfo) => {
    await page.setViewportSize(viewport);
    /* The composition must remain usable even without a GPU adapter. */
    await page.addInitScript(() => {
      /* eslint-disable-next-line functional/immutable-data -- Test the native API's unavailable boundary. */
      Object.defineProperty(navigator, "gpu", { value: undefined });
    });
    await distribute(request, "v1");
    await prepareAndReload(page);
    await page.getByRole("button", { name: "Reel study", exact: true }).click();
    await expect(page.locator("#play-status")).toHaveText("再生できます");
    await expect(page.locator("html")).toHaveAttribute(
      "data-composition",
      "true",
    );
    await expect(page.locator("#artwork")).toBeVisible();
    await expect(page.locator("#artist")).toHaveText("AB Study");
    await expect(page.locator("#track-title")).toHaveText("Reel study");
    expect(await page.locator("#visualizer").boundingBox()).toEqual({
      x: 0,
      y: 0,
      ...viewport,
    });
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth),
    ).toBeLessThanOrEqual(viewport.width);
    for (const selector of ["#play", "#pause", "#stop"]) {
      const box = await page.locator(selector).boundingBox();
      expect(box?.height).toBeGreaterThanOrEqual(44);
      expect(box?.x).toBeGreaterThanOrEqual(0);
      expect((box?.x ?? 0) + (box?.width ?? 0)).toBeLessThanOrEqual(
        viewport.width,
      );
      expect((box?.y ?? 0) + (box?.height ?? 0)).toBeLessThanOrEqual(
        viewport.height,
      );
    }
    await page.screenshot({
      path: testInfo.outputPath("selected.png"),
      fullPage: true,
    });
    await page.locator("#play").click();
    await expect(page.locator("#play-status")).toHaveText("再生中");
    await expect(page.locator("#visualizer-status")).toContainText(
      "再生と操作は続けられます",
    );
    await page.locator("#pause").click();
    await expect(page.locator("html")).toHaveAttribute(
      "data-presentation-state",
      "paused",
    );
    await page.locator("#stop").click();
    await expect(page.locator("html")).toHaveAttribute(
      "data-presentation-state",
      "selected",
    );
    await page.getByRole("button", { name: "Return", exact: true }).click();
    await expect(page.locator("#artwork")).toBeHidden();
    await expect(page.locator("#track-title")).toHaveText("Return");
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth),
    ).toBeLessThanOrEqual(viewport.width);
    await expect(page.locator("#play")).toBeEnabled();
    await page.screenshot({
      path: testInfo.outputPath("without-artwork.png"),
      fullPage: true,
    });
  });
}
