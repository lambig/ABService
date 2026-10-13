/* eslint-disable functional/immutable-data -- Browser tests observe imports and intentionally damage their own stored fixture. */
import { test, expect } from "@playwright/test";
import type { Page } from "@playwright/test";
import { manifest } from "player-study/fixture";
import { distribute, entry, prepareOnline, restart } from "./support";

const sample = (duration: number) => ({
  schemaVersion: 1,
  audioSha256: manifest.assets[0]?.checksum.value,
  durationSeconds: duration,
  analysisVersion: "a".repeat(64),
  engines: { melody: "b".repeat(64), harmony: "c".repeat(64) },
  melody: [
    { start: 0, end: 1, midi: 72, salience: 0.7 },
    { start: 2, end: 3, midi: 79, salience: 0.5 },
  ],
  harmony: [
    { start: 0, end: 2, root: 2, quality: "sus4", bass: null },
    { start: 2, end: duration - 0.2, root: 7, quality: "5", bass: null },
  ],
});
const upload = async (page: Page, value: unknown): Promise<void> => {
  await page.locator("#management-panel").evaluate((node) => {
    node.removeAttribute("hidden");
  });
  await page.locator("#exhibition-menu").evaluate((node) => {
    node.setAttribute("open", "");
  });
  await page.locator("#musical-analysis-file").setInputFiles({
    name: "synthetic-analysis.json",
    mimeType: "application/json",
    buffer: Buffer.from(JSON.stringify(value)),
  });
};
const seek = async (page: Page, seconds: number): Promise<void> => {
  await page.locator("#seek").evaluate((node: HTMLInputElement, value) => {
    node.value = String(value);
    node.dispatchEvent(new Event("input", { bubbles: true }));
  }, seconds);
};
const select = async (page: Page): Promise<number> => {
  await page.getByRole("button", { name: "Reel study", exact: true }).click();
  await expect(page.locator("#play-status")).toHaveText("再生できます");
  return Number(await page.locator("#seek").getAttribute("max"));
};

test.beforeEach(async ({ request }) => {
  await distribute(request, "v1");
});

test("解析を音源照合して保存し、offline再起動・seek・pause・作品切替・loopに追従する", async ({
  page,
  context,
}) => {
  await page.goto(entry);
  await prepareOnline(page);
  const next = await restart(page, context);
  const duration = await select(next);
  await upload(next, sample(duration));
  await expect(next.locator("#musical-analysis-import-status")).toHaveText(
    "主旋律・和音の解析を保存しました。",
  );
  const canvas = next.locator("#visualizer");
  await expect(canvas).toHaveAttribute("data-music-status", "ready");
  await expect(canvas).toHaveAttribute("data-melody-midi", "72");
  const expression = () =>
    next.locator("html").evaluate((node) => ({
      melody: node.style.getPropertyValue("--melody-lift"),
      harmony: node.style.getPropertyValue("--harmony-hue"),
      active: node.style.getPropertyValue("--harmony-presence"),
    }));
  const first = await expression();
  expect(first.melody).not.toBe("0px");
  expect(first.active).toBe("1");
  await seek(next, 1.5);
  await expect(canvas).toHaveAttribute("data-melody-midi", "");
  await expect(canvas).toHaveAttribute("data-harmony-quality", "sus4");
  expect(await expression()).toEqual({ ...first, melody: "0px" });
  await seek(next, 2.5);
  await expect(canvas).toHaveAttribute("data-melody-midi", "79");
  const second = await expression();
  expect(second.melody).not.toBe(first.melody);
  expect(second.harmony).not.toBe(first.harmony);
  await next.locator("#play").click();
  await next.locator("#pause").click();
  await expect(next.locator("#play-status")).toHaveText("一時停止");
  /* Playback may cross the note's end before the pause click; compare at a fixed paused time. */
  await seek(next, 2.5);
  await expect(canvas).toHaveAttribute("data-melody-midi", "79");
  expect(await expression()).toEqual(second);
  const paused = await canvas.getAttribute("data-music-position");
  /* Observe an interval of inactivity instead of accepting an immediately equal poll. */
  await next.waitForTimeout(300);
  expect(await canvas.getAttribute("data-music-position")).toBe(paused);
  expect(await expression()).toEqual(second);
  await next.getByRole("button", { name: "Air study", exact: true }).click();
  await expect(canvas).toHaveAttribute("data-music-status", "missing");
  expect(await expression()).toEqual({
    melody: "0px",
    harmony: "0deg",
    active: "0",
  });
  await expect(canvas).toHaveAttribute("data-melody-midi", "");
  await next.getByRole("button", { name: "Reel study", exact: true }).click();
  await expect(canvas).toHaveAttribute("data-music-status", "ready");
  await seek(next, duration - 0.4);
  await expect(next.locator("#play-status")).toHaveText("再生中");
  await expect(canvas).toHaveAttribute("data-melody-midi", "72");
  const reopened = await restart(next, context);
  await select(reopened);
  await expect(reopened.locator("#visualizer")).toHaveAttribute(
    "data-music-version",
    "a".repeat(64),
  );
  await expect(reopened.locator("#visualizer")).toHaveAttribute(
    "data-melody-midi",
    "72",
  );
});

test("不正・別音源の更新で既存結果を壊さず、保存破損時は解釈を空欄にする", async ({
  page,
  context,
}) => {
  await page.goto(entry);
  await prepareOnline(page);
  const next = await restart(page, context);
  const duration = await select(next);
  const original = sample(duration);
  await upload(next, original);
  await expect(next.locator("#musical-analysis-import-status")).toHaveText(
    "主旋律・和音の解析を保存しました。",
  );
  await upload(next, { ...original, audioSha256: "f".repeat(64) });
  await expect(next.locator("#musical-analysis-import-status")).toContainText(
    "対応する解析ファイル",
  );
  await upload(next, {
    ...original,
    melody: [{ start: 1, end: 0, midi: 80, salience: 0.8 }],
  });
  await expect(next.locator("#musical-analysis-import-status")).toContainText(
    "破損",
  );
  await expect(next.locator("#visualizer")).toHaveAttribute(
    "data-music-version",
    "a".repeat(64),
  );
  await next.evaluate(async () => {
    const directory = await (
      await navigator.storage.getDirectory()
    ).getDirectoryHandle("abservice-musical-analysis-v1");
    const values = (
      directory as FileSystemDirectoryHandle & {
        values: () => AsyncIterableIterator<FileSystemFileHandle>;
      }
    ).values();
    const first = await values.next();
    const file = first.value as FileSystemFileHandle;
    const writer = await file.createWritable();
    await writer.write("corrupt");
    await writer.close();
  });
  const reopened = await restart(next, context);
  await select(reopened);
  await expect(reopened.locator("#visualizer")).toHaveAttribute(
    "data-music-status",
    "invalid",
  );
  await expect(reopened.locator("#visualizer")).toHaveAttribute(
    "data-melody-midi",
    "",
  );
});
