/* eslint-disable functional/immutable-data -- This adapter owns the current GPU session, animation callback and DOM status. */
import type { AudioFeatures } from "abservice-audio-dsp";
import type { PlayerSnapshot } from "abservice-player";
import { mapFeatures, restingFrame } from "abservice-visualizer";
import { createRenderer } from "abservice-visualizer/renderer";
import type { Renderer } from "abservice-visualizer/renderer";

export const presentation = (
  canvas: HTMLCanvasElement,
  status: HTMLElement,
) => {
  type Session = { abort: AbortController; renderer: Renderer | null };
  const state: {
    session: Session | null;
    phase: PlayerSnapshot["phase"];
    frame: typeof restingFrame;
    animation: number | null;
    failed: boolean;
  } = {
    session: null,
    phase: "idle",
    frame: restingFrame,
    animation: null,
    failed: false,
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
    const session = state.session;
    state.session = null;
    session?.abort.abort();
    session?.renderer?.dispose();
    state.frame = restingFrame;
  };
  const fail = (): void => {
    dispose();
    state.failed = true;
    status.textContent = "描画を利用できません。再生と操作は続けられます。";
  };
  const draw = (): void => {
    state.animation = null;
    const render = (): void => {
      try {
        state.session?.renderer?.render(state.frame);
        state.animation = requestAnimationFrame(draw);
      } catch {
        fail();
      }
    };
    (state.phase === "playing" &&
      state.session?.renderer !== null &&
      state.session !== null
      ? render
      : () => undefined)();
  };
  const start = (): void => {
    const session: Session = { abort: new AbortController(), renderer: null };
    state.session = session;
    status.textContent = "描画を準備しています";
    void createRenderer(canvas, session.abort.signal)
      .then((renderer) => {
        const accept = (): void => {
          session.renderer = renderer;
          status.textContent = "音に合わせて描画します";
          renderer.render(state.frame);
          draw();
          void renderer.lost.then(() => {
            (state.session === session ? fail : () => undefined)();
          });
        };
        (state.session === session ? accept : renderer.dispose)();
      })
      .catch(() => {
        (state.session === session ? fail : () => undefined)();
      });
  };
  const sync = (snapshot: PlayerSnapshot): void => {
    state.phase = snapshot.phase;
    const playing = (): void => {
      (state.session === null
        ? state.failed
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
            state.failed = false;
            status.textContent = "再生すると音に合わせて描画します";
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
