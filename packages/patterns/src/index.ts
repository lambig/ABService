/**
 * 網羅性を保ったまま、分岐を式で書くための道具。
 *
 * <p>
 * `switch` 文の代わりに使う。`switch` を禁じたうえで網羅性を失わないために、候補を1つ当てるたびに
 * 残りの型から取り除き、すべて当て終えたときだけ {@link exhaustive} を渡せる形にしている。
 * </p>
 *
 * ```ts
 * const message = (status: Status): string =>
 *   patterns(status)
 *     .when('pending', () => '承認待ち')
 *     .when('approved', () => '承認済み')
 *     .when('rejected', () => '却下')
 *     .orElse(exhaustive);
 * ```
 *
 * 候補を1つ落とすと、落とした候補名を示してコンパイルが落ちる。
 */

/** 値を遅延して返すもの。当たった候補の値だけを評価するために使う */
export type Supplier<R> = () => R;

/**
 * 対象と、まだ当てていない候補の型を持つ照合。
 *
 * <p>
 * {@link Patterns.when} が返す型からは、当てた候補が {@link Exclude} で取り除かれる。すべて当て終えると
 * 残りが `never` になり、そのときだけ {@link exhaustive} を {@link Patterns.orElse} へ渡せる。
 * </p>
 *
 * <p>
 * `orElse` をメソッドではなく関数型のプロパティとして宣言している。メソッド記法では引数が双変で
 * 検査されるため、残りが `never` でなくても `exhaustive` を渡せてしまう。関数型のプロパティなら
 * `strictFunctionTypes` の下で反変に検査され、網羅性の検査が成立する。
 * </p>
 */
export type Patterns<T, R> = {
  /** 対象が `value` と等しければ `then` の値を採る。以降、この候補は残りから外れる */
  readonly when: <U extends T>(value: U, then: Supplier<R>) => Patterns<Exclude<T, U>, R>;

  /** どの候補にも当たらなかったときの値を決めて、照合を終える */
  readonly orElse: (fallback: (rest: T) => R) => R;
};

const patternsFrom = <T, R>(target: T, found: readonly Supplier<R>[]): Patterns<T, R> => ({
  /*
   * NARROWING: 値は変えず、型の見え方だけを狭める。当たった候補は found に積まれるため、ここで
   * 取り除いた候補が fallback へ渡ることはない。この言い換えはこの関数の内側だけに閉じる。
   */
  when: <U extends T>(value: U, then: Supplier<R>) =>
    patternsFrom(target, Object.is(target, value) ? [...found, then] : found) as unknown as Patterns<
      Exclude<T, U>,
      R
    >,

  orElse: (fallback) => (found[0] ?? (() => fallback(target)))(),
});

/**
 * 対象を受け取って照合を始める。
 *
 * @param target
 *            照合する値
 */
export const patterns = <T, R>(target: T): Patterns<T, R> => patternsFrom(target, []);

/**
 * すべての候補を当て終えたことを型で示す。
 *
 * <p>
 * 引数が `never` のため、残りの候補があるうちは {@link Patterns.orElse} へ渡せない。渡せた時点で
 * 到達しないことが型で保証されるが、型を欺いて呼ばれた場合に備えて投げる。
 * </p>
 */
export const exhaustive = (value: never): never => {
  throw new Error(`unhandled pattern: ${String(value)}`);
};
