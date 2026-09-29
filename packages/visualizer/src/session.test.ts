/* eslint-disable functional/immutable-data -- The fakes record renders and disposals so tests can inspect them. */
import { describe, expect, it } from "vitest";
import { restingFrame } from "./index";
import type { Renderer } from "./renderer";
import { superviseRenderer } from "./session";
import type { DegradedReason, RendererStatus } from "./session";

type Fake = Renderer & {
  renders: number;
  disposed: boolean;
  lose: () => void;
  failNextRender: () => void;
};
const fakeRenderer = (): Fake => {
  const control: { lose: () => void; fail: boolean } = {
    lose: () => undefined,
    fail: false,
  };
  const lost = new Promise<GPUDeviceLostInfo>((resolve) => {
    control.lose = () => {
      resolve({ reason: "unknown", message: "fake loss" } as GPUDeviceLostInfo);
    };
  });
  const fake: Fake = {
    renders: 0,
    disposed: false,
    lost,
    render: () => {
      (control.fail
        ? () => {
            throw new Error("fake render failure");
          }
        : () => {
            fake.renders += 1;
          })();
    },
    dispose: () => {
      fake.disposed = true;
    },
    lose: () => {
      control.lose();
    },
    failNextRender: () => {
      control.fail = true;
    },
  };
  return fake;
};
/* The factory hands out the queued outcomes in order: a renderer, or a failure. */
const factory = (outcomes: readonly (Fake | Error)[]) => {
  const queue = [...outcomes];
  const signals: AbortSignal[] = [];
  const create = (signal: AbortSignal): Promise<Renderer> => {
    signals.push(signal);
    const next = queue.shift() ?? new Error("no more renderers");
    return next instanceof Error ? Promise.reject(next) : Promise.resolve(next);
  };
  return { create, signals };
};
const settle = (): Promise<void> =>
  new Promise((resolve) => {
    setTimeout(resolve, 0);
  });
const supervise = (outcomes: readonly (Fake | Error)[]) => {
  const statuses: (RendererStatus | `degraded:${DegradedReason}`)[] = [];
  const made = factory(outcomes);
  const supervisor = superviseRenderer({
    create: made.create,
    onStatus: (status, reason) => {
      statuses.push(status === "degraded" ? `degraded:${reason ?? "unavailable"}` : status);
    },
  });
  return { supervisor, statuses, made };
};

describe("superviseRenderer", () => {
  it("renders only while running", async () => {
    const first = fakeRenderer();
    const { supervisor, statuses } = supervise([first]);

    supervisor.render(restingFrame);
    await settle();
    supervisor.render(restingFrame);

    expect(statuses).toEqual(["starting", "running"]);
    expect(first.renders).toBe(1);
  });

  it("recreates the renderer once after a device loss", async () => {
    const first = fakeRenderer();
    const second = fakeRenderer();
    const { supervisor, statuses } = supervise([first, second]);
    await settle();

    first.lose();
    await settle();
    supervisor.render(restingFrame);

    expect(statuses).toEqual(["starting", "running", "recovering", "running"]);
    expect(first.disposed).toBe(true);
    expect(second.renders).toBe(1);
  });

  it("degrades instead of retrying again when the recreated renderer is lost too", async () => {
    const first = fakeRenderer();
    const second = fakeRenderer();
    const { supervisor, statuses, made } = supervise([first, second, fakeRenderer()]);
    await settle();

    first.lose();
    await settle();
    second.lose();
    await settle();
    supervisor.render(restingFrame);

    expect(statuses).toEqual([
      "starting",
      "running",
      "recovering",
      "running",
      "degraded:lost-again",
    ]);
    expect(second.disposed).toBe(true);
    expect(made.signals).toHaveLength(2);
  });

  it("degrades when WebGPU is unavailable, and when recreation fails", async () => {
    const unavailable = supervise([new Error("WebGPU unavailable")]);
    await settle();
    expect(unavailable.statuses).toEqual(["starting", "degraded:unavailable"]);

    const first = fakeRenderer();
    const recreation = supervise([first, new Error("adapter gone")]);
    await settle();
    first.lose();
    await settle();
    expect(recreation.statuses).toEqual([
      "starting",
      "running",
      "recovering",
      "degraded:lost-again",
    ]);
  });

  it("treats an exception while rendering like a device loss", async () => {
    const first = fakeRenderer();
    const second = fakeRenderer();
    const { supervisor, statuses } = supervise([first, second]);
    await settle();

    first.failNextRender();
    supervisor.render(restingFrame);
    await settle();

    expect(statuses).toEqual(["starting", "running", "recovering", "running"]);
    expect(first.disposed).toBe(true);
  });

  it("releases the renderer on dispose and ignores a late completion or loss", async () => {
    const first = fakeRenderer();
    const { supervisor, statuses, made } = supervise([first]);

    supervisor.dispose();
    await settle();
    first.lose();
    await settle();

    expect(made.signals[0]?.aborted).toBe(true);
    expect(first.disposed).toBe(true);
    expect(statuses).toEqual(["starting", "disposed"]);
    expect(supervisor.status()).toBe("disposed");
  });
});
