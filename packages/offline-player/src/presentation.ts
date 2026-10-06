/* eslint-disable functional/immutable-data -- This adapter owns the current GPU session, animation callback and DOM status. */
import type { AudioFeatures } from "abservice-audio-dsp";
import type { PlayerSnapshot } from "abservice-player";
import type { ListeningAlbum } from "abservice-listening-presentation";
import {
  createProbe,
  due,
  followPlayback,
  mapFeatures,
  parseBudget,
  probeRequested,
  restingFrame,
  sceneLoadRequested,
} from "abservice-visualizer";
import type { FrameProbe, PresentationState } from "abservice-visualizer";
import { createRenderer } from "abservice-visualizer/renderer";
import { planSession, superviseRenderer } from "abservice-visualizer/session";
import type {
  RendererStatus,
  RendererSupervisor,
} from "abservice-visualizer/session";

const messages = {
  starting: "描画を準備しています",
  running: "音に合わせて描画します",
  recovering: "描画を準備し直しています",
  degraded: "描画を利用できません。再生と操作は続けられます。",
  resting: "再生すると音に合わせて描画します",
} as const;

/* Drawing continues through a seek that began while playing; the frame is back at rest by then. */
const drawing: ReadonlySet<PresentationState> = new Set<PresentationState>([
  "playing",
  "seeking",
]);

/* A session has settled once it can draw or has given up drawing. */
const settled: ReadonlySet<RendererStatus> = new Set<RendererStatus>([
  "running",
  "degraded",
]);

/* Budget and probe are read once from the hash: a query would keep the Service Worker from serving the page offline. */
const budget = parseBudget(location.hash);
const representative = sceneLoadRequested(location.hash);
document.documentElement.dataset["sceneLoad"] = String(representative);
const probe: FrameProbe | undefined = probeRequested(location.hash)
  ? createProbe()
  : undefined;

/* Main-thread work between two drawn frames; the next drawn frame reports it as its cycle. */
const work = { pendingMs: 0 };
/** main thread の仕事を測り、次に描くフレームの cycleCpu に足す。`#probe` のときだけ測る。 */
export const measured = <A extends unknown[]>(
  task: (...args: A) => void,
): ((...args: A) => void) =>
  probe === undefined
    ? task
    : (...args: A): void => {
        const begin = performance.now();
        task(...args);
        work.pendingMs += performance.now() - begin;
      };

/** 観測の要約と起動時の計測点。`#probe` のときだけ作る。 */
export const probeReport = (): string | undefined => {
  const summary = probe?.summary();
  const marks = performance
    .getEntriesByType("mark")
    .filter((entry) => entry.name.startsWith("listening:"))
    .map((entry) => `${entry.name} ${entry.startTime.toFixed(0)} ms`);
  const format = (
    name: string,
    value?: { p50: number; p95: number; max: number },
  ) =>
    value === undefined
      ? `${name} -`
      : `${name} p50 ${value.p50.toFixed(2)} / p95 ${value.p95.toFixed(2)} / max ${value.max.toFixed(2)} ms`;
  return summary === undefined
    ? undefined
    : [
        ...marks,
        `scene ${representative ? "representative-v1" : "study"}`,
        `budget dpr ${String(budget.maxDevicePixelRatio)} scale ${String(budget.renderScale)} effects ${String(budget.effectDensity)} fps ${String(budget.targetFps)}`,
        `frames ${String(summary.frames)} skipped ${String(summary.skipped)} pixels ${String(summary.pixels ?? 0)}`,
        format("cycle cpu", summary.cycleCpu),
        format("renderer cpu", summary.rendererCpu),
        format("gpu (submit→done)", summary.gpu),
        format("interval", summary.interval),
      ].join("\n");
};

