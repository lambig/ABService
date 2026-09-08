import { describe, expect, it } from 'vitest';
import { formErrorsOf, hasAssignedErrors } from './form-errors';

/** この画面が欄を持つ位置。入れ子（`event.name`）を含む */
const PATHS = ['title', 'catalogNumber', 'event.name'];

const problemOf = (errors: readonly { field: string; message: string }[]) => ({
  type: 'urn:abservice:error:VALIDATION_ERROR',
  status: 400,
  errors,
});

describe('検証エラーの欄への割り当て', () => {
  it('field が欄の位置と一致するものを、その欄へ割り当てる', () => {
    const errors = formErrorsOf(
      problemOf([
        { field: 'title', message: 'タイトルは必須です' },
        { field: 'event.name', message: 'イベント名は必須です' },
      ]),
      PATHS,
    );

    expect(errors.byField.get('title')).toEqual(['タイトルは必須です']);
    expect(errors.byField.get('event.name')).toEqual(['イベント名は必須です']);
    expect(errors.unassigned).toEqual([]);
  });

  it('同じ位置に複数返っても、どちらも落とさない', () => {
    const errors = formErrorsOf(
      problemOf([
        { field: 'catalogNumber', message: '形式が不正です' },
        { field: 'catalogNumber', message: '長すぎます' },
      ]),
      PATHS,
    );

    expect(errors.byField.get('catalogNumber')).toEqual(['形式が不正です', '長すぎます']);
  });

  it('field が空のものは、どの欄へも寄せない', () => {
    const errors = formErrorsOf(
      problemOf([{ field: '', message: '操作を受け付けられません' }]),
      PATHS,
    );

    expect(errors.byField.size).toBe(0);
    expect(errors.unassigned).toEqual(['操作を受け付けられません']);
  });

  it('欄が無い位置のエラーは捨てず、位置を添えて全体へ出す', () => {
    const errors = formErrorsOf(
      problemOf([{ field: 'tracks[0].title', message: 'トラックタイトルは必須です' }]),
      PATHS,
    );

    expect(errors.byField.size).toBe(0);
    expect(errors.unassigned).toEqual(['tracks[0].title: トラックタイトルは必須です']);
  });

  it('Problem Details を持たない失敗では、エラーを作らない', () => {
    const errors = formErrorsOf(undefined, PATHS);

    expect(errors.byField.size).toBe(0);
    expect(errors.unassigned).toEqual([]);
    expect(hasAssignedErrors(errors)).toBe(false);
  });

  it('欄へ割り当てたものがあるかを、全体のエラーと区別して答える', () => {
    expect(
      hasAssignedErrors(formErrorsOf(problemOf([{ field: 'title', message: '必須' }]), PATHS)),
    ).toBe(true);
    expect(
      hasAssignedErrors(formErrorsOf(problemOf([{ field: '', message: '不可' }]), PATHS)),
    ).toBe(false);
  });
});
