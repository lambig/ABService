/* eslint-disable functional/immutable-data -- Fill bounded sampling buffers once per analysis load, yielding and checking cancellation between chunks. */
import { musicalMoment } from "abservice-installation";
import type { MusicalAnalysis } from "abservice-installation";
import { chordVelocity, planFlow, sceneStep } from "abservice-visualizer";
import type { FlowPlan } from "abservice-visualizer";

export const prepareFlow = async (
  data: MusicalAnalysis,
  signal: AbortSignal,
): Promise<FlowPlan | null> => {
  const count = Math.ceil(data.durationSeconds / sceneStep);
  const pitches = new Int16Array(count),
    velocities = new Float32Array(count);
  const chunk = async (start: number): Promise<FlowPlan | null> => {
    const fill = async (): Promise<FlowPlan | null> => {
      pitches.subarray(start, start + 512).forEach((_, offset) => {
        const i = start + offset;
        const moment = musicalMoment(data, i * sceneStep);
        pitches[i] =
          moment.melody === null ? -1 : Math.round(moment.melody.midi);
        velocities[i] = chordVelocity(moment.harmony?.quality);
      });
      await new Promise<void>((resolve) => setTimeout(resolve, 0));
      return chunk(start + 512);
    };
    return signal.aborted
      ? null
      : start >= count
        ? planFlow(pitches, velocities)
        : fill();
  };
  return chunk(0);
};
