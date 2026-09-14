import { describe, expect, it } from 'vitest';
import {
  EMPTY_TRACK,
  EMPTY_TUNE,
  trackPathsOf,
  trackTitleOf,
  tunePathOf,
  tuneSummaryOf,
} from './track-form';

/**
 * 曲目の入力の読み方（#122 / #391）。
 *
 * <p>
 * 見るのは<b>畳んだ行の出し方</b>と<b>誤りを割り当てられる位置</b>である。前者を落とすと、開くまで何が
 * 入っているのか分からない一覧になる。後者を落とすと、行の誤りが行から離れて全体のエラーへ回る。
 * </p>
 */

describe('畳んだチューンの行', () => {
  it('曲名だけの行は、曲名をそのまま出す', () => {
    expect(tuneSummaryOf({ ...EMPTY_TUNE, tuneTitle: '前半' })).toBe('前半');
  });

  it('クレジットは括弧に入れて添える', () => {
    expect(
      tuneSummaryOf({
        ...EMPTY_TUNE,
        tuneTitle: '前半',
        composerCreditOverride: '作曲者',
        arrangerCreditOverride: '編曲者',
      }),
    ).toBe('前半（作曲: 作曲者 / 編曲: 編曲者）');
  });

  /*
   * KEEP-EMPTY-ROWS: 公開は読めない行を落とすが、編集では落とさない。落とすと、入っているものが画面
   * から消えて触れなくなる。
   */
  it('何も入っていない行も、行として読める形で出す', () => {
    expect(tuneSummaryOf(EMPTY_TUNE)).toBe('（空の行）');
  });

  /* 読み込んだものは `null` で、入力は空文字で持たない状態を表す。どちらも同じ読み方になる */
  it('読み込んだ形（null）でも、入力の形（空文字）でも同じに読む', () => {
    expect(
      tuneSummaryOf({
        tuneTitle: '前半',
        composerCreditOverride: null,
        arrangerCreditOverride: null,
      }),
    ).toBe(tuneSummaryOf({ ...EMPTY_TUNE, tuneTitle: '前半' }));
  });
});

describe('畳んだトラックの行', () => {
  it('名を持つトラックは、その名を出す', () => {
    expect(trackTitleOf({ ...EMPTY_TRACK, title: '組曲' })).toBe('組曲');
  });

  /*
   * NO-TITLE-COMPOSITION-HERE: 名の組み方（区切り）はバックエンドの設定が持つ（#360）。画面が繋ぐと、
   * 区切りを変えたときに2箇所を直すことになる。
   */
  it('名を省いたトラックは、省かれていることだけを示す', () => {
    expect(trackTitleOf(EMPTY_TRACK)).toBe('（チューン名から組まれます）');
  });
});

describe('誤りを割り当てられる位置', () => {
  const withTunes = { ...EMPTY_TRACK, tunes: [EMPTY_TUNE, EMPTY_TUNE] };

  it('行の数だけ添字が増える', () => {
    const paths = trackPathsOf([withTunes]);

    expect(paths).toContain('tracks[0].title');
    expect(paths).toContain(tunePathOf(0, 1, 'linkUrl'));
    expect(paths).not.toContain(tunePathOf(0, 2, 'linkUrl'));
  });

  it('トラックの位置は、作品の中の並びから決まる', () => {
    const paths = trackPathsOf([EMPTY_TRACK, withTunes]);

    expect(paths).toContain(tunePathOf(1, 0, 'tuneTitle'));
    expect(paths).not.toContain(tunePathOf(0, 0, 'tuneTitle'));
  });
});
