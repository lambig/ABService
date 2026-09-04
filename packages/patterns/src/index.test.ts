import { describe, expect, it, vi } from 'vitest';

import { exhaustive, patterns } from './index';

type Status = 'pending' | 'approved' | 'rejected';

const message = (status: Status): string =>
  patterns<Status, string>(status)
    .when('pending', () => '承認待ち')
    .when('approved', () => '承認済み')
    .when('rejected', () => '却下')
    .orElse(exhaustive);

describe('patterns', () => {
  it('当たった候補の値を返す', () => {
    expect(message('pending')).toBe('承認待ち');
    expect(message('approved')).toBe('承認済み');
    expect(message('rejected')).toBe('却下');
  });

  it('どの候補にも当たらなければ orElse の値を返す', () => {
    const result = patterns<string, string>('unknown')
      .when('a', () => 'A')
      .orElse((rest) => `fallback: ${rest}`);

    expect(result).toBe('fallback: unknown');
  });

  it('同じ候補が複数あるときは先に書いたものを採る', () => {
    const result = patterns<string, string>('a')
      .when('a', () => 'first')
      .when('a', () => 'second')
      .orElse(() => 'none');

    expect(result).toBe('first');
  });

  it('当たらなかった候補の値は評価しない', () => {
    const notTaken = vi.fn(() => 'not taken');

    const result = patterns<string, string>('a')
      .when('a', () => 'taken')
      .when('b', notTaken)
      .orElse(() => 'none');

    expect(result).toBe('taken');
    expect(notTaken).not.toHaveBeenCalled();
  });

  it('当たった候補があれば orElse は評価しない', () => {
    const fallback = vi.fn(() => 'fallback');

    const result = patterns<string, string>('a')
      .when('a', () => 'taken')
      .orElse(fallback);

    expect(result).toBe('taken');
    expect(fallback).not.toHaveBeenCalled();
  });
});

describe('exhaustive', () => {
  it('型を欺いて呼ばれたら投げる', () => {
    /*
     * 到達しないことは型が保証する。ここでは保証を外して、万一到達したときに黙って undefined を
     * 返さないことだけを確かめる。
     */
    const unreachable = 'unexpected' as never;

    expect(() => exhaustive(unreachable)).toThrow('unhandled pattern: unexpected');
  });
});
