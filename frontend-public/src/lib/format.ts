/** 日付の表示形式。閲覧者の環境によらず同じ文字列を出す（静的出力のため、ビルド時に確定する） */
const DATE_FORMAT = new Intl.DateTimeFormat('ja-JP', {
  dateStyle: 'long',
  timeZone: 'UTC',
});

/**
 * 発表日（`YYYY-MM-DD`）を表示用へ整形する。
 *
 * 日付は暦日であって時刻を持たないため、閲覧者の時間帯で日がずれないよう UTC で解釈する。
 */
export const formatReleaseDate = (isoDate: string): string => DATE_FORMAT.format(new Date(isoDate));
