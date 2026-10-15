/* eslint-disable functional/immutable-data -- This boundary owns local PIN verification, temporary controls, and DOM timers. */
import { element } from "./dom";
import { synchronization } from "./synchronization";
const key = "exhibition-management-v1";
type Lock = Readonly<{
  salt: number[];
  digest: string;
  failures: number;
  retryAt: number;
}>;
const read = (): Lock | undefined => {
  const value: unknown = JSON.parse(localStorage.getItem(key) ?? "null");
  const data = value as Partial<Lock> | null;
  return data !== null &&
    typeof data === "object" &&
    Array.isArray(data.salt) &&
    data.salt.length === 16 &&
    data.salt.every(
      (n: unknown) =>
        typeof n === "number" && Number.isInteger(n) && n >= 0 && n < 256,
    ) &&
    typeof data.digest === "string" &&
    /^[a-f0-9]{64}$/.test(data.digest) &&
    typeof data.failures === "number" &&
    Number.isInteger(data.failures) &&
    data.failures >= 0 &&
    typeof data.retryAt === "number" &&
    Number.isFinite(data.retryAt)
    ? (data as Lock)
    : value === null
      ? undefined
      : (() => {
          throw new Error("管理ロックの保存状態を確認できません。");
        })();
};
const hash = async (pin: string, salt: number[]): Promise<string> => {
  const material = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(pin),
    "PBKDF2",
    false,
    ["deriveBits"],
  );
  const bits = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      hash: "SHA-256",
      salt: new Uint8Array(salt),
      iterations: 100000,
    },
    material,
    256,
  );
  return Array.from(new Uint8Array(bits), (n) =>
    n.toString(16).padStart(2, "0"),
  ).join("");
};
export const management = () => {
  const button = element("#management-unlock", HTMLButtonElement);
  const panel = element("#management-panel", HTMLElement);
  const presentationDelay = synchronization(panel);
  const dialog = element("#management-auth", HTMLDialogElement);
  const form = element("#management-auth-form", HTMLFormElement);
  const pin = element("#management-pin", HTMLInputElement);
  const confirm = element("#management-confirm", HTMLInputElement);
  const error = element("#management-auth-error", HTMLElement);
  const state = {
    hold: 0,
    close: 0,
    action: () => {},
    setup: false,
    busy: false,
    request: new AbortController(),
  };
  const cancelHold = (): void => {
    clearTimeout(state.hold);
    button.dataset["holding"] = "false";
  };
  const close = (): void => {
    cancelHold();
    clearTimeout(state.close);
    panel.hidden = true;
    button.setAttribute("aria-expanded", "false");
    button.textContent = "管理";
    element("#exhibition-menu", HTMLDetailsElement).open = false;
  };
  const spaceKey = "exhibition-space-v1";
  const space = element("#space-name", HTMLInputElement);
  const spaceError = element("#space-error", HTMLElement);
  const showSpace = (value: string): void => {
    const display = element("#space-display", HTMLElement);
    display.textContent = value === "" ? "LISTENING ROOM" : value;
    display.title = value;
  };
  try {
    space.value = (localStorage.getItem(spaceKey) ?? "").slice(0, 80);
    showSpace(space.value);
  } catch {
    spaceError.textContent = "スペース表示の保存状態を確認できません。";
  }
  element("#space-form", HTMLFormElement).addEventListener(
    "submit",
    (event) => {
      event.preventDefault();
      (panel.hidden
        ? () => undefined
        : () => {
            try {
              const value = space.value.trim().slice(0, 80);
              localStorage.setItem(spaceKey, value);
              space.value = value;
              showSpace(value);
              spaceError.textContent = "";
              close();
            } catch {
              spaceError.textContent =
                "保存できませんでした。もう一度お試しください。";
            }
          })();
    },
  );
  const clear = (): void => {
    state.request.abort();
    state.request = new AbortController();
    pin.value = "";
    confirm.value = "";
    state.action = () => {};
  };
  const authenticate = (action: () => void): void => {
    close();
    clear();
    error.textContent = "";
    try {
      state.setup = read() === undefined;
      state.action = action;
      element("#management-auth-title", HTMLElement).textContent = state.setup
        ? "展示の管理PINを設定"
        : "管理操作のロック解除";
      element("#management-auth-help", HTMLElement).textContent = state.setup
        ? "試聴中の管理操作を保護します。端末のロック解除とは別のPINです。"
        : "管理PINを入力してください。";
      confirm.hidden = state.setup ? false : true;
      confirm.required = state.setup;
      element("#management-confirm-label", HTMLElement).hidden = confirm.hidden;
      dialog.showModal();
      pin.focus();
    } catch {
      element("#display-status", HTMLElement).textContent =
        "管理ロックの保存状態を確認できません。展示を開始できません。";
    }
  };
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const verify = async (): Promise<void> => {
      const request = state.request;
      state.busy = true;
      error.textContent = "";
      try {
        const lock = read();
        await (lock !== undefined && lock.retryAt > Date.now()
          ? Promise.reject(
              new Error("試行回数が多いため、30秒ほど待ってください。"),
            )
          : Promise.resolve());
        await (state.setup && pin.value !== confirm.value
          ? Promise.reject(new Error("PINが一致しません。"))
          : Promise.resolve());
        const salt =
          lock?.salt ?? Array.from(crypto.getRandomValues(new Uint8Array(16)));
        const digest = await hash(pin.value, salt);
        request.signal.throwIfAborted();
        const valid = lock === undefined ? true : lock.digest === digest;
        const failures = (lock?.failures ?? 0) + 1;
        localStorage.setItem(
          key,
          JSON.stringify(
            valid
              ? { salt, digest, failures: 0, retryAt: 0 }
              : {
                  ...lock,
                  failures,
                  retryAt: failures >= 5 ? Date.now() + 30000 : 0,
                },
          ),
        );
        await (valid
          ? Promise.resolve()
          : Promise.reject(new Error("PINが違います。")));
        const action = state.action;
        clear();
        dialog.close();
        action();
      } catch (caught) {
        (request.signal.aborted
          ? () => undefined
          : () => {
              error.textContent =
                caught instanceof Error
                  ? caught.message
                  : "管理ロックを確認できません。";
              pin.value = "";
              confirm.value = "";
            })();
      } finally {
        state.busy = false;
      }
    };
    void (state.busy ? Promise.resolve() : verify());
  });
  const scheduleClose = (): void => {
    state.close = window.setTimeout(() => {
      (panel.dataset["analysisBusy"] === "true" ? scheduleClose : close)();
    }, 20000);
  };
  const unlock = (): void => {
    panel.hidden = false;
    button.setAttribute("aria-expanded", "true");
    button.textContent = "管理を閉じる";
    scheduleClose();
  };
  const hold = (): void => {
    cancelHold();
    (panel.hidden
      ? () => {
          button.dataset["holding"] = "true";
          state.hold = window.setTimeout(() => {
            authenticate(unlock);
          }, 2000);
        }
      : close)();
  };
  button.setAttribute("aria-label", "管理操作（2秒長押し）");
  button.addEventListener("pointerdown", hold);
  for (const event of ["pointerup", "pointercancel", "pointerleave", "blur"])
    button.addEventListener(event, cancelHold);
  button.addEventListener("contextmenu", (event) => {
    event.preventDefault();
  });
  button.addEventListener("keydown", (event) => {
    (event.repeat
      ? () => undefined
      : [" ", "Enter"].includes(event.key)
        ? () => {
            event.preventDefault();
            hold();
          }
        : () => undefined)();
  });
  button.addEventListener("keyup", cancelHold);
  panel.addEventListener("click", (event) => {
    (event.target instanceof HTMLButtonElement &&
      event.target.closest(
        "#space-form, #device-analysis-controls, #synchronization-form",
      ) === null
      ? close
      : () => undefined)();
  });
  panel.addEventListener("input", () => {
    clearTimeout(state.close);
    scheduleClose();
  });
  element("#management-auth-cancel", HTMLButtonElement).addEventListener(
    "click",
    () => {
      clear();
      dialog.close();
    },
  );
  dialog.addEventListener("cancel", clear);
  document.addEventListener("keydown", (event) => {
    (event.key === "Escape" ? close : () => undefined)();
  });
  window.addEventListener(
    "pagehide",
    () => {
      close();
      clear();
    },
    { once: true },
  );
  return {
    presentationDelay,
    start: (action: () => void): void => {
      try {
        (read() === undefined
          ? () => {
              authenticate(action);
            }
          : action)();
      } catch {
        authenticate(action);
      }
    },
  };
};
