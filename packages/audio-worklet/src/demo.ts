/* eslint-disable functional/immutable-data -- The demo owns DOM output and the currently selected session; DSP state remains in the worklet. */
import { startRmsSession } from "./index";
import type { Fixture } from "./index";

const state = { controller: new AbortController() };
const display = (id: string, text: string): void => {
  document.getElementById(id)?.replaceChildren(text);
};
const status = (text: string): void => {
  display("status", text);
};
const errorText = (error: unknown): string =>
  error instanceof Error ? error.message : "音声処理に失敗しました";
const fixture = (): Fixture => {
  const value = document.querySelector<HTMLSelectElement>("#fixture")?.value;
  return value === "stereo"
    ? "stereo"
    : value === "opposed"
      ? "opposed"
      : value === "left-only"
        ? "left-only"
        : value === "silence"
          ? "silence"
          : "mono";
};
const start = async (): Promise<void> => {
  state.controller.abort();
  const controller = new AbortController();
  state.controller = controller;
  const received = { count: 0, first: 0 };
  ["count", "rms", "time", "frequency"].forEach((id) => {
    display(id, "0");
  });
  status("準備中");
  try {
    await startRmsSession(
      {
        fixture: fixture(),
        notificationHz: Number(
          document.querySelector<HTMLSelectElement>("#rate")?.value ?? 30,
        ),
        onFeatures: (features) => {
          received.first =
            received.count === 0 ? features.timeSeconds : received.first;
          received.count += 1;
          display("rms", features.rms.toFixed(5));
          display("time", features.timeSeconds.toFixed(5));
          display("count", String(received.count));
          display(
            "frequency",
            received.count > 1
              ? (
                  (received.count - 1) /
                  (features.timeSeconds - received.first)
                ).toFixed(2)
              : "0",
          );
        },
        onError: (error) => {
          status(error.message);
        },
      },
      controller.signal,
    );
    controller.signal.throwIfAborted();
    status("再生中");
  } catch (error) {
    const report = controller.signal.aborted
      ? () => undefined
      : () => {
          status(errorText(error));
        };
    report();
  }
};
document.getElementById("start")?.addEventListener("click", () => {
  void start();
});
document.getElementById("stop")?.addEventListener("click", () => {
  state.controller.abort();
  status("停止中");
});
window.addEventListener("pagehide", () => {
  state.controller.abort();
});
