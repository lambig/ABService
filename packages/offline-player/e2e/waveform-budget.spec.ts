/* eslint-disable functional/immutable-data -- Synthetic FLAC headers and browser fault injection exercise allocation boundaries. */
import { expect, test } from "@playwright/test";
import { waveformInputAllowed } from "../src/waveform";
import { distribute, prepareAndReload, openManagement } from "./support";

const header = (seconds: number, channels = 2, rate = 48000) => {
  const bytes = new Uint8Array(42);
  const view = new DataView(bytes.buffer);
  view.setUint32(0, 0x664c6143);
  view.setUint32(4, 34);
  view.setBigUint64(
    18,
    (BigInt(rate) << 44n) |
      (BigInt(channels - 1) << 41n) |
      (15n << 36n) |
      BigInt(seconds * rate),
  );
  return bytes;
};

test("波形予算: 容量・長さ・PCM・不明ヘッダーを全読込の前に判定", async () => {
  const small = new Blob([header(600)]);
  expect(await waveformInputAllowed(small)).toBe(true);
  expect(await waveformInputAllowed(new Blob([header(601)]))).toBe(false);
  expect(await waveformInputAllowed(new Blob([header(7200)]))).toBe(false);
  expect(await waveformInputAllowed(new Blob([header(0)]))).toBe(false);
  expect(await waveformInputAllowed(new Blob([header(1, 2, 0)]))).toBe(false);
  expect(await waveformInputAllowed(new Blob([header(10, 8)]))).toBe(false);
  expect(await waveformInputAllowed(new Blob([header(600, 2, 192000)]))).toBe(
    false,
  );
  expect(await waveformInputAllowed(new Blob([new Uint8Array(42)]))).toBe(
    false,
  );
  expect(await waveformInputAllowed(new Blob([header(1).slice(0, 41)]))).toBe(
    false,
  );
  const unknownBlock = header(1);
  unknownBlock[4] = 1;
  expect(await waveformInputAllowed(new Blob([unknownBlock]))).toBe(false);
  const wrongLength = header(1);
  wrongLength[7] = 33;
  expect(await waveformInputAllowed(new Blob([wrongLength]))).toBe(false);
  const last = header(1);
  last[4] = 128;
  expect(await waveformInputAllowed(new Blob([last]))).toBe(true);
  const reads: number[] = [];
  class Sized extends Blob {
    constructor(private readonly reportedSize: number) {
      super([header(600)]);
    }
    override get size() {
      return this.reportedSize;
    }
    override slice(start?: number, end?: number) {
      reads.push((end ?? 0) - (start ?? 0));
      return super.slice(start, end);
    }
    override arrayBuffer(): Promise<ArrayBuffer> {
      throw new Error("Full read forbidden");
    }
  }
  expect(await waveformInputAllowed(new Sized(64 * 1024 * 1024))).toBe(true);
  expect(reads).toEqual([42]);
  reads.length = 0;
  for (const size of [64 * 1024 * 1024 + 1, 256 * 1024 * 1024]) {
    expect(await waveformInputAllowed(new Sized(size))).toBe(false);
  }
  expect(reads).toEqual([]);
});

test("長尺波形をdecodeせず、オフラインの再生中切替とシークを維持", async ({
  page,
  request,
  context,
}) => {
  await distribute(request, "v1");
  await prepareAndReload(page);
  await context.setOffline(true);
  await page.evaluate(
    (longHeader) => {
      /* eslint-disable-next-line @typescript-eslint/unbound-method -- Called with the native blob receiver. */
      const slice = Blob.prototype.slice;
      Blob.prototype.slice = function (start, end, type) {
        return start === 0 && end === 42
          ? new Blob([new Uint8Array(longHeader)])
          : slice.call(this, start, end, type);
      };
      const scope = globalThis as typeof globalThis & {
        overviewDecodeCalls: number;
      };
      scope.overviewDecodeCalls = 0;
      const Native = OfflineAudioContext;
      globalThis.OfflineAudioContext = new Proxy(Native, {
        construct(target, args: ConstructorParameters<typeof Native>) {
          scope.overviewDecodeCalls += 1;
          return Reflect.construct(target, args);
        },
      });
    },
    Array.from(header(7200)),
  );
  await page
    .getByRole("button", { name: "Album crossfade", exact: true })
    .click();
  await expect(page.locator("#waveform-status")).toContainText(
    "再生位置はこのまま選べます",
  );
  await expect(page.locator(".waveform-path").first()).toHaveAttribute("d", "");
  await page.locator("#play").click();
  await expect(page.locator("#play-status")).toHaveText("再生中");
  await page.getByRole("button", { name: "Air study", exact: true }).click();
  await expect(page.locator("#waveform-status")).toContainText(
    "再生位置はこのまま選べます",
  );
  await expect(page.locator("#play-status")).toHaveText("再生中");
  await page.locator("#seek").fill("3");
  await expect
    .poll(() => page.locator("#seek").inputValue().then(Number))
    .toBeGreaterThanOrEqual(3);
  expect(
    await page.evaluate(
      () =>
        (globalThis as typeof globalThis & { overviewDecodeCalls: number })
          .overviewDecodeCalls,
    ),
  ).toBe(0);
  await expect(page.locator("#error")).toBeEmpty();
});

for (const viewport of [
  { width: 1100, height: 850 },
  { width: 800, height: 1100 },
]) {
  test(`代表負荷の管理操作は通常PIN経路で開く ${String(viewport.width)}`, async ({
    page,
    request,
  }) => {
    await distribute(request, "v1");
    await prepareAndReload(page);
    await page.goto("/offline-player/#probe&load=scene&scale=0.5&fps=30");
    await page.reload();
    await page.setViewportSize(viewport);
    await expect(page.locator("html")).toHaveAttribute(
      "data-scene-load",
      "true",
    );
    await page.getByRole("button", { name: "Reel study", exact: true }).click();
    await page.locator("#play").click();
    await expect(page.locator("#play-status")).toHaveText("再生中");
    await expect(page.locator("#management-unlock")).toBeInViewport();
    await expect(page.locator("#pause")).toBeHidden();
    await openManagement(page);
    await page.locator("#exhibition-menu > summary").click();
    await page.locator("#pause").click();
    await expect(page.locator("#play-status")).toHaveText("一時停止");
    await expect(page.locator("#management-panel")).toBeHidden();
    await openManagement(page);
    await page.locator("#exhibition-menu > summary").click();
    await page.locator("#stop").click();
    await expect(page.locator("#play-status")).toHaveText("停止中");
  });
}
