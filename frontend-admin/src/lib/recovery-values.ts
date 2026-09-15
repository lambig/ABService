import { ALBUM_FIELD_PATHS, type AlbumDraft, type ExternalAudioDraft } from './api/album-form';
import { ARTICLE_FIELD_PATHS } from './api/article-form';
import type { TrackDraft, TuneDraft } from './api/track-form';
import { objectOf, stringsOf } from './editor-recovery';

/** 作品のフォーム入力。アップロード中のFile・署名付きURL・セッションは保存しない。 */
export type AlbumRecoveryValues = Readonly<{
  draft: AlbumDraft;
  coverImageUrl: string | null;
  audios: readonly ExternalAudioDraft[];
  tracks: readonly TrackDraft[];
}>;
/** 記事のタグ・作品参照は別の確定済み操作なので復旧入力へ混ぜない。 */
export const articleRecoveryValues = (value: unknown) => stringsOf(ARTICLE_FIELD_PATHS, value);

const arrayOf = <T>(value: unknown, decode: (entry: unknown) => T | null): readonly T[] | null => {
  const entries = Array.isArray(value) ? (value as readonly unknown[]).map(decode) : null;
  return entries?.every((entry) => entry !== null) === true ? entries : null;
};
const tuneOf = (value: unknown): TuneDraft | null =>
  stringsOf(['tuneTitle', 'composerCreditOverride', 'arrangerCreditOverride', 'linkUrl'], value);
const nullableString = (value: unknown): value is string | null =>
  [value === null, typeof value === 'string'].some(Boolean);
const trackOf = (value: unknown): TrackDraft | null => {
  const record = objectOf(value);
  const fields = stringsOf(['title', 'artistDisplayName', 'artistSortKey'], value);
  const tunes = arrayOf(record?.tunes, tuneOf);
  const trackId = record?.trackId;
  return fields !== null && tunes !== null && nullableString(trackId)
    ? { ...fields, trackId, tunes }
    : null;
};
const audioOf = (value: unknown): ExternalAudioDraft | null => {
  const record = objectOf(value);
  const fields = stringsOf(['url'], value);
  const externalAudioId = record?.externalAudioId;
  return fields !== null && nullableString(externalAudioId) ? { ...fields, externalAudioId } : null;
};
/** スキーマ検証後に既知の欄だけを投影し、未知の保存値をフォームへ持ち込まない。 */
export const albumRecoveryValues = (value: unknown): AlbumRecoveryValues | null => {
  const record = objectOf(value);
  const draft = stringsOf(ALBUM_FIELD_PATHS, record?.draft);
  const coverImageUrl = record?.coverImageUrl;
  const audios = arrayOf(record?.audios, audioOf);
  const tracks = arrayOf(record?.tracks, trackOf);
  return draft !== null && audios !== null && tracks !== null && nullableString(coverImageUrl)
    ? { draft, audios, tracks, coverImageUrl }
    : null;
};
