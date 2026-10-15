/* eslint-disable functional/immutable-data -- Tests advance a fake media clock and change its output route. */
import { expect, it } from "vitest";
import { createOutputClock } from "./output-clock";

const fixture = () => {
  const media = {
    currentTime: 10,
    playbackRate: 1,
    paused: false,
    seeking: false,
    ended: false,
  };
  const output = {
    state: "running" as AudioContextState,
    baseLatency: 0.01,
    outputLatency: 0.02,
  };
  return { media, output, clock: createOutputClock(media, () => output) };
};

it("follows route latency without rewinding a continuous melody history", () => {
  const { media, output, clock } = fixture();
  expect(clock.time()).toBeCloseTo(9.97);
  media.currentTime = 10.05;
  output.outputLatency = 0.2;
  expect(clock.time()).toBeCloseTo(9.97);
  media.currentTime = 10.3;
  expect(clock.time()).toBeCloseTo(10.09);
  output.outputLatency = 0.02;
  expect(clock.time()).toBeCloseTo(10.27);
});

it("uses the selected position while paused/seeking and resets across playback epochs", () => {
  const { media, clock } = fixture();
  clock.time();
  media.paused = true;
  expect(clock.time()).toBe(10);
  media.currentTime = 2;
  media.seeking = true;
  expect(clock.time()).toBe(2);
  media.paused = false;
  media.seeking = false;
  clock.reset();
  expect(clock.time()).toBeCloseTo(1.97);
  media.currentTime = 0.01;
  expect(clock.time()).toBe(0);
});

it("scales the delay by playback rate and ignores unavailable or invalid estimates", () => {
  const { media, output, clock } = fixture();
  media.playbackRate = 2;
  expect(clock.time()).toBeCloseTo(9.94);
  output.baseLatency = Number.NaN;
  output.outputLatency = -1;
  expect(clock.time()).toBe(10);
  expect(createOutputClock(media, () => null).time()).toBe(10);
  expect(createOutputClock(media, () => ({ state: "running" })).time()).toBe(
    10,
  );
  output.state = "suspended";
  output.outputLatency = 1;
  expect(clock.time()).toBe(10);
});

it("applies live signed calibration within bounds without changing paused positions", () => {
  const { media, output } = fixture();
  const adjustment = { value: 0.02 };
  const clock = createOutputClock(
    media,
    () => output,
    () => adjustment.value,
  );
  expect(clock.time()).toBeCloseTo(9.95);
  adjustment.value = -0.02;
  expect(clock.time()).toBeCloseTo(9.99);
  adjustment.value = -0.25;
  expect(clock.time()).toBeCloseTo(10.22);
  adjustment.value = -1;
  expect(clock.time()).toBeCloseTo(10.22);
  media.currentTime = 11;
  adjustment.value = 1;
  expect(clock.time()).toBeCloseTo(10.72);
  adjustment.value = Number.NaN;
  expect(clock.time()).toBeCloseTo(10.97);
  media.paused = true;
  adjustment.value = 0.2;
  expect(clock.time()).toBe(11);
});
