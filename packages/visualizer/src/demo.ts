/* eslint-disable functional/immutable-data -- DOM and animation lifecycle state are owned exclusively by this browser adapter. */
import { fakeFeatures, mapFeatures, restingFrame } from "./index";
import type { PresentationFrame } from "./index";
import { createRenderer } from "./renderer";
import type { Renderer } from "./renderer";

const canvas = document.querySelector<HTMLCanvasElement>("#scene");
const status = document.querySelector<HTMLElement>("#status");
const metrics = document.querySelector<HTMLOutputElement>("#metrics");
const when = (condition: boolean, action: () => void): void => {
  const selected = condition ? action : () => undefined;
  selected();
};
const state = { session: new AbortController() };
const report = (message: string): void => {
  status?.replaceChildren(message);
};
const failure = (error: unknown): string =>
  error instanceof Error ? error.message : "Unknown WebGPU error";
const animate = (
  renderer: Renderer,
  signal: AbortSignal,
  onFailure: (error: unknown) => void,
): void => {
  const start = performance.now();
  const step = (
    previous: PresentationFrame,
    samples: readonly number[],
    last: number,
  ): void => {
    const id = requestAnimationFrame((now) => {
      signal.removeEventListener("abort", cancel);
      try {
        const frame = mapFeatures(fakeFeatures((now - start) / 1000), previous);
        const begin = performance.now();
        renderer.render(frame);
        const cpu = performance.now() - begin;
        const intervals = [...samples, now - last].slice(-120);
        const mean =
          intervals.reduce((sum, value) => sum + value, 0) / intervals.length;
        metrics?.replaceChildren(
          `${(1000 / mean).toFixed(1)} fps · interval ${mean.toFixed(2)} ms · CPU submit ${cpu.toFixed(2)} ms · ${frame.timeSeconds.toFixed(1)} s`,
        );
        when(signal.aborted ? false : true, () => {
          step(frame, intervals, now);
        });
      } catch (error) {
        onFailure(error);
      }
    });
    const cancel = (): void => {
      cancelAnimationFrame(id);
    };
    signal.addEventListener("abort", cancel, { once: true });
  };
  step(restingFrame, [], start);
};
const start = async (): Promise<void> => {
  state.session.abort();
  const session = new AbortController();
  state.session = session;
  report("WebGPUを初期化しています…");
  try {
    const renderer =
      canvas === null
        ? undefined
        : await createRenderer(canvas, session.signal);
    const launch = (ready: Renderer): void => {
      session.signal.addEventListener("abort", ready.dispose, { once: true });
      void ready.lost.then((info) => {
        when(session.signal.aborted ? false : true, () => {
          report(
            `WebGPU device lost: ${info.message}。開始で再初期化できます。`,
          );
        });
        session.abort();
      });
      report("実行中 · fake AudioFeatures");
      animate(ready, session.signal, (error) => {
        report(failure(error));
        session.abort();
      });
    };
    const proceed =
      renderer === undefined
        ? () => {
            report("描画領域がありません。");
          }
        : session.signal.aborted
          ? renderer.dispose
          : () => {
              launch(renderer);
            };
    proceed();
  } catch (error) {
    when(session.signal.aborted ? false : true, () => {
      report(`${failure(error)}。WebGPUが利用可能な環境で再試行してください。`);
    });
  }
};
document.querySelector("#start")?.addEventListener("click", () => {
  void start();
});
document.querySelector("#stop")?.addEventListener("click", () => {
  state.session.abort();
  report("停止しました。");
});
document.querySelector("#fullscreen")?.addEventListener("click", () => {
  void canvas?.requestFullscreen().catch((error: unknown) => {
    report(failure(error));
  });
});
window.addEventListener("pagehide", () => {
  state.session.abort();
});
