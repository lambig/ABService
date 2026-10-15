/* eslint-disable functional/immutable-data -- This boundary owns the saved output profiles and their management controls. */
import { element } from "./dom";

type Profile = "speaker" | "headphones";
type Settings = Readonly<{
  selected: Profile;
  speaker: number;
  headphones: number;
}>;
const key = "exhibition-synchronization-v1";
const validDelay = (value: unknown): value is number =>
  typeof value === "number" &&
  Number.isInteger(value) &&
  value >= -250 &&
  value <= 250 &&
  value % 10 === 0;
const parse = (value: unknown): Settings => {
  const data = value as Partial<Settings> | null;
  return data !== null &&
    typeof data === "object" &&
    ["speaker", "headphones"].includes(data.selected ?? "") &&
    validDelay(data.speaker) &&
    validDelay(data.headphones)
    ? (data as Settings)
    : { selected: "speaker", speaker: 0, headphones: 0 };
};

export const synchronization = (panel: HTMLElement): (() => number) => {
  const form = element("#synchronization-form", HTMLFormElement);
  const output = element("#synchronization-output", HTMLSelectElement);
  const delay = element("#synchronization-delay", HTMLInputElement);
  const status = element("#synchronization-status", HTMLElement);
  const state = { settings: parse(null) };
  const selected = (): Profile =>
    output.value === "headphones" ? "headphones" : "speaker";
  const show = (): void => {
    const current = state.settings;
    output.value = current.selected;
    delay.value = String(current[current.selected]);
    const label =
      current.selected === "speaker" ? "本体スピーカー用" : "ヘッドホン用";
    status.textContent = `${label}を適用中（${String(current[current.selected])}ms）。`;
  };
  try {
    state.settings = parse(
      JSON.parse(localStorage.getItem(key) ?? "null") as unknown,
    );
    show();
  } catch {
    show();
    status.textContent =
      "保存状態を確認できないため、自動補正のみで開始します。";
  }
  output.addEventListener("change", () => {
    delay.value = String(state.settings[selected()]);
  });
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const value = delay.valueAsNumber;
    ([panel.hidden, validDelay(value) ? false : true].some(Boolean)
      ? () => undefined
      : () => {
          const profile = selected();
          const next = {
            ...state.settings,
            selected: profile,
            [profile]: value,
          };
          try {
            localStorage.setItem(key, JSON.stringify(next));
            state.settings = next;
            show();
          } catch {
            status.textContent =
              "保存できませんでした。適用中の同期設定は変更していません。";
          }
        })();
  });
  return () => state.settings[state.settings.selected] / 1000;
};
