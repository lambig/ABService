/* eslint-disable functional/immutable-data -- Fullscreen controls reflect browser events, including browser-initiated exit. */
import { element } from "./dom";
export const fullscreen = (): void => {
  const button = element("#fullscreen", HTMLButtonElement);
  const status = element("#display-status", HTMLElement);
  const update = () => {
    button.textContent =
      document.fullscreenElement === null ? "全画面" : "全画面を解除";
    button.setAttribute(
      "aria-pressed",
      String(document.fullscreenElement !== null),
    );
  };
  button.disabled = document.fullscreenEnabled ? false : true;
  button.addEventListener("click", () => {
    status.textContent = "";
    void (
      document.fullscreenElement === null
        ? document.documentElement.requestFullscreen({ navigationUI: "hide" })
        : document.exitFullscreen()
    ).catch(() => {
      status.textContent =
        "全画面に切り替えられませんでした。もう一度お試しください。";
    });
  });
  document.addEventListener("fullscreenchange", update);
  update();
};
