/* eslint-disable functional/immutable-data -- File selection and feedback belong to the authenticated management UI. */
import { analysisByteLimit } from "abservice-installation";
import type { InstallationManifest } from "abservice-installation";
import { createAnalysisStore } from "abservice-offline-storage";
import { element } from "./dom";
import { storageMessage } from "./preparation";

export const analysisImport = (
  manifest: InstallationManifest,
  refresh: () => Promise<void>,
): void => {
  const input = element("#musical-analysis-file", HTMLInputElement);
  const message = element("#musical-analysis-import-status", HTMLElement);
  const panel = element("#management-panel", HTMLElement);
  const lifetime = new AbortController();
  const store = createAnalysisStore(manifest);
  input.disabled = false;
  window.addEventListener(
    "pagehide",
    () => {
      lifetime.abort();
    },
    { once: true },
  );
  input.addEventListener("change", () => {
    const file = input.files?.[0];
    const run = async (): Promise<void> => {
      input.disabled = true;
      message.textContent = "音源との対応を確認して保存しています。";
      try {
        const selected =
          file ??
          (() => {
            throw new Error("解析ファイルを選んでください。");
          })();
        await (selected.size > analysisByteLimit
          ? Promise.reject(
              new Error("解析ファイルが大きすぎます（上限16 MiB）。"),
            )
          : Promise.resolve());
        const saved = await store.save(selected, lifetime.signal);
        await (saved.kind === "error"
          ? Promise.reject(
              new Error(
                saved.error === "unknown-asset"
                  ? "保存済みの音源に対応する解析ファイルを選んでください。"
                  : storageMessage(saved.error),
              ),
            )
          : Promise.resolve());
        await refresh();
        message.textContent = "主旋律・和音の解析を保存しました。";
      } catch (error) {
        message.textContent =
          error instanceof Error
            ? error.message
            : "解析を保存できませんでした。";
      } finally {
        input.value = "";
        input.disabled = false;
      }
    };
    void ([panel.hidden, input.disabled, file === undefined].some(Boolean)
      ? Promise.resolve()
      : run());
  });
};
