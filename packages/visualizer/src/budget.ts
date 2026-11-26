/**
 * 描画の負荷ノブ。#479 の表現要素を削らずに、下限の端末で予算へ戻すための調整だけを持つ。
 * - maxDevicePixelRatio: DPR の上限
 * - renderScale: 内部の描画解像度の倍率。低く描いて canvas の表示寸法へ拡大する
 * - effectDensity: effect の強さの倍率（0 で effect を描かない）
 * - targetFps: 描画の上限 fps。これより速い rAF は描かずに見送る
 */
export type RendererBudget = Readonly<{
  maxDevicePixelRatio: number;
  renderScale: number;
  effectDensity: number;
  targetFps: number;
}>;

/** 既定値。起動直後の上位端末で余裕がある値ではなく、現行の見え方を変えない値にする。 */
export const defaultBudget: RendererBudget = Object.freeze({
  maxDevicePixelRatio: 2,
  renderScale: 1,
  effectDensity: 1,
  targetFps: 60,
});

const ranges: Readonly<
  Record<keyof RendererBudget, readonly [min: number, max: number]>
> = {
  maxDevicePixelRatio: [0.5, 3],
  renderScale: [0.25, 1],
  effectDensity: [0, 1],
  targetFps: [10, 120],
};
const keys: Readonly<Record<string, keyof RendererBudget>> = {
  dpr: "maxDevicePixelRatio",
  scale: "renderScale",
  effects: "effectDensity",
  fps: "targetFps",
};
const clamp = (key: keyof RendererBudget, value: number): number => {
  const [min, max] = ranges[key];
  return Math.min(max, Math.max(min, value));
};

/**
 * `#probe&dpr=1.5&scale=0.5&effects=0.5&fps=30` のような URL の hash から予算を読む。
 * 実機で調整するための入口で、query ではなく hash を使う（Service Worker のナビゲーションは query 付きを扱わない）。
 * 知らない項目・数でない値は無視し、範囲外は範囲へ収める。
 */
export const parseBudget = (hash: string): RendererBudget =>
  Object.freeze(
    hash
      .replace(/^#/, "")
      .split("&")
      .map((part) => part.split("="))
      .reduce<RendererBudget>((budget, [name = "", raw = ""]) => {
        const key = keys[name];
        const value = Number(raw);
        const usable = [
          key !== undefined,
          raw !== "",
          Number.isFinite(value),
        ].every(Boolean);
        return usable && key !== undefined
          ? { ...budget, [key]: clamp(key, value) }
          : budget;
      }, defaultBudget),
  );

/** 観測を表示するか。`#probe` を含む hash のときだけ表示する。 */
export const probeRequested = (hash: string): boolean =>
  hash.replace(/^#/, "").split("&").includes("probe");

/** 同じPWAで全画面の代表負荷を測る明示指定。完成表現の合格を意味しない。 */
export const sceneLoadRequested = (hash: string): boolean =>
  probeRequested(hash) &&
  hash.replace(/^#/, "").split("&").includes("load=scene");

/** canvas の表示寸法と DPR から、予算に従った描画寸法を求める。device の上限を超えない。 */
export const drawSize = (
  clientWidth: number,
  clientHeight: number,
  devicePixelRatio: number,
  budget: RendererBudget,
  maxDimension: number,
): Readonly<{ width: number; height: number }> => {
  const ratio =
    Math.min(devicePixelRatio, budget.maxDevicePixelRatio) * budget.renderScale;
  const fit = (length: number): number =>
    Math.max(1, Math.min(maxDimension, Math.floor(length * ratio)));
  return { width: fit(clientWidth), height: fit(clientHeight) };
};

/* A small tolerance keeps a 60 Hz display from dropping every other frame at a 60 fps target. */
const tolerance = 0.9;
/** 前回描いてからの経過が目標 fps の間隔に届いたか。 */
export const due = (
  now: number,
  last: number | undefined,
  budget: RendererBudget,
): boolean =>
  last === undefined
    ? true
    : now - last >= (1000 / budget.targetFps) * tolerance;
