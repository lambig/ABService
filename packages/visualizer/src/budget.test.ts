import { describe, expect, it } from "vitest";
import {
  createProbe,
  defaultBudget,
  distribution,
  drawSize,
  due,
  parseBudget,
  probeRequested,
} from "./index";

describe("renderer budget", () => {
  it("keeps the current look by default", () => {
    expect(parseBudget("")).toEqual(defaultBudget);
    expect(drawSize(400, 300, 2, defaultBudget, 8192)).toEqual({
      width: 800,
      height: 600,
    });
  });

  it("reads tuning from the hash and clamps it to its range", () => {
    expect(parseBudget("#probe&dpr=1.5&scale=0.5&effects=0.25&fps=30")).toEqual({
      maxDevicePixelRatio: 1.5,
      renderScale: 0.5,
      effectDensity: 0.25,
      targetFps: 30,
    });
    expect(parseBudget("#dpr=9&scale=0.01&effects=-1&fps=1000")).toEqual({
      maxDevicePixelRatio: 3,
      renderScale: 0.25,
      effectDensity: 0,
      targetFps: 120,
    });
  });

  it("ignores unknown names and values that are not numbers", () => {
    expect(parseBudget("#scale=abc&unknown=2&fps=&dpr")).toEqual(defaultBudget);
  });

  it("draws fewer pixels with a lower render scale or DPR cap, within the device limit", () => {
    const budget = { ...defaultBudget, maxDevicePixelRatio: 1, renderScale: 0.5 };
    expect(drawSize(1280, 800, 2, budget, 8192)).toEqual({ width: 640, height: 400 });
    expect(drawSize(10000, 10, 2, defaultBudget, 4096)).toEqual({
      width: 4096,
      height: 20,
    });
    expect(drawSize(0, 0, 2, defaultBudget, 4096)).toEqual({ width: 1, height: 1 });
  });

  it("skips frames that come sooner than the target fps allows", () => {
    const thirty = { ...defaultBudget, targetFps: 30 };
    expect(due(0, undefined, thirty)).toBe(true);
    expect(due(16.7, 0, thirty)).toBe(false);
    expect(due(33.4, 0, thirty)).toBe(true);
    /* At the default 60 fps a 60 Hz display renders every frame. */
    expect(due(16.4, 0, defaultBudget)).toBe(true);
  });

  it("shows the probe only when the hash asks for it", () => {
    expect(probeRequested("#probe")).toBe(true);
    expect(probeRequested("#scale=0.5&probe")).toBe(true);
    expect(probeRequested("#prober")).toBe(false);
    expect(probeRequested("")).toBe(false);
  });
});

describe("frame probe", () => {
  it("summarises renderer CPU, whole-cycle CPU, GPU and frame intervals separately", () => {
    const probe = createProbe();
    [1, 2, 3, 4, 100].forEach((cpuMs, index) => {
      probe.record({
        rendererCpuMs: cpuMs,
        gpuMs: cpuMs * 2,
        intervalMs: 16 + index,
        pixels: 640 * 400,
      });
      probe.cycle(cpuMs + 5);
    });
    probe.skip();

    expect(probe.summary()).toEqual({
      frames: 5,
      skipped: 1,
      rendererCpu: { p50: 3, p95: 100, max: 100 },
      cycleCpu: { p50: 8, p95: 105, max: 105 },
      gpu: { p50: 6, p95: 200, max: 200 },
      interval: { p50: 18, p95: 20, max: 20 },
      pixels: 256000,
    });
  });

  it("keeps only the most recent samples so that a long run does not grow memory", () => {
    const probe = createProbe(3);
    [1, 2, 3, 4, 5].forEach((cpuMs) => {
      probe.record({ rendererCpuMs: cpuMs, pixels: 1 });
      probe.cycle(cpuMs);
    });

    expect(probe.summary()).toMatchObject({
      frames: 3,
      rendererCpu: { p50: 4, max: 5 },
      cycleCpu: { p50: 4, max: 5 },
    });
    expect(probe.summary()).not.toHaveProperty("gpu");
  });

  it("has no distribution without samples", () => {
    expect(distribution([])).toBeUndefined();
    expect(createProbe().summary()).toEqual({ frames: 0, skipped: 0 });
  });
});
