/* eslint-disable functional/immutable-data -- DOM state, cancellation and MessageChannel updates stay in this integration boundary. */
import {
  createDistributionClient,
  parseListenerToken,
} from "abservice-distribution-client";
import type { DistributionError } from "abservice-distribution-client";
import { assessReadiness } from "abservice-installation";
import type { AppVersion, InstallationManifest } from "abservice-installation";
import { prepare, startup } from "abservice-listening-preparation";
import type {
  ActivePackage,
  Maintenance,
  PreparationFailure,
} from "abservice-listening-preparation";
import { createAssetStore } from "abservice-offline-storage";
import type { StorageError } from "abservice-offline-storage";

const appVersion: AppVersion = [1, 10, 0];
const storageMessages: Record<StorageError, string> = {
  "invalid-manifest": "曲目の情報が不正です。",
  "unknown-asset": "音源の対応がありません。",
  "unsupported-checksum": "この音源の検証方式には対応していません。",
  corrupt: "保存音源が破損しています。オンラインで保存し直してください。",
  missing: "音源が未保存です。オンラインで保存してください。",
  aborted: "保存を中止しました。",
  "quota-exceeded": "保存容量が不足しています。空き容量を確保してください。",
  conflict: "同じ版の配布物が保存済みの内容と異なります。",
  "storage-unavailable": "この環境では保存領域を確認できません。",
};
export const storageMessage = (error: StorageError): string =>
  storageMessages[error];
const distributionMessages: Record<DistributionError, string> = {
  unauthorized:
    "端末の認証が通りませんでした（期限切れ・失効を含む）。トークンを入力し直してください。",
  forbidden: "この端末には配布が許可されていません。トークンを確認してください。",
  unavailable: "現在、配布されている試聴パッケージがありません。",
  "not-distributed": "配布物が更新されました。もう一度保存してください。",
  "source-rejected":
    "取得先に拒否されました（取得URLの期限切れを含む）。再試行してください。",
  server: "配布元が応答しません。時間をおいて再試行してください。",
  network: "通信を確認して再試行してください。",
  "invalid-response": "配布元の応答を読めませんでした。",
  "unsupported-schema": "この配布物を読むにはアプリの更新が必要です。",
  "unsupported-asset": "この配布物の素材の取得方法に対応していません。",
  aborted: "保存を中止しました。",
};
const isDistributionError = (
  error: DistributionError | StorageError,
): error is DistributionError => error in distributionMessages;
const megabytes = (bytes: number): string =>
  `${(bytes / 1_000_000).toFixed(1)} MB`;
