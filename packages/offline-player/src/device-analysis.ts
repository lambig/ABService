/* eslint-disable functional/immutable-data -- This boundary owns a single worker, cancellation and temporary OPFS files while holding the cross-tab job lock. */
import { getPlaybackItems, parseMusicalAnalysis } from "abservice-installation";
import type { InstallationManifest } from "abservice-installation";
import {
  createAssetStore,
  createAnalysisStore,
} from "abservice-offline-storage";
import { element, when } from "./dom";
import { storageMessage } from "./preparation";

const namespace = "abservice-analysis-scratch-v1";
type Directory = FileSystemDirectoryHandle & {
  keys: () => AsyncIterableIterator<string>;
};
const cleanup = async (): Promise<void> => {
  const root = await navigator.storage.getDirectory();
  const directory = (await root.getDirectoryHandle(namespace, {
    create: true,
  })) as Directory;
  for await (const name of directory.keys()) {
    await directory.removeEntry(name);
  }
};
type Message = {
  kind: "progress" | "result" | "error";
  phase?: string;
  pass?: number;
  completed?: number;
  total?: number;
  value?: unknown;
  message?: string;
};
const execute = (
  blob: Blob,
  audioSha256: string,
  signal: AbortSignal,
  progress: (message: Message) => void,
): Promise<unknown> =>
  new Promise((resolve, reject) => {
    signal.throwIfAborted();
    const worker = new Worker(
      `${import.meta.env.BASE_URL}assets/analysis/worker.mjs`,
      { type: "module" },
    );
    const finish = (): void => {
      worker.terminate();
      signal.removeEventListener("abort", abort);
    };
    const abort = (): void => {
      finish();
      reject(new DOMException("Cancelled", "AbortError"));
    };
    signal.addEventListener("abort", abort, { once: true });
    worker.onerror = () => {
      finish();
      reject(new Error("解析を実行できませんでした。"));
    };
    worker.onmessageerror = () => {
      finish();
      reject(new Error("解析結果を受信できませんでした。"));
    };
    worker.onmessage = (event: MessageEvent<Message>) => {
      const message = event.data;
      when(signal.aborted ? false : true, () => {
        (message.kind === "progress"
          ? () => {
              progress(message);
            }
          : message.kind === "result"
            ? () => {
                finish();
                resolve(message.value);
              }
            : () => {
                finish();
                reject(
                  new Error(
                    "解析に失敗しました。音源の形式と空き容量を確認してください。",
                  ),
                );
              })();
      });
    };
    worker.postMessage({ blob, audioSha256 });
  });

const fail = (message: string): never => {
  throw new Error(message);
};
export const deviceAnalysis = (
  manifest: InstallationManifest,
  refresh: () => Promise<void>,
  activity: Readonly<{
    canStart: () => boolean;
    busy: (value: boolean) => void;
  }>,
): void => {
  const select = element("#device-analysis-track", HTMLSelectElement);
  const start = element("#device-analysis-start", HTMLButtonElement);
  const cancel = element("#device-analysis-cancel", HTMLButtonElement);
  const status = element("#device-analysis-status", HTMLElement);
  const panel = element("#management-panel", HTMLElement);
  const json = element("#musical-analysis-file", HTMLInputElement);
  const assets = createAssetStore(manifest),
    analyses = createAnalysisStore(manifest);
  const state: { job: AbortController | null; disposed: boolean } = {
    job: null,
    disposed: false,
  };
  const items = getPlaybackItems(manifest);
  select.replaceChildren(
    ...items.map((item) => {
      const option = document.createElement("option");
      option.value = item.playbackItemId;
      option.textContent = item.title;
      return option;
    }),
  );
  start.disabled = items.length === 0;
  cancel.hidden = true;
  const stop = (): void => {
    state.job?.abort();
  };
  cancel.addEventListener("click", stop);
  const observer = new MutationObserver(() => {
    when(panel.hidden, stop);
  });
  observer.observe(panel, { attributes: true, attributeFilter: ["hidden"] });
  window.addEventListener(
    "pagehide",
    () => {
      state.disposed = true;
      stop();
      observer.disconnect();
    },
    { once: true },
  );
  start.addEventListener("click", () => {
    when(activity.canStart() ? false : true, () => {
      status.textContent = "再生・読み込み・計測を終えてから解析してください。";
    });
    when(
      [
        activity.canStart(),
        panel.hidden ? false : true,
        state.job === null,
        state.disposed ? false : true,
        json.disabled ? false : true,
      ].every(Boolean),
      () => {
        const item = items.find((item) => item.playbackItemId === select.value);
        const asset = manifest.assets.find(
          (asset) => asset.assetId === item?.audioAssetId,
        );
        (asset === undefined
          ? () => undefined
          : () => {
              const job = new AbortController();
              state.job = job;
              activity.busy(true);
              panel.dataset["analysisBusy"] = "true";
              start.disabled = true;
              select.disabled = true;
              json.disabled = true;
              cancel.hidden = false;
              const run = async (): Promise<void> => {
                try {
                  status.textContent = "ほかの解析の終了を待っています。";
                  await navigator.locks.request(
                    namespace,
                    { signal: job.signal },
                    async () => {
                      await cleanup();
                      try {
                        status.textContent = "保存された音源を確認しています。";
                        const source =
                          assets.kind === "ok"
                            ? await assets.value.read(asset.assetId, job.signal)
                            : assets;
                        const blob =
                          source.kind === "ok"
                            ? source.value
                            : fail(storageMessage(source.error));
                        const value = await execute(
                          blob,
                          asset.checksum.value,
                          job.signal,
                          (message) => {
                            const fraction = Math.floor(
                              (100 * (message.completed ?? 0)) /
                                Math.max(1, message.total ?? 1),
                            );
                            status.textContent =
                              message.phase === "harmony"
                                ? `和音を解析しています（${String(message.pass ?? 1)}/2） ${String(fraction)}%`
                                : `主旋律を解析しています ${String(fraction)}%`;
                          },
                        );
                        job.signal.throwIfAborted();
                        const analysis =
                          parseMusicalAnalysis(value) ??
                          fail("解析結果を確認できませんでした。");
                        when(
                          analysis.audioSha256 !== asset.checksum.value,
                          () => fail("音源が一致しません。"),
                        );
                        status.textContent = "主旋律・和音を保存しています。";
                        const saved = await analyses.save(
                          new Blob([JSON.stringify(analysis)]),
                          job.signal,
                        );
                        (saved.kind === "ok"
                          ? () => undefined
                          : () => fail(storageMessage(saved.error)))();
                        await refresh();
                        status.textContent =
                          "主旋律・和音を保存しました。再生に利用できます。";
                      } finally {
                        /* Worker termination releases sync handles asynchronously in some browsers. */
                        await new Promise((resolve) => setTimeout(resolve, 50));
                        await cleanup();
                      }
                    },
                  );
                } catch (error) {
                  status.textContent = job.signal.aborted
                    ? "解析を取り消しました。"
                    : error instanceof Error
                      ? error.message
                      : "解析できませんでした。";
                } finally {
                  state.job = null;
                  panel.dataset["analysisBusy"] = "false";
                  start.disabled = false;
                  select.disabled = false;
                  json.disabled = false;
                  cancel.hidden = true;
                  activity.busy(false);
                }
              };
              void run();
            })();
      },
    );
  });
};
