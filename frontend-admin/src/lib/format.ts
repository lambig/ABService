/** 日付の表示形式。暦日をそのまま出すため、閲覧者の時間帯で日がずれないよう UTC で解釈する */
const DATE_FORMAT = new Intl.DateTimeFormat('ja-JP', {
  dateStyle: 'medium',
  timeZone: 'UTC',
});

/** 暦日（`YYYY-MM-DD`）を表示用へ整形する。 */
export const formatCalendarDate = (isoDate: string): string =>
  DATE_FORMAT.format(new Date(isoDate));
