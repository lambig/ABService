/** 日付の表示形式。閲覧者の環境によらず同じ文字列を出す（静的出力のため、ビルド時に確定する） */
const DATE_FORMAT = new Intl.DateTimeFormat('ja-JP', {
  dateStyle: 'long',
  timeZone: 'UTC',
});

/**
 * 暦日（`YYYY-MM-DD`）を表示用へ整形する。
 *
 * リリース日と初出イベントの開催日が対象。どちらも時刻を持たない暦日のため、閲覧者の時間帯で日が
 * ずれないよう UTC で解釈する。
 */
export const formatCalendarDate = (isoDate: string): string =>
  DATE_FORMAT.format(new Date(isoDate));

/** 公開日時の表示形式。時点を運用の時間帯で暦日へ落とす */
const PUBLISHED_DATE_FORMAT = new Intl.DateTimeFormat('ja-JP', {
  dateStyle: 'long',
  timeZone: 'Asia/Tokyo',
});

/**
 * 公開日時（ISO 8601 の日時）を表示用の日付へ整形する。
 *
 * 暦日と違い公開日時は時刻を持つ時点のため、運用の時間帯で暦日にする。UTC のまま日付にすると、
 * 夜に公開したものが前日として出る。
 */
export const formatPublishedDate = (isoDateTime: string): string =>
  PUBLISHED_DATE_FORMAT.format(new Date(isoDateTime));

/**
 * 頒布額を表示用へ整形する。
 *
 * 額は通貨の最小単位で持つ（円なら1円、ドルならセント）。主要単位への直し方は通貨ごとに違うため、
 * 桁数を持たず整形器へ聞く。書き並べると、通貨が増えるたびにここも直すことになる。
 */
export const formatPrice = (amount: number, currency: string): string => {
  const format = new Intl.NumberFormat('ja-JP', { style: 'currency', currency });

  return format.format(amount / 10 ** minorUnitDigitsOf(format));
};

/*
 * 通貨の小数桁。整形器は通貨を指定されていれば必ず持つが、型の上では省略されうる。取れないときは
 * 桁を動かさない（最小単位と主要単位が同じ通貨の扱い）。額を10倍・100倍にして出すよりは安全側。
 */
const minorUnitDigitsOf = (format: Intl.NumberFormat): number =>
  format.resolvedOptions().maximumFractionDigits ?? 0;
