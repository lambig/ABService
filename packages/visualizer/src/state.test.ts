import { describe, expect, it } from "vitest";
import { transition } from "./index";
import type { PresentationEvent, PresentationState } from "./index";

const run = (
  events: readonly PresentationEvent[],
  from: PresentationState = "idle",
) =>
  events.reduce<{ state: PresentationState; resets: readonly boolean[] }>(
    (acc, event) => {
      const next = transition(acc.state, event);
      return { state: next.state, resets: [...acc.resets, next.resetFrame] };
    },
    { state: from, resets: [] },
  );

describe("presentation state", () => {
  it("follows waiting, selecting, listening, pausing, ending and selecting again", () => {
    expect(
      run([
        { kind: "select" },
        { kind: "play" },
        { kind: "pause" },
        { kind: "play" },
        { kind: "end" },
        { kind: "select" },
      ]),
    ).toEqual({
      state: "selected",
      resets: [true, false, false, false, false, true],
    });
  });

  it("returns from seeking to playing or paused, and resets the frame only on entering the seek", () => {
    expect(run([{ kind: "seek" }, { kind: "seeked", playing: true }], "playing")).toEqual({
      state: "playing",
      resets: [true, false],
    });
    expect(run([{ kind: "seek" }, { kind: "seeked", playing: false }], "paused")).toEqual({
      state: "paused",
      resets: [true, false],
    });
  });

  it("goes back to selected on stop, which is also where an ended listening returns", () => {
    (["playing", "paused", "seeking", "ended"] as const).forEach((from) => {
      expect(transition(from, { kind: "stop" })).toEqual({
        state: "selected",
        resetFrame: true,
      });
    });
  });

  it("keeps the controls reachable after a failure and recovers by selecting again", () => {
    expect(run([{ kind: "select" }, { kind: "play" }, { kind: "fail" }, { kind: "select" }])).toEqual({
      state: "selected",
      resets: [true, false, true, true],
    });
  });

  it("does not invent a state from an event that cannot happen in it", () => {
    expect(transition("idle", { kind: "play" })).toEqual({
      state: "idle",
      resetFrame: false,
    });
    expect(transition("playing", { kind: "seeked", playing: true })).toEqual({
      state: "playing",
      resetFrame: false,
    });
    expect(transition("error", { kind: "pause" })).toEqual({
      state: "error",
      resetFrame: false,
    });
  });

  it("clears back to idle from any selected state", () => {
    (["selected", "playing", "paused", "seeking", "ended", "error"] as const).forEach(
      (from) => {
        expect(transition(from, { kind: "clear" })).toEqual({
          state: "idle",
          resetFrame: true,
        });
      },
    );
  });
});
