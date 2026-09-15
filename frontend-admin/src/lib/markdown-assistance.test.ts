import { describe, expect, it } from 'vitest';
import { MARKDOWN_ASSISTANCE } from './markdown-assistance';

describe('Markdown入力支援', () => {
  it.each([
    ['bold', '前**日本語🎵**後', 3, 8],
    ['italic', '前*日本語🎵*後', 2, 7],
    ['code', '前`日本語🎵`後', 2, 7],
  ])('選択範囲だけに記法を付け、選択した文字を保持する: %s', (id, value, start, end) => {
    expect(
      MARKDOWN_ASSISTANCE.find((source) => source.id === id)?.suggest({
        value: '前日本語🎵後',
        start: 1,
        end: 6,
      }),
    ).toEqual({ value, start, end });
  });

  it('未選択ならカーソル位置に仮の文字を挿入し、置換入力できるよう選ぶ', () => {
    expect(MARKDOWN_ASSISTANCE[0]?.suggest({ value: '前後', start: 1, end: 1 })).toEqual({
      value: '前**文字**後',
      start: 3,
      end: 5,
    });
  });

  it('選択に含まれるバッククォートでコードを閉じない', () => {
    expect(MARKDOWN_ASSISTANCE[2]?.suggest({ value: '前a``b後', start: 1, end: 5 })).toEqual({
      value: '前``` a``b ```後',
      start: 5,
      end: 9,
    });
  });

  it('選択前後の改行や編集中の記法を変更しない', () => {
    expect(
      MARKDOWN_ASSISTANCE[0]?.suggest({ value: '# 未完\n対象\n[途中', start: 5, end: 7 }),
    ).toEqual({
      value: '# 未完\n**対象**\n[途中',
      start: 7,
      end: 9,
    });
  });
});
