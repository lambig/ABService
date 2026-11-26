/* eslint-disable functional/immutable-data -- Pairing lifetime and DOM updates are confined to this browser boundary. */
import { z } from "zod";

const reply = z.object({
  state: z.enum([
    "PENDING",
    "APPROVED",
    "DENIED",
    "EXPIRED",
    "CONSUMED",
    "ISSUED",
    "NOT_FOUND",
    "RATE_LIMITED",
    "SLOW_DOWN",
  ]),
  requestId: z.uuid().nullable(),
  label: z.string().nullable(),
  expiresAt: z.string().nullable(),
  deviceId: z.uuid().nullable(),
  retryAfterSeconds: z.number().int().nonnegative(),
  code: z.string().nullable(),
  proof: z.string().nullable(),
  token: z.string().nullable(),
});
type Reply = z.infer<typeof reply>;
const request = async (
  path: string,
  body: unknown,
  signal: AbortSignal,
): Promise<Reply> => {
  const response = await fetch(`/api/v1/listening/authorizations${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    cache: "no-store",
    redirect: "error",
    credentials: "omit",
    signal: AbortSignal.any([signal, AbortSignal.timeout(30_000)]),
  });
  const parsed = reply.safeParse(await response.json());
  return [response.ok, response.status === 429].some(Boolean) && parsed.success
    ? parsed.data
    : Promise.reject(new Error("接続の応答を確認できません。"));
};
const wait = (signal: AbortSignal): Promise<void> =>
  new Promise((resolve, reject) => {
    const finish = (): void => {
      signal.removeEventListener("abort", abort);
      resolve();
    };
    const timeout = setTimeout(finish, 5_000);
    const abort = (): void => {
      clearTimeout(timeout);
      reject(new Error("接続を中止しました。"));
    };
    signal.addEventListener("abort", abort, { once: true });
    (signal.aborted ? abort : () => undefined)();
  });
const setText = (selector: string, text: string): void => {
  const target = document.querySelector(selector);
  (target === null
    ? () => undefined
    : () => {
        target.textContent = text;
      })();
};

/** Pair in this tab only. Returned credentials are passed directly to preparation, never persisted. */
export const mountPairing = (
  lifetime: AbortSignal,
  acceptToken: (token: string) => void,
): void => {
  const active: { controller: AbortController | undefined } = {
    controller: undefined,
  };
  const begin = async (): Promise<void> => {
    active.controller?.abort();
    const controller = new AbortController();
    active.controller = controller;
    const signal = AbortSignal.any([lifetime, controller.signal]);
    acceptToken("");
    setText("#pair-code", "");
    setText("#pair-status", "接続コードを発行しています。");
    const label =
      document.querySelector<HTMLInputElement>("#pair-label")?.value.trim() ??
      "";
    try {
      const started = await request("", { label }, signal);
      signal.throwIfAborted();
      const poll = async (
        id: string,
        proof: string,
        expiresAt: string,
      ): Promise<void> => {
        await wait(signal);
        signal.throwIfAborted();
        const result =
          Date.now() >= Date.parse(expiresAt)
            ? undefined
            : await request("/token", { requestId: id, proof }, signal);
        signal.throwIfAborted();
        const next = async (): Promise<void> => poll(id, proof, expiresAt);
        const finish = (): Promise<void> => {
          (result?.state === "ISSUED" &&
            result.token !== null &&
            /^abs_device_[0-9a-f]{64}$/u.test(result.token)
            ? () => {
                acceptToken(result.token ?? "");
                setText(
                  "#pair-status",
                  "接続が許可されました。この試聴機で「アプリと音源を保存」を押してください。",
                );
              }
            : () => {
                setText(
                  "#pair-status",
                  result?.state === "CONSUMED"
                    ? `認証は発行済みですが受取を確認できません。PCで確認用ID ${id} の認証を失効し、新しい接続を開始してください。`
                    : "接続が拒否されたか期限が切れました。新しい接続を開始してください。",
                );
              })();
          return Promise.resolve();
        };
        await (
          ["PENDING", "SLOW_DOWN"].includes(result?.state ?? "") ? next : finish
        )();
      };
      await (
        started.state === "PENDING" &&
          started.requestId !== null &&
          started.proof !== null &&
          started.code !== null &&
          started.expiresAt !== null
          ? async () => {
              setText("#pair-code", started.code ?? "");
              setText(
                "#pair-status",
                `このコードをPCの管理画面へ入力して許可してください。接続期限 ${started.expiresAt ?? ""}。確認用ID ${started.requestId ?? ""}`,
              );
              await poll(
                started.requestId ?? "",
                started.proof ?? "",
                started.expiresAt ?? "",
              );
            }
          : () => {
              setText(
                "#pair-status",
                "接続開始の上限です。5分後にもう一度操作してください。",
              );
              return Promise.resolve();
            }
      )();
    } catch {
      (signal.aborted
        ? () => undefined
        : () => {
            setText(
              "#pair-status",
              "接続の応答を確認できません。自動では再発行しません。PCで上のコードを照合し、発行済みなら対象の認証を失効してから新しい接続を開始してください。",
            );
          })();
    }
  };
  document.querySelector("#pair-start")?.addEventListener(
    "click",
    () => {
      void begin();
    },
    { signal: lifetime },
  );
};
