/* eslint-disable functional/immutable-data -- Media要素と現在のsession参照はブラウザ副作用の境界に閉じる。外へ通知する状態はimmutable snapshot。 */
import type { InstallationManifest } from "abservice-installation";
import type { LocalAssetResolver, Player, PlayerSnapshot } from "./index";

type Session = {
  audio: HTMLAudioElement;
  abort: AbortController;
  intent: AbortController;
  url: string | null;
};
const idle: PlayerSnapshot = Object.freeze({
  phase: "idle",
  trackId: null,
  position: 0,
  duration: 0,
  error: null,
});
const noop = (): void => {};
const when = (condition: boolean, action: () => void): void => {
  (condition ? action : noop)();
};
const release = (session: Session): void => {
  session.abort.abort();
  session.intent.abort();
  session.audio.pause();
  session.audio.removeAttribute("src");
  session.audio.load();
  const url = session.url;
  (url === null
    ? noop
    : () => {
        URL.revokeObjectURL(url);
      })();
  session.url = null;
};
const message = (error: unknown): string =>
  error instanceof Error ? error.message : String(error);

export const player = (
  manifest: InstallationManifest,
  resolve: LocalAssetResolver,
  changed: (state: PlayerSnapshot) => void,
): Player => {
  const cell: {
    session: Session | null;
    state: PlayerSnapshot;
    disposed: boolean;
  } = {
    session: null,
    state: idle,
    disposed: false,
  };
  const publish = (patch: Partial<PlayerSnapshot>): void => {
    cell.state = Object.freeze({ ...cell.state, ...patch });
    changed(cell.state);
  };
  const current = (session: Session): boolean =>
    session.abort.signal.aborted ? false : cell.session === session;
  const clear = (): void => {
    const previous = cell.session;
    cell.session = null;
    (previous === null
      ? noop
      : () => {
          release(previous);
        })();
  };
  const fail = (session: Session, error: string): void => {
    when(current(session), () => {
      clear();
      publish({ phase: "error", position: 0, duration: 0, error });
    });
  };
  const position = (session: Session): void => {
    when(current(session), () => {
      publish({
        position: Number.isFinite(session.audio.currentTime)
          ? session.audio.currentTime
          : 0,
      });
    });
  };
  const load = async (trackId: string, assetId: string): Promise<void> => {
    clear();
    const session: Session = {
      audio: new Audio(),
      abort: new AbortController(),
      intent: new AbortController(),
      url: null,
    };
    cell.session = session;
    const listen = (event: string, handler: () => void): void => {
      session.audio.addEventListener(event, handler, {
        signal: session.abort.signal,
      });
    };
    listen("loadedmetadata", () => {
      when(current(session), () => {
        (Number.isFinite(session.audio.duration) && session.audio.duration > 0
          ? () => {
              publish({ phase: "ready", duration: session.audio.duration });
            }
          : () => {
              fail(
                session,
                "音源の長さを取得できません。曲を選び直してください。",
              );
            })();
      });
    });
    listen("timeupdate", () => {
      position(session);
    });
    listen("seeked", () => {
      position(session);
    });
    listen("ended", () => {
      when(current(session), () => {
        publish({ phase: "ended", position: session.audio.duration });
      });
    });
    listen("error", () => {
      fail(
        session,
        "音源を読み込めません。形式・ファイルを確認して曲を選び直してください。",
      );
    });
    publish({ ...idle, phase: "loading", trackId });
    await Promise.resolve()
      .then(() => resolve(assetId, session.abort.signal))
      .then((blob) => {
        when(current(session), () => {
          session.url = URL.createObjectURL(blob);
          session.audio.preload = "auto";
          session.audio.src = session.url;
          session.audio.load();
        });
      })
      .catch((error: unknown) => {
        fail(
          session,
          `音源の取得に失敗しました。曲を選び直してください。${message(error)}`,
        );
      });
  };
  const select = async (trackId: string): Promise<void> => {
    const track = manifest.albums
      .flatMap((album) => album.tracks)
      .find((entry) => entry.trackId === trackId);
    await (cell.disposed
      ? Promise.resolve()
      : track === undefined
        ? (() => {
            clear();
            publish({
              ...idle,
              phase: "error",
              error: "選択した曲がManifestにありません。",
            });
            return Promise.resolve();
          })()
        : load(track.trackId, track.audioAssetId));
  };
  const play = async (): Promise<void> => {
    const session = cell.session;
    await (session === null
      ? Promise.resolve()
      : cell.state.phase === "loading"
        ? Promise.resolve()
        : (() => {
            session.intent.abort();
            const intent = new AbortController();
            session.intent = intent;
            return session.audio
              .play()
              .then(() => {
                when(intent.signal.aborted ? false : current(session), () => {
                  publish({ phase: "playing", error: null });
                });
              })
              .catch((error: unknown) => {
                when(intent.signal.aborted ? false : current(session), () => {
                  publish({
                    phase: "paused",
                    error: `再生できません。再生ボタンで再試行してください。${message(error)}`,
                  });
                });
              });
          })());
  };
  const withSession = (action: (session: Session) => void): void => {
    const session = cell.session;
    (session === null
      ? noop
      : () => {
          action(session);
        })();
  };
  const pause = (): void => {
    withSession((session) => {
      when(cell.state.phase !== "loading", () => {
        session.intent.abort();
        session.audio.pause();
        publish({ phase: "paused", position: session.audio.currentTime });
      });
    });
  };
  const seek = (seconds: number): void => {
    withSession((session) => {
      when(cell.state.duration > 0 && Number.isFinite(seconds), () => {
        session.audio.currentTime = Math.max(
          0,
          Math.min(cell.state.duration, seconds),
        );
        publish({
          position: session.audio.currentTime,
          phase: cell.state.phase === "ended" ? "paused" : cell.state.phase,
        });
      });
    });
  };
  const stop = (): void => {
    when(cell.disposed ? false : true, () => {
      clear();
      publish({ ...idle, trackId: cell.state.trackId });
    });
  };
  const dispose = (): void => {
    when(cell.disposed ? false : true, () => {
      cell.disposed = true;
      clear();
      publish({ ...idle, trackId: cell.state.trackId });
    });
  };
  return Object.freeze({
    snapshot: () => cell.state,
    select,
    play,
    pause,
    seek,
    stop,
    dispose,
  });
};
