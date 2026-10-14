/* eslint-disable functional/immutable-data -- Observe the real shader with controlled PresentationFrame values at the GPU upload boundary. */
import { expect, test } from "@playwright/test";
import { distribute, prepareAndReload } from "./support";

type Probe = {
  time: number;
  energy: number;
  impulse: number;
  music: number[];
  pixels: number[];
  draws: number;
  errors: string[];
};
type Scope = typeof globalThis & { motionProbe: Probe };

test("無音では静止し、細線の流れと立上がりの濃淡・両解析は独立して応答する", async ({
  page,
  request,
}) => {
  await page.addInitScript(() => {
    const probe: Probe = {
      time: 3,
      energy: 0,
      impulse: 0,
      music: [0, 0, 0, 0],
      pixels: [],
      draws: 0,
      errors: [],
    };
    (globalThis as Scope).motionProbe = probe;
    const copy = document.createElement("canvas");
    copy.width = 64;
    copy.height = 64;
    const context = copy.getContext("2d");
    /* eslint-disable-next-line @typescript-eslint/unbound-method -- Native receivers are retained below. */
    const write = GPUQueue.prototype.writeBuffer;
    GPUQueue.prototype.writeBuffer = function (buffer, offset, data, ...rest) {
      const values =
        data instanceof Float32Array && data.length === 20
          ? new Float32Array([
              data[0] ?? 0,
              data[1] ?? 0,
              probe.time,
              1,
              1,
              0,
              0.15 + probe.energy * 0.4,
              probe.impulse,
              0,
              0.65,
              0,
              1,
              ...probe.music,
              ...(probe.music.length === 4
                ? [4 / 12, 7 / 12, 0, probe.time]
                : []),
            ])
          : data;
      write.call(this, buffer, offset, values, ...rest);
    };
    /* eslint-disable-next-line @typescript-eslint/unbound-method -- Called with the native queue. */
    const submit = GPUQueue.prototype.submit;
    GPUQueue.prototype.submit = function (commands) {
      submit.call(this, commands);
      const canvas = document.querySelector<HTMLCanvasElement>("#visualizer");
      (canvas !== null && context !== null
        ? () => {
            context.drawImage(canvas, 0, 0, 64, 64);
            probe.pixels = Array.from(context.getImageData(0, 0, 64, 64).data);
            probe.draws += 1;
          }
        : () => undefined)();
    };
    /* eslint-disable-next-line @typescript-eslint/unbound-method -- Called with the native adapter. */
    const create = GPUAdapter.prototype.requestDevice;
    GPUAdapter.prototype.requestDevice = async function (...args) {
      const device = await create.apply(this, args);
      device.addEventListener("uncapturederror", (event) =>
        probe.errors.push(event.error.message),
      );
      return device;
    };
  });
  test.setTimeout(60000);
  await distribute(request, "v1");
  await prepareAndReload(page);
  await page.getByRole("button", { name: "Reel study", exact: true }).click();
  await expect(page.locator("#play-status")).toHaveText("再生できます");
  await page.locator("#play").click();
  await expect(page.locator("#visualizer-status")).toHaveAttribute(
    "data-renderer-state",
    "running",
  );
  const frame = async (
    time: number,
    energy: number,
    impulse: number,
    music: number[] = [0, 0, 0, 0],
  ): Promise<number[]> => {
    const before = await page.evaluate(
      (values) => {
        const p = (globalThis as Scope).motionProbe;
        Object.assign(p, values);
        return p.draws;
      },
      { time, energy, impulse, music },
    );
    await expect
      .poll(() => page.evaluate(() => (globalThis as Scope).motionProbe.draws))
      .toBeGreaterThan(before + 1);
    return page.evaluate(() => (globalThis as Scope).motionProbe.pixels);
  };
  const silent = await frame(3, 0, 0);
  expect(new Set(silent).size).toBeGreaterThan(8);
  expect(await frame(10, 0, 0)).toEqual(silent);
  const sustained = await frame(3, 0.7, 0);
  expect(sustained).not.toEqual(silent);
  expect(await frame(10, 0.7, 0)).not.toEqual(sustained);
  expect(await frame(3, 0.7, 1)).not.toEqual(sustained);
  const melody = await frame(3, 0, 0, [-0.5, 1, 0, 0]);
  expect(melody).not.toEqual(silent);
  expect(await frame(3, 0, 0, [0.5, 1, 0, 0])).not.toEqual(melody);
  const harmony = await frame(3, 0, 0, [0, 0, 0, 1]);
  expect(harmony).not.toEqual(silent);
  expect(await frame(3, 0, 0, [0, 0, 0.5, 1])).not.toEqual(harmony);
  expect(await frame(3, 0, 0, [0, 0, 0, 1, 3 / 12, 7 / 12, 0, 3])).not.toEqual(
    harmony,
  );
  expect(await frame(3, 0, 0, [0, 0, 0, 1, 4 / 12, 8 / 12, 0, 3])).not.toEqual(
    harmony,
  );
  expect(await frame(3, 0, 0)).toEqual(silent);
  expect(
    await page.evaluate(() => (globalThis as Scope).motionProbe.errors),
  ).toEqual([]);
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
