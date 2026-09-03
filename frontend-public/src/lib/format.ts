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
