import type { PcmBlock } from "./index";

export const validBlock = (block: PcmBlock): boolean =>
  [1, 2].includes(block.channels.length) &&
  Number.isFinite(block.sampleRate) &&
  block.sampleRate > 0 &&
  Number.isFinite(block.timeSeconds) &&
  block.timeSeconds >= 0 &&
  block.channels.every(
    (channel) =>
      channel.length > 0 &&
      channel.length === block.channels[0]?.length &&
      channel.every(Number.isFinite),
  );
