/* eslint-disable functional/immutable-data -- This adapter owns the current GPU session, animation callback and DOM status. */
import type { AudioFeatures } from "abservice-audio-dsp";
import type { PlayerSnapshot } from "abservice-player";
import { mapFeatures, restingFrame } from "abservice-visualizer";
import { createRenderer } from "abservice-visualizer/renderer";
import { superviseRenderer } from "abservice-visualizer/session";
import type { RendererSupervisor } from "abservice-visualizer/session";

const messages = {
  starting: "描画を準備しています",
  running: "音に合わせて描画します",
  recovering: "描画を準備し直しています",
  degraded: "描画を利用できません。再生と操作は続けられます。",
  resting: "再生すると音に合わせて描画します",
} as const;

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
  const dispose = (): void => {
    cancelFrame();
    const supervisor = state.supervisor;
    state.supervisor = null;
    supervisor?.dispose();
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
      cancelFrame();
      (snapshot.phase === "paused"
        ? () => undefined
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
