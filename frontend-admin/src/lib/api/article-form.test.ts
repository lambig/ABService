import { describe, expect, it } from 'vitest';
import { EMPTY_DRAFT, articleFieldsOf, draftOf, withValue } from './article-form';
import type { AdminArticleDetail } from './client';

const detail = {
  articleType: 'ALBUM',
  articleId: 'article-1',
  revision: 4,
  title: '作品紹介',
  body: '## 見出し\n\n本文',
  bodyFormat: 'MARKDOWN',
  introShort: 'ショート紹介文',
  publishedAt: null,
  updatedAtBusiness: null,
  publicFlag: false,
  albumId: 'album-1',
  formerAlbumId: null,
  albumReferenceLostAt: null,
  albumReferenceLostReason: null,
  tags: [],
} satisfies AdminArticleDetail;

describe('編集の入力値', () => {
  it('照会の項目を、要求の位置へそのまま写す', () => {
    const draft = draftOf(detail);

    expect(draft.articleType).toBe('ALBUM');
    expect(draft.title).toBe('作品紹介');
    expect(draft.bodyFormat).toBe('MARKDOWN');
  });

  it('1つの欄だけを差し替える', () => {
    const draft = withValue(draftOf(detail), 'title', '改題');

    expect(draft.title).toBe('改題');
    expect(draft.introShort).toBe('ショート紹介文');
  });
});

describe('要求への写し取り', () => {
  it('空文字の項目は送らない（未指定として扱わせる）', () => {
    const fields = articleFieldsOf(withValue(draftOf(detail), 'introShort', ''));

    expect(fields.introShort).toBeUndefined();
    expect(fields.title).toBe('作品紹介');
  });

  it('空白だけの項目は送らない', () => {
    const fields = articleFieldsOf(withValue(draftOf(detail), 'title', '   '));

    expect(fields.title).toBeUndefined();
  });

  it('入力された値は加工せず送る（前後の空白も保つ）', () => {
    const fields = articleFieldsOf(withValue(EMPTY_DRAFT, 'body', '  本文  '));

    expect(fields.body).toBe('  本文  ');
  });

  it('触っていない項目は、読み込んだ値のまま送り返す（全項目置換で書き換えない）', () => {
    const fields = articleFieldsOf(withValue(draftOf(detail), 'title', '改題'));

    expect(fields.title).toBe('改題');
    expect(fields.body).toBe('## 見出し\n\n本文');
    expect(fields.bodyFormat).toBe('MARKDOWN');
  });

  it('新規作成の初期値は、選択肢を持つ欄だけが埋まっている', () => {
    const fields = articleFieldsOf(EMPTY_DRAFT);

    expect(fields.articleType).toBe('NOTE');
    expect(fields.bodyFormat).toBe('PLAIN_TEXT');
    expect(fields.title).toBeUndefined();
    expect(fields.body).toBeUndefined();
    expect(fields.introShort).toBeUndefined();
  });
});