export const presentation = (
  canvas: HTMLCanvasElement,
  status: HTMLElement,
) => {
  const state: {
    supervisor: RendererSupervisor | null;
    presentation: PresentationState;
    observed: PlayerSnapshot | undefined;
    frame: typeof restingFrame;
    animation: number | null;
    degraded: boolean;
    drawnAt: number | undefined;
    interval: number | undefined;
    content: ListeningAlbum | undefined;
  } = {
    supervisor: null,
    presentation: "idle",
    observed: undefined,
    frame: restingFrame,
    animation: null,
    degraded: false,
    drawnAt: undefined,
    interval: undefined,
    content: undefined,
  };
  canvas.dataset["presentationState"] = state.presentation;
  const cancelFrame = (): void => {
    (state.animation === null
      ? () => undefined
      : () => {
          cancelAnimationFrame(state.animation as number);
        })();
    state.animation = null;
  };
  /* A paused scene stays visible as a still behind the released canvas; the token drops a still that arrives after resuming. */
  const still: { token: number; url: string | null } = { token: 0, url: null };
  const thaw = (): void => {
    still.token += 1;
    (still.url === null
      ? () => undefined
      : () => {
          URL.revokeObjectURL(still.url as string);
        })();
    still.url = null;
    canvas.style.backgroundImage = "";
  };
  const freeze = (supervisor: RendererSupervisor): void => {
    thaw();
    const token = still.token;
    /* The canvas can be copied only within the task that rendered it, so the current frame is drawn once more here. */
    supervisor.render(state.frame);
    const copy = document.createElement("canvas");
    copy.width = canvas.width;
    copy.height = canvas.height;
    copy.getContext("2d")?.drawImage(canvas, 0, 0);
    copy.toBlob((blob) => {
      (blob !== null && token === still.token
        ? () => {
            still.url = URL.createObjectURL(blob);
            canvas.style.backgroundImage = `url("${still.url}")`;
            canvas.style.backgroundSize = "100% 100%";
          }
        : () => undefined)();
    });
  };
  /* The GPU session lives only while playing; the frame and the degraded verdict outlive it across a pause. */
  const release = (keepStill: boolean): void => {
    cancelFrame();
    state.drawnAt = undefined;
    state.interval = undefined;
    const supervisor = state.supervisor;
    state.supervisor = null;
    /* A repeated pause snapshot has no session left and keeps the still it already has. */
    (keepStill
      ? supervisor?.status() === "running"
        ? () => {
            freeze(supervisor);
          }
        : () => undefined
      : thaw)();
    supervisor?.dispose();
  };
  const reset = (): void => {
    state.frame = restingFrame;
  };
  const dispose = (): void => {
    release(false);
    reset();
  };
  /* A frame that comes sooner than the target fps is skipped, not drawn; the loop keeps running. */
  const draw = (): void => {
    state.animation = null;
    const supervisor = state.supervisor;
    const now = performance.now();
    const render = (running: RendererSupervisor): void => {
      state.interval =
        state.drawnAt === undefined ? undefined : now - state.drawnAt;
      state.drawnAt = now;
      running.render(state.frame);
      (performance.getEntriesByName("listening:first-frame").length === 0
        ? () => {
            performance.mark("listening:first-frame");
          }
        : () => undefined)();
    };
    /* A drawn frame closes the cycle with the work since the previous one; a skipped frame's own work carries over. */
    const close = (): void => {
      probe?.cycle(work.pendingMs + performance.now() - now);
      work.pendingMs = 0;
    };
    (drawing.has(state.presentation) && supervisor?.status() === "running"
      ? () => {
          (due(now, state.drawnAt, budget)
            ? () => {
                render(supervisor);
                close();
              }
            : () => {
                probe?.skip();
                work.pendingMs += performance.now() - now;
              })();
          state.animation = requestAnimationFrame(draw);
        }
      : () => undefined)();
  };
  /* Rendering recovers once from a lost device; after that it degrades and playback carries on without it. */
  const start = (): void => {
    /* The first status arrives while the supervisor is still being created, so it is compared through a holder. */
    const own: { supervisor: RendererSupervisor | undefined } = {
      supervisor: undefined,
    };
    const supervisor = superviseRenderer({
      create: (signal) =>
        createRenderer(canvas, signal, {
          budget,
          ...(representative
            ? {
                representative,
                ...(state.content === undefined
                  ? {}
                  : { content: state.content }),
              }
            : {}),
          ...(probe === undefined
            ? {}
            : {
                onSample: (sample) => {
                  probe.record({
                    ...sample,
                    ...(state.interval === undefined
                      ? {}
                      : { intervalMs: state.interval }),
                  });
                },
              }),
        }),
      onStatus: (next) => {
        const current =
          own.supervisor !== undefined && state.supervisor === own.supervisor;
        (current && next !== "disposed"
          ? () => {
              status.textContent = messages[next];
              state.degraded = next === "degraded";
              /* The still is kept until the new session can draw over it or has given up. */
              (settled.has(next) ? thaw : () => undefined)();
              (next === "running" && state.animation === null
                ? draw
                : () => undefined)();
            }
          : () => undefined)();
      },
    });
    own.supervisor = supervisor;
    state.supervisor = supervisor;
    status.textContent = messages.starting;
  };
  /* The presentation state, through planSession, decides the GPU lifetime and whether the frame goes back to rest. */
  const sync = (snapshot: PlayerSnapshot, content?: ListeningAlbum): void => {
    state.content = content;
    const step = followPlayback(state.presentation, state.observed, snapshot);
    const plan = planSession(step, state.frame);
    state.observed = snapshot;
    state.presentation = step.state;
    state.frame = plan.frame;
    canvas.dataset["presentationState"] = step.state;
    const drawn = (): void => {
      (state.supervisor === null
        ? state.degraded
          ? () => undefined
          : start
        : state.animation === null
          ? draw
          : () => undefined)();
    };
    const keep = (): void => {
      (state.supervisor !== null && state.animation === null
        ? draw
        : () => undefined)();
    };
    const still = (): void => {
      release(true);
      status.textContent = state.degraded
        ? messages.degraded
        : messages.resting;
    };
    /* Releasing leaves the frame to the plan; only resetFrame puts it back at rest. */
    const released = (): void => {
      release(false);
      state.degraded = false;
      status.textContent = messages.resting;
    };
    ({ draw: drawn, keep, still, release: released })[plan.session]();
  };
  const update = (features: AudioFeatures): void => {
    state.frame = mapFeatures(features, state.frame);
  };
  return Object.freeze({ sync, update: measured(update), reset, dispose });
};
