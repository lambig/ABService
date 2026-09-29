/* eslint-disable functional/immutable-data -- The supervisor owns the current renderer and its retry budget for one presentation session. */
import type { PresentationFrame } from "./index";
import type { Renderer } from "./renderer";

/**
 * 描画の状態。再生・作品情報・操作はどの状態でも保つ。
 * - starting: 初めの renderer を作っている
 * - running: 描画している
 * - recovering: device lost の後、作り直している
 * - degraded: WebGPU が使えない、初期化に失敗した、または作り直しも失敗した。描画だけを止める
 * - disposed: セッションを閉じた
 */
export type RendererStatus =
  | "starting"
  | "running"
  | "recovering"
  | "degraded"
  | "disposed";

/** 縮退の理由。画面の説明と診断に使う。 */
export type DegradedReason = "unavailable" | "lost-again";

export type RendererSupervisor = Readonly<{
  /** running のときだけ描く。それ以外では何もしない。 */
  render: (frame: PresentationFrame) => void;
  status: () => RendererStatus;
  /** GPU 資源を放し、以後の完了・lost を無視する。 */
  dispose: () => void;
}>;

export type SupervisorOptions = Readonly<{
  create: (signal: AbortSignal) => Promise<Renderer>;
  onStatus: (status: RendererStatus, reason?: DegradedReason) => void;
}>;

/**
 * 1 つの描画セッションの renderer を監督する。device lost では今の renderer を捨て、作り直しを 1 回だけ試す。
 * 作り直しが失敗するか、作り直した renderer もまた lost したら縮退し、無限に再試行しない。
 * 描画中の例外も lost と同じに扱う。
 */
export const superviseRenderer = ({
  create,
  onStatus,
}: SupervisorOptions): RendererSupervisor => {
  const session = new AbortController();
  const state: {
    status: RendererStatus;
    renderer: Renderer | undefined;
    retried: boolean;
  } = { status: "starting", renderer: undefined, retried: false };
  const announce = (status: RendererStatus, reason?: DegradedReason): void => {
    state.status = status;
    onStatus(status, reason);
  };
  const release = (): void => {
    const renderer = state.renderer;
    state.renderer = undefined;
    renderer?.dispose();
  };
  const degrade = (reason: DegradedReason): void => {
    release();
    announce("degraded", reason);
  };
  /* A lost device is recovered once; the second loss means the device cannot sustain the scene. */
  const lose = (renderer: Renderer): void => {
    const current = [
      state.renderer === renderer,
      session.signal.aborted ? false : true,
    ].every(Boolean);
    (current
      ? state.retried
        ? () => {
            degrade("lost-again");
          }
        : () => {
            state.retried = true;
            release();
            announce("recovering");
            launch("lost-again");
          }
      : () => undefined)();
  };
  const launch = (failure: DegradedReason): void => {
    void create(session.signal).then(
      (renderer) => {
        (session.signal.aborted
          ? () => {
              renderer.dispose();
            }
          : () => {
              state.renderer = renderer;
              announce("running");
              void renderer.lost.then(() => {
                lose(renderer);
              });
            })();
      },
      () => {
        (session.signal.aborted
          ? () => undefined
          : () => {
              degrade(failure);
            })();
      },
    );
  };
  announce("starting");
  launch("unavailable");
  return Object.freeze({
    render: (frame: PresentationFrame): void => {
      const renderer = state.renderer;
      (state.status === "running" && renderer !== undefined
        ? () => {
            try {
              renderer.render(frame);
            } catch {
              lose(renderer);
            }
          }
        : () => undefined)();
    },
    status: () => state.status,
    dispose: (): void => {
      session.abort();
      release();
      announce("disposed");
    },
  });
};
