import type { FieldError, ProblemDetails } from './http';

/**
 * 検証エラーを、入力欄へ割り当てられるものと割り当てられないものへ分けた形。
 *
 * <p>
 * 鍵は応答が返した `field` をそのまま使う（`event.name` のような入れ子、`tracks[0].title` のような
 * 配列添字も綴りのまま）。画面が code から位置を導く対応表を持たないため、検証を1つ足すたびに画面を
 * 変える必要が生まれない（DECISIONS 29）。
 * </p>
 */
export type FormErrors = Readonly<{
  /** 欄ごとのエラー文言。同じ位置に複数のエラーが返ることがあるため配列で持つ */
  byField: ReadonlyMap<string, readonly string[]>;
  /**
   * どの欄にも出せないエラー。
   *
   * <p>
   * 応答が `field` を空で返したもの（要求全体に関わるエラー）と、画面に欄が無い位置のものが入る。
   * 後者を捨てると、送った内容が拒まれた理由が画面のどこにも出ない状態になる。
   * </p>
   */
  unassigned: readonly string[];
}>;

/** エラーが1つも無い状態 */
export const NO_ERRORS: FormErrors = { byField: new Map(), unassigned: [] };

const messagesOf = (errors: readonly FieldError[], field: string): readonly string[] =>
  errors.filter((error) => error.field === field).map((error) => error.message);

const groupedByField = (errors: readonly FieldError[]): ReadonlyMap<string, readonly string[]> =>
  new Map(
    [...new Set(errors.map((error) => error.field))].map((field) => [
      field,
      messagesOf(errors, field),
    ]),
  );

/**
 * 欄に出せないエラーの文言。
 *
 * <p>
 * 空の `field` は要求全体に関わるエラーで、位置を足すと「どの欄でもない」ことが「欄が分からない」に
 * すり替わる。欄が無い位置のものは、どこの話かを読めるように位置を添える。
 * </p>
 */
const unassignedTextOf = (error: FieldError): string =>
  error.field === '' ? error.message : `${error.field}: ${error.message}`;

const assignable = (paths: readonly string[]) => (error: FieldError) => paths.includes(error.field);

/** {@link assignable} の補集合。否定（`!`）を使わないため、条件を肯定形で書く（CODING_GUIDELINES §9） */
const unassignable = (paths: readonly string[]) => (error: FieldError) =>
  paths.every((path) => path !== error.field);

const dividedErrors = (errors: readonly FieldError[], paths: readonly string[]): FormErrors => ({
  byField: groupedByField(errors.filter(assignable(paths))),
  unassigned: errors.filter(unassignable(paths)).map(unassignedTextOf),
});

/**
 * Problem Details の検証エラーを、欄へ割り当てた形にする。
 *
 * @param problem 応答が返した Problem Details（無い場合は undefined）
 * @param paths この画面が欄を持つ位置。ここに無い位置は割り当てず、位置つきで全体へ出す
 */
export const formErrorsOf = (
  problem: ProblemDetails | undefined,
  paths: readonly string[],
): FormErrors => dividedErrors(problem?.errors ?? [], paths);

/** 欄に出せるエラーが1つでもあるか。無いときは全体の失敗として扱う */
export const hasAssignedErrors = (errors: FormErrors): boolean => errors.byField.size > 0;

/**
 * 位置が指す先が変わった欄のエラーを落とす。
 *
 * <p>
 * 配列の行を入れ替える・取り除くと、同じ位置（`externalAudios[1].url`）は別の行を指す。残したままに
 * すると、直っていない行から消えて関係のない行に出る。行がまだ識別子を持たない（保存されていない）
 * 間は、エラーを行へ追従させられないため、意味を失った側を落とす。
 * </p>
 *
 * <p>
 * <b>落とすのは接頭辞に一致するものだけ。</b> 1回の応答には本体の欄の誤り（`title`）も一緒に入る。
 * まとめて捨てると、何も直していない欄のエラーまで画面から消える。
 * </p>
 *
 * @param errors
 *            いま出ているエラー
 * @param prefix
 *            落とす位置の接頭辞（例: `externalAudios[`）
 * @returns 接頭辞に一致する位置を除いたエラー
 */
export const withoutPathsUnder = (errors: FormErrors, prefix: string): FormErrors => ({
  byField: new Map([...errors.byField].filter(([field]) => outside(prefix)(field))),
  unassigned: errors.unassigned.filter(outside(prefix)),
});

/** 接頭辞の外にある位置。否定（`!`）を使わないため、綴りの比較で表す（CODING_GUIDELINES §9） */
const outside =
  (prefix: string) =>
  (path: string): boolean =>
    path.slice(0, prefix.length) !== prefix;
