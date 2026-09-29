/* eslint-disable functional/immutable-data -- This adapter owns the current GPU session, animation callback and DOM status. */
import type { AudioFeatures } from "abservice-audio-dsp";
import type { PlayerSnapshot } from "abservice-player";
import { mapFeatures, restingFrame } from "abservice-visualizer";
import { createRenderer } from "abservice-visualizer/renderer";
import { superviseRenderer } from "abservice-visualizer/session";
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

/* A session has settled once it can draw or has given up drawing. */
const settled: ReadonlySet<RendererStatus> = new Set<RendererStatus>([
  "running",
  "degraded",
]);

export const presentation = (
  canvas: HTMLCanvasElement,
  status: HTMLElement,
) => {
  const state: {
    supervisor: RendererSupervisor | null;
    phase: PlayerSnapshot["phase"];
    frame: typeof restingFrame;
    animation: number | null;
    degraded: boolean;
  } = {
    supervisor: null,
    phase: "idle",
    frame: restingFrame,
    animation: null,
    degraded: false,
  };
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
  const dispose = (): void => {
    release(false);
    state.frame = restingFrame;
  };
  const draw = (): void => {
    state.animation = null;
    const supervisor = state.supervisor;
    (state.phase === "playing" && supervisor?.status() === "running"
      ? () => {
          supervisor.render(state.frame);
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
      create: (signal) => createRenderer(canvas, signal),
      onStatus: (next) => {
        const current =
          own.supervisor !== undefined && state.supervisor === own.supervisor;
        (current && next !== "disposed"
          ? () => {
              status.textContent = messages[next];
              state.degraded = next === "degraded";
              /* The still is kept until the new session can draw over it or has given up. */
              (settled.has(next)
                ? thaw
                : () => undefined)();
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
  const sync = (snapshot: PlayerSnapshot): void => {
    state.phase = snapshot.phase;
    const playing = (): void => {
      (state.supervisor === null
        ? state.degraded
          ? () => undefined
          : start
        : state.animation === null
          ? draw
          : () => undefined)();
    };
    const stopped = (): void => {
      (snapshot.phase === "paused"
        ? () => {
            release(true);
            status.textContent = state.degraded
              ? messages.degraded
              : messages.resting;
          }
        : () => {
            dispose();
            state.degraded = false;
            status.textContent = messages.resting;
          })();
    };
    (snapshot.phase === "playing" ? playing : stopped)();
  };
  const reset = (): void => {
    state.frame = restingFrame;
  };
  const update = (features: AudioFeatures): void => {
    state.frame = mapFeatures(features, state.frame);
  };
  return Object.freeze({ sync, update, reset, dispose });
};
