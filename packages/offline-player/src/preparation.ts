/* eslint-disable functional/immutable-data -- DOM state, cancellation and MessageChannel updates stay in this integration boundary. */
import { assessReadiness, parseManifest } from "abservice-installation";
import { createAssetStore } from "abservice-offline-storage";
import type { StorageError } from "abservice-offline-storage";
import { manifest } from "player-study/fixture";

const fail = (message: string): never => {
  throw new Error(message);
};
const parsed = parseManifest(manifest);
const contract =
  parsed.kind === "manifest" ? parsed.manifest : fail("Invalid manifest");
const opened = createAssetStore(contract);
export const assetStore =
  opened.kind === "ok" ? opened.value : fail(opened.error);
const messages: Record<StorageError, string> = {
  "invalid-manifest": "曲目の情報が不正です。",
  "unknown-asset": "音源の対応がありません。",
  "unsupported-checksum": "この音源の検証方式には対応していません。",
  corrupt: "保存音源が破損しています。オンラインで保存し直してください。",
  missing: "音源が未保存です。オンラインで保存してください。",
  aborted: "保存を中止しました。",
  "quota-exceeded": "保存容量が不足しています。空き容量を確保してください。",
  "storage-unavailable": "この環境では保存領域を確認できません。",
};
export const storageMessage = (error: StorageError): string => messages[error];
const lifetime = new AbortController();
const controls = (): HTMLButtonElement[] =>
  Array.from(
    document.querySelectorAll<HTMLButtonElement>(".preparation button"),
  );
const show = (text: string, detail = ""): void => {
  lifetime.signal.throwIfAborted();
  const status = document.querySelector("#readiness");
  const explanation = document.querySelector("#preparation-detail");
  (status === null
    ? () => undefined
    : () => {
        status.textContent = text;
      })();
  (explanation === null
    ? () => undefined
    : () => {
        explanation.textContent = detail;
      })();
};
const revision = "__SHELL_REVISION__";
const shellAvailable = async (): Promise<boolean> => {
  const registration =
    await navigator.serviceWorker.getRegistration("/offline-player/");
  const target = registration?.active ?? undefined;
  return target === undefined
    ? false
    : new Promise((resolve) => {
        const channel = new MessageChannel();
        const finish = (ready: boolean): void => {
          clearTimeout(timeout);
          channel.port1.close();
          channel.port2.close();
          resolve(ready);
        };
        const timeout = setTimeout(() => {
          finish(false);
        }, 3000);
        channel.port1.onmessage = (event: MessageEvent<unknown>) => {
          const data = event.data;
          finish(
            typeof data === "object" &&
              data !== null &&
              "kind" in data &&
              data.kind === "shell-status" &&
              "revision" in data &&
              data.revision === revision &&
              "complete" in data &&
              data.complete === true,
          );
        };
        target.postMessage("shell-status", [channel.port2]);
      });
};
const inspect = async (): Promise<void> => {
  const observed = await assetStore.inspect(lifetime.signal);
  const shell = await shellAvailable();
  const result =
    observed.kind === "ok"
      ? assessReadiness(contract, {
          ...observed.value,
          appVersion: [1, 1, 0],
          appShellAvailable: shell,
        })
      : fail(storageMessage(observed.error));
  const ready = result.kind === "assessed" && result.offlineReady;
  show(
    ready
      ? "オフライン再生の準備ができました"
      : "オフライン再生の準備が未完了です",
    ready
      ? "このアプリのタブを閉じてから、通信なしで開き直せます。"
      : "オンラインで保存してください。更新が待機中の場合は、すべてのタブを閉じて開き直してください。",
  );
};
const activate = (registration: ServiceWorkerRegistration): Promise<void> => {
  const candidate = registration.installing;
  return candidate === null
    ? Promise.resolve()
    : new Promise((resolve, reject) => {
        const finish = (success: boolean): void => {
          clearTimeout(timeout);
          candidate.removeEventListener("statechange", changed);
          (success
            ? () => {
                resolve();
              }
            : () => {
                reject(new Error("アプリの保存を完了できませんでした。"));
              })();
        };
        const changed = (): void => {
          ([
            candidate.state === "activated",
            candidate.state === "installed" && registration.active !== null,
          ].some(Boolean)
            ? () => {
                finish(true);
              }
            : candidate.state === "redundant"
              ? () => {
                  finish(false);
                }
              : () => undefined)();
        };
        const timeout = setTimeout(() => {
          finish(false);
        }, 15000);
        candidate.addEventListener("statechange", changed);
        changed();
      });
};
const prepare = async (): Promise<void> => {
  const registration = await navigator.serviceWorker.register(
    "/offline-player/sw.js",
    { scope: "/offline-player/", type: "module", updateViaCache: "none" },
  );
  await activate(registration);
  await contract.assets.reduce<Promise<void>>(async (previous, asset) => {
    await previous;
    lifetime.signal.throwIfAborted();
    const stored = await assetStore.read(asset.assetId, lifetime.signal);
    const download = async (): Promise<void> => {
      const filenames: Readonly<Record<string, string>> = {
        "tone-first": "first.flac",
        "tone-second": "second.flac",
      };
      const filename =
        filenames[asset.assetId] ?? fail("音源の対応がありません。");
      const response = await fetch(`/offline-player/audio/${filename}`, {
        signal: lifetime.signal,
        cache: "no-store",
      });
      const blob = response.ok
        ? await response.blob()
        : fail(
            "音源を取得できませんでした。通信を確認して再試行してください。",
          );
      const saved = await assetStore.save(asset.assetId, blob, lifetime.signal);
      (saved.kind === "error"
        ? () => fail(storageMessage(saved.error))
        : () => undefined)();
    };
    await (stored.kind === "ok" ? () => Promise.resolve() : download)();
  }, Promise.resolve());
  await inspect();
};
const busy = { value: false };
const run = async (action: () => Promise<void>): Promise<void> => {
  const execute = async (): Promise<void> => {
    busy.value = true;
    controls().forEach((button) => {
      button.disabled = true;
    });
    show("保存状態を確認しています");
    try {
      await action();
    } catch (error) {
      (lifetime.signal.aborted
        ? () => undefined
        : () => {
            show(
              "オフライン再生の準備が未完了です",
              error instanceof Error
                ? error.message
                : "保存を確認できませんでした。",
            );
          })();
    } finally {
      busy.value = false;
      controls().forEach((button) => {
        button.disabled = false;
      });
    }
  };
  await (busy.value ? () => Promise.resolve() : execute)();
};
export const preparation = {
  invalidate: (): void => {
    show(
      "保存状態を確認してください",
      "音源を再検査し、必要に応じてオンラインで保存し直してください。",
    );
  },
  mount: (): void => {
    document.querySelector("#prepare")?.addEventListener("click", () => {
      void run(prepare);
    });
    document.querySelector("#inspect")?.addEventListener("click", () => {
      void run(inspect);
    });
    window.addEventListener(
      "pagehide",
      () => {
        lifetime.abort();
      },
      { once: true },
    );
    void run(inspect);
  },
};
