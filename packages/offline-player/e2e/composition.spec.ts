import { expect, test } from "@playwright/test";
import { createHash } from "node:crypto";
import { artworkAssetId, distributions } from "../distribution";
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
    for (const selector of ["#play", "#seek", "#management-unlock"]) {
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
    await page.locator("#exhibition-menu").evaluate((node) => {
      node.setAttribute("open", "");
      const panel = document.querySelector<HTMLElement>("#management-panel");
      (panel === null
        ? () => undefined
        : () => {
            panel.removeAttribute("hidden");
          })();
    });
    await page.locator("#pause").click();
    await expect(page.locator("html")).toHaveAttribute(
      "data-presentation-state",
      "paused",
    );
    await page.locator("#exhibition-menu").evaluate((node) => {
      node.setAttribute("open", "");
      const panel = document.querySelector<HTMLElement>("#management-panel");
      (panel === null
        ? () => undefined
        : () => {
            panel.removeAttribute("hidden");
          })();
    });
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

for (const viewport of [
  { width: 1280, height: 800 },
  { width: 390, height: 844 },
  { width: 844, height: 390 },
]) {
  test(`作品ページの長文を表示せず、切替と再起動後も操作欄を維持する (${String(viewport.width)})`, async ({
    page,
    context,
  }, testInfo) => {
    await page.setViewportSize(viewport);
    await page.addInitScript(() => {
      /* eslint-disable-next-line functional/immutable-data -- Layout is independent of GPU availability. */
      Object.defineProperty(navigator, "gpu", { value: undefined });
    });
    const title = "雨の日の街と、遠い記憶をつなぐ音の風景".repeat(4);
    const artist = "とても長い日本語の名義と共同制作の記録".repeat(3);
    const portrait = Buffer.from(
      await page.evaluate(() => {
        const canvas = document.createElement("canvas");
        /* eslint-disable functional/immutable-data -- Synthetic portrait artwork for layout coverage. */
        canvas.width = 300;
        canvas.height = 900;
        const context =
          canvas.getContext("2d") ??
          (() => {
            throw new Error("Canvas context unavailable");
          })();
        context.fillStyle = "#426679";
        /* eslint-enable functional/immutable-data -- Synthetic artwork setup ends here. */
        context.fillRect(0, 0, canvas.width, canvas.height);
        return canvas
          .toDataURL("image/png")
          .slice("data:image/png;base64,".length);
      }),
      "base64",
    );
    await page.route(`**/assets/${artworkAssetId}`, (route) =>
      route.fulfill({ body: portrait, contentType: "image/png" }),
    );
    const content = {
      ...distributions.v1,
      assets: distributions.v1.assets.map((asset) =>
        asset.assetId === artworkAssetId
          ? {
              ...asset,
              byteLength: portrait.length,
              checksum: {
                algorithm: "sha256",
                value: createHash("sha256").update(portrait).digest("hex"),
              },
            }
          : asset,
      ),
      albums: distributions.v1.albums.map((album, index) =>
        index === 0
          ? {
              ...album,
              title,
              artistDisplayName: artist,
              description:
                "作品ページの販売説明と収録元一覧。".repeat(60) +
                "販売価格の注意書き",
              descriptionFormat: "PLAIN_TEXT",
            }
          : album,
      ),
      playbackItems: distributions.v1.playbackItems.map((item, index) =>
        index === 0 ? { ...item, title } : item,
      ),
    };
    await page.route("**/api/v1/listening/package", (route) =>
      route.fulfill({
        json: {
          ...content,
          packageVersion: createHash("sha256")
            .update(JSON.stringify(content))
            .digest("hex"),
        },
      }),
    );
    await prepareAndReload(page);
    await context.setOffline(true);
    await page.getByRole("button", { name: title, exact: true }).click();
    await expect(page.locator("#play-status")).toHaveText("再生できます");
    await expect(page.locator("#track-title")).toHaveText(title);
    await expect(page.locator("#artist")).toHaveText(artist);
    await expect(page.locator("#artwork")).toBeVisible();
    await expect
      .poll(() =>
        page
          .locator("#artwork")
          .evaluate((node) => (node as HTMLImageElement).naturalHeight),
      )
      .toBe(900);
    const region = page.getByRole("region", { name: "選択中の作品情報" });
    const controls = async (): Promise<void> => {
      await expect(page.locator("body")).not.toContainText(
        "作品ページの販売説明",
      );
      await expect(page.locator("body")).not.toContainText(
        "販売価格の注意書き",
      );
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth),
      ).toBeLessThanOrEqual(viewport.width);
      for (const selector of ["#play", "#seek", "#management-unlock"]) {
        const box = await page.locator(selector).boundingBox();
        expect(box?.height).toBeGreaterThanOrEqual(44);
        expect(box?.y).toBeGreaterThanOrEqual(0);
        expect((box?.y ?? 0) + (box?.height ?? 0)).toBeLessThanOrEqual(
          viewport.height,
        );
      }
    };
    await controls();
    await page.screenshot({
      path: testInfo.outputPath("portrait-without-description.png"),
      fullPage: true,
    });
    await page.reload();
    await page.getByRole("button", { name: title, exact: true }).click();
    await expect(page.locator("#play-status")).toHaveText("再生できます");
    await expect(page.locator("#track-title")).toHaveText(title);
    await expect(page.locator("#artist")).toHaveText(artist);
    await controls();
    await page.getByRole("button", { name: "Return", exact: true }).click();
    await expect(page.locator("#track-title")).toHaveText("Return");
    await expect(page.locator("#artwork")).toBeHidden();
    await expect.poll(() => region.evaluate((node) => node.scrollTop)).toBe(0);
    await expect(page.locator("#play")).toBeVisible();
    await expect(page.locator("#play")).toBeEnabled();
    await controls();
    await page.locator("#play").click();
    await expect(page.locator("#play-status")).toHaveText("再生中");
    await page.locator("#seek").fill("1");
    await expect
      .poll(() => page.locator("#seek").inputValue().then(Number))
      .toBeGreaterThanOrEqual(1);
    await page.locator("#exhibition-menu").evaluate((node) => {
      node.setAttribute("open", "");
      const panel = document.querySelector<HTMLElement>("#management-panel");
      (panel === null
        ? () => undefined
        : () => {
            panel.removeAttribute("hidden");
          })();
    });
    await page.locator("#stop").click();
  });
}
