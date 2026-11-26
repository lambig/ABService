export function compactTimeline(duration: number): {
  push(frame: number, bin: number, salience: number): void;
  finish(
    harmony: readonly Readonly<{
      start: number;
      end: number;
      root: number;
      quality: string;
      bass: number | null;
    }>[],
  ): { melody: string; harmony: string };
};
