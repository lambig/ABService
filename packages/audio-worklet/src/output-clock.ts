/* eslint-disable functional/immutable-data -- The previous presentation time belongs only to this media connection. */
type MediaClock = Readonly<
  Pick<
    HTMLMediaElement,
    "currentTime" | "playbackRate" | "paused" | "seeking" | "ended"
  >
>;
type OutputClock = Readonly<{
  state: AudioContextState;
  baseLatency?: number;
  outputLatency?: number;
}>;

const seconds = (value: number | undefined): number =>
  value !== undefined && Number.isFinite(value) && value >= 0 ? value : 0;

/** Follow the current output device's estimated delay, without delaying playback itself. */
export const createOutputClock = (
  media: MediaClock,
  context: () => OutputClock | null,
  adjustment: () => number = () => 0,
) => {
  const previous: { media: number | null; presentation: number } = {
    media: null,
    presentation: 0,
  };
  const reset = (): void => {
    previous.media = null;
  };
  const time = (): number => {
    const position = seconds(media.currentTime);
    const output = context();
    const running = [media.paused, media.seeking, media.ended].some(Boolean)
      ? false
      : output?.state === "running";
    const requested = adjustment();
    const extra = Number.isFinite(requested)
      ? Math.max(-0.25, Math.min(0.25, requested))
      : 0;
    const delay = running
      ? seconds(output?.baseLatency) + seconds(output?.outputLatency) + extra
      : 0;
    const corrected = Math.max(
      0,
      position - delay * seconds(media.playbackRate),
    );
    /* A slower new route holds the pen briefly; a seek/loop explicitly starts a new history. */
    const continuous =
      running && previous.media !== null && position >= previous.media;
    const presentation = continuous
      ? Math.max(previous.presentation, corrected)
      : corrected;
    previous.media = running ? position : null;
    previous.presentation = presentation;
    return presentation;
  };
  return Object.freeze({ time, reset });
};