/* Diagnoses name the stage, asset and classification only; tokens and signed URLs never reach them. */
const failureMessage = (failure: PreparationFailure): string =>
  failure.stage === "package"
    ? distributionMessages[failure.error]
    : failure.stage === "incompatible"
      ? "このアプリの版では使えない配布物です。アプリを更新してください。"
      : failure.stage === "capacity"
        ? `保存容量が不足しています（必要 ${megabytes(failure.requiredBytes)} / 空き ${megabytes(failure.availableBytes)}）。空き容量を確保してください。`
        : failure.stage === "asset"
          ? isDistributionError(failure.error)
            ? `${failure.mediaType.startsWith("audio/") ? "音源" : "画像"}を取得できませんでした（${failure.assetId}）。${distributionMessages[failure.error]}`
            : `${failure.assetId} を保存できませんでした。${storageMessages[failure.error]}`
          : failure.stage === "storage"
            ? storageMessages[failure.error]
            : failure.stage === "shell"
              ? "アプリの保存が完了していません。オンラインで保存し直してください。"
              : "音源の検証が完了しませんでした。再試行してください。";

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
const ready = "オフライン再生の準備ができました";
const unready = "オフライン再生の準備が未完了です";
const revision = "__SHELL_REVISION__";
const shellAvailable = async (): Promise<boolean> => {
  const registration =
    await navigator.serviceWorker.getRegistration("/offline-player/");
  const target = registration?.active ?? undefined;
  return target === undefined
    ? false
    : new Promise((resolve) => {
        const channel = new MessageChannel();
        const finish = (complete: boolean): void => {
          clearTimeout(timeout);
          channel.port1.close();
          channel.port2.close();
          resolve(complete);
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

/*
 * The preparation surface (token and saving) is shown only while this device is not ready or when opened on purpose,
 * so that listening never presents a credential prompt.
 */
const surface = (open: boolean): void => {
  const section = document.querySelector<HTMLElement>("#preparation");
  const opener = document.querySelector<HTMLElement>("#open-preparation");
  (section === null
    ? () => undefined
    : () => {
        section.hidden = open ? false : true;
      })();
  (opener === null
    ? () => undefined
    : () => {
        opener.hidden = open;
      })();
};
/* The package this page plays. It changes only at startup, never while the page is open. */
const session: { active: InstallationManifest | undefined } = {
  active: undefined,
};
const describeActive = (
  active: ActivePackage,
  maintenance: Maintenance,
): void => {
  const deferred =
    maintenance.kind === "maintained" &&
    maintenance.promotion.kind === "deferred"
      ? `新しい配布物は準備が未完了のため、前の版を使っています。${failureMessage(maintenance.promotion.failure)}`
      : "";
  (active.kind === "ready"
    ? () => {
        show(
          ready,
          deferred === ""
            ? "このアプリのタブを閉じてから、通信なしで開き直せます。"
            : deferred,
        );
      }
    : active.kind === "none"
      ? () => {
          show(
            unready,
            "端末のトークンを入力し、オンラインで保存してください。",
          );
        }
      : active.kind === "unreadable"
        ? () => {
            show(
              unready,
              `${storageMessage(active.error)} オンラインで保存し直してください。`,
            );
          }
        : () => {
            show(unready, failureMessage(active.failure));
          })();
};
const inspect = async (): Promise<void> => {
  const manifest = session.active;
  const opened =
    manifest === undefined ? undefined : createAssetStore(manifest);
  const observed =
    opened?.kind === "ok"
      ? await opened.value.inspect(lifetime.signal)
      : undefined;
  const shell = await shellAvailable();
  const result =
    manifest !== undefined && observed?.kind === "ok"
      ? assessReadiness(manifest, {
          ...observed.value,
          appVersion,
          appShellAvailable: shell,
        })
      : undefined;
  const complete = result?.kind === "assessed" && result.offlineReady;
  show(
    complete ? ready : unready,
    complete
      ? "このアプリのタブを閉じてから、通信なしで開き直せます。"
      : observed?.kind === "error"
        ? storageMessage(observed.error)
        : "オンラインで保存してください。更新が待機中の場合は、すべてのタブを閉じて開き直してください。",
  );
};
const tokenInput = (): HTMLInputElement | null =>
  document.querySelector<HTMLInputElement>("#token");
const prepareNow = async (): Promise<void> => {
  /* The token lives only in the input and this call; it is never stored or logged. */
  const token = parseListenerToken(tokenInput()?.value.trim() ?? "");
  const run = async (listener: NonNullable<typeof token>): Promise<void> => {
    const registration = await navigator.serviceWorker.register(
      "/offline-player/sw.js",
      { scope: "/offline-player/", type: "module", updateViaCache: "none" },
    );
    await activate(registration);
    const result = await prepare(
      {
        client: createDistributionClient({ token: listener }),
        appVersion,
        appShellAvailable: shellAvailable,
      },
      lifetime.signal,
    );
    const failed = (failure: PreparationFailure): void => {
      console.warn(
        `Listening preparation failed: ${JSON.stringify(failure)}`,
      );
      show(unready, failureMessage(failure));
    };
    await (result.kind === "failed"
      ? () => {
          failed(result.failure);
          return Promise.resolve();
        }
      : result.value.activation === "current"
        ? inspect
        : () => {
            show(
              "新しい配布物の準備ができました",
              "すべてのタブを閉じてから開き直すと、この配布物に切り替わります。",
            );
            return Promise.resolve();
          })();
  };
  await (token === undefined
    ? () => {
        show(unready, "端末のトークンの形が正しくありません。");
        return Promise.resolve();
      }
    : () => run(token))();
};
const busy = { value: false };
const guarded = async (action: () => Promise<void>): Promise<void> => {
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
              unready,
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
  /**
   * 起動時の保守（昇格・不要世代の削除）を済ませ、このページが再生に使う package を返す。
   * ページを閉じるまで世代の利用を保持し、その間に準備を受け付ける。
   */
  mount: async (): Promise<InstallationManifest | undefined> => {
    window.addEventListener(
      "pagehide",
      () => {
        lifetime.abort();
      },
      { once: true },
    );
    controls().forEach((button) => {
      button.disabled = true;
    });
    const started = await startup(
      { appVersion, appShellAvailable: shellAvailable },
      lifetime.signal,
    );
    const active =
      started.kind === "started" && "manifest" in started.active
        ? started.active.manifest
        : undefined;
    session.active = active;
    (started.kind === "started"
      ? () => {
          describeActive(started.active, started.maintenance);
        }
      : () => undefined)();
    surface(
      started.kind === "started" && started.active.kind === "ready"
        ? false
        : true,
    );
    document
      .querySelector("#open-preparation")
      ?.addEventListener("click", () => {
        surface(true);
      });
    controls().forEach((button) => {
      button.disabled = false;
    });
    document.querySelector("#prepare")?.addEventListener("click", () => {
      void guarded(prepareNow);
    });
    document.querySelector("#inspect")?.addEventListener("click", () => {
      void guarded(inspect);
    });
    return active;
  },
};
