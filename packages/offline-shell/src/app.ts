/* eslint-disable functional/immutable-data -- DOM and MessageChannel mutation is confined to this browser entry point. */
declare const SHELL_REVISION: string;
const status = document.querySelector<HTMLElement>("#status");
const detail = document.querySelector<HTMLElement>("#detail");
const setText = (element: HTMLElement | null, text: string): void => {
  (element === null
    ? () => undefined
    : () => {
        element.textContent = text;
      })();
};
const show = (text: string, explanation: string): void => {
  setText(status, text);
  setText(detail, explanation);
};
const inspect = (target: ServiceWorker): Promise<boolean> =>
  new Promise((resolve) => {
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
          data.revision === SHELL_REVISION &&
          "complete" in data &&
          data.complete === true,
      );
    };
    target.postMessage("shell-status", [channel.port2]);
  });
const refresh = async (): Promise<void> => {
  const registration = await navigator.serviceWorker.getRegistration("./");
  const active = registration?.active ?? undefined;
  const ready = active === undefined ? false : await inspect(active);
  (ready
    ? registration?.waiting === null
      ? () => {
          show(
            "アプリの保存を確認しました",
            "通信がなくても、この画面を開き直せます。音源はまだ保存していません。",
          );
        }
      : () => {
          show(
            "更新の準備ができました",
            "このアプリのタブをすべて閉じてから開き直すと、新しい版に切り替わります。",
          );
        }
    : () => {
        show(
          "アプリの保存を確認できません",
          "オンラインで「保存・更新を確認」を押してください。",
        );
      })();
};
const watch = (registration: ServiceWorkerRegistration): void => {
  const candidate = registration.installing;
  candidate?.addEventListener("statechange", () => {
    (candidate.state === "redundant"
      ? () => {
          show(
            "更新を取得できませんでした",
            "保存済みの版は引き続き使えます。通信を確認して、もう一度お試しください。",
          );
        }
      : candidate.state === "installed"
        ? () => {
            show(
              "アプリを保存しました",
              "起動準備中です。更新の場合は、このアプリのタブをすべて閉じてから開き直してください。",
            );
          }
        : candidate.state === "activated"
          ? () => {
              void refresh();
            }
          : () => undefined)();
  });
};
const prepare = async (): Promise<void> => {
  show(
    "アプリを確認しています",
    "通信が終わるまで、この画面を開いておいてください。",
  );
  try {
    const registration = await navigator.serviceWorker.register("./sw.js", {
      scope: "./",
      type: "module",
      updateViaCache: "none",
    });
    registration.addEventListener("updatefound", () => {
      watch(registration);
    });
    watch(registration);
    await refresh();
  } catch {
    show(
      "保存・更新を確認できませんでした",
      "通信が必要です。保存済みの版がある場合、その版は引き続き使えます。",
    );
  }
};
document.querySelector("#prepare")?.addEventListener("click", () => {
  void prepare();
});
document.querySelector("#reopen")?.addEventListener("click", () => {
  location.reload();
});
void refresh().catch(() => {
  show(
    "この環境では保存を確認できません",
    "Service Workerが利用できるブラウザで開いてください。",
  );
});

export {};
