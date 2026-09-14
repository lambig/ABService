import type { Locator } from '@playwright/test';

/**
 * 属性の値。持っていなければ落とす。
 *
 * <p>
 * `null` をそのまま比較へ渡すと、**属性が無いのか値が違うのか**が失敗の出力から読めない。値どうしを
 * 突き合わせる検査（同じ画像を2箇所が指しているか等）の前段で使う。
 * </p>
 *
 * @param locator 対象の要素
 * @param name 属性の名前
 */
export const attributeOf = async (locator: Locator, name: string): Promise<string> => {
  const value = await locator.getAttribute(name);

  return value === null ? Promise.reject(new Error(`${name} を持っていません`)) : value;
};
