/**
 * 記事の語彙の表示。
 *
 * <p>
 * 値は管理APIの列挙子名で、そのままでは画面に出せない。一覧と編集の双方が同じ語を出すため、写しを
 * 2つ持たずここへ置く。
 * </p>
 */

/** 記事種別の表示。管理APIが返す列挙子名を鍵にする */
export const ARTICLE_TYPE_LABELS: Readonly<Record<string, string>> = {
  ALBUM: '作品紹介',
  NOTE: '記事',
  NEWS: 'ニュース',
  EVENT: 'イベント',
  OTHER: 'その他',
};
