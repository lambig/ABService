import { describe, expect, it } from 'vitest';
import { availablePageOf, isFirstPage, isLastPage, rangeOf } from './article-list';
import type { AdminArticlePage } from './client';

/** 1ページ50件で、総件数から組み立てた応答 */
const pageOf = (page: number, totalElements: number): AdminArticlePage => ({
  items: [],
  page,
  size: 50,
  totalElements,
  totalPages: Math.ceil(totalElements / 50),
});

describe('読めるページ', () => {
  it('範囲に収まっているページはそのまま', () => {
    expect(availablePageOf(pageOf(1, 51))).toBe(1);
  });

  it('最後の1件が消えて範囲の外に出たページは、1つ手前へ戻す', () => {
    expect(availablePageOf(pageOf(1, 50))).toBe(0);
  });

  it('1件も無いときは先頭', () => {
    expect(availablePageOf(pageOf(0, 0))).toBe(0);
  });
});

describe('出している範囲', () => {
  it('先頭のページ', () => {
    expect(rangeOf(pageOf(0, 51))).toEqual({ first: 1, last: 50 });
  });

  it('最後のページは総件数で止まる', () => {
    expect(rangeOf(pageOf(1, 51))).toEqual({ first: 51, last: 51 });
  });

  it('1件だけ', () => {
    expect(rangeOf(pageOf(0, 1))).toEqual({ first: 1, last: 1 });
  });
});

describe('ページ送りの端', () => {
  it('50件は1ページに収まり、どちらへも送れない', () => {
    expect(isFirstPage(pageOf(0, 50))).toBe(true);
    expect(isLastPage(pageOf(0, 50))).toBe(true);
  });

  it('51件目は次のページにある', () => {
    expect(isLastPage(pageOf(0, 51))).toBe(false);
  });

  it('最後のページからは戻れるだけ', () => {
    expect(isFirstPage(pageOf(1, 51))).toBe(false);
    expect(isLastPage(pageOf(1, 51))).toBe(true);
  });

  it('1件も無いときはどちらへも送れない', () => {
    expect(isFirstPage(pageOf(0, 0))).toBe(true);
    expect(isLastPage(pageOf(0, 0))).toBe(true);
  });
});
