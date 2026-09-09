import { describe, expect, it } from 'vitest';
import { EMPTY_DRAFT, albumFieldsOf, draftOf, withValue } from './album-form';
import type { AdminAlbumDetail } from './client';

const detail = {
  albumId: 'album-1',
  revision: 3,
  title: 'アルバム',
  releaseDate: '2026-01-01',
  artistDisplayName: 'アーティスト',
  artistSortKey: null,
  description: null,
  descriptionFormat: 'MARKDOWN',
  catalogNumber: 'AB-001',
  isdn: null,
  eventName: 'イベント',
  eventDate: '2026-02-01',
  eventPlace: null,
  eventSpaceNumber: null,
  eventNote: null,
  publishedAt: null,
  coverImageKey: 'covers/album-1.png',
  coverImageUrl: 'https://example.com/covers/album-1.png',
  externalAudios: [],
  tracks: [],
} satisfies AdminAlbumDetail;

describe('編集の入力値', () => {
  it('照会の平らな項目を、要求の入れ子の位置へ写す', () => {
    const draft = draftOf(detail);

    expect(draft['event.name']).toBe('イベント');
    expect(draft['event.date']).toBe('2026-02-01');
    expect(draft['event.place']).toBe('');
  });

  it('未設定は空文字で持ち、null と undefined を混ぜない', () => {
    const draft = draftOf(detail);

    expect(draft.artistSortKey).toBe('');
    expect(draft.description).toBe('');
  });

  it('1つの欄だけを差し替える', () => {
    const draft = withValue(draftOf(detail), 'title', '改題');

    expect(draft.title).toBe('改題');
    expect(draft.catalogNumber).toBe('AB-001');
  });
});

describe('要求への写し取り', () => {
  it('空文字の項目は送らない（未指定として扱わせる）', () => {
    const fields = albumFieldsOf(withValue(draftOf(detail), 'catalogNumber', ''));

    expect(fields.catalogNumber).toBeUndefined();
    expect(fields.title).toBe('アルバム');
  });

  it('空白だけの項目は送らない', () => {
    const fields = albumFieldsOf(withValue(draftOf(detail), 'title', '   '));

    expect(fields.title).toBeUndefined();
  });

  it('入力された値は加工せず送る（前後の空白も保つ）', () => {
    const fields = albumFieldsOf(withValue(EMPTY_DRAFT, 'description', '  本文  '));

    expect(fields.description).toBe('  本文  ');
  });

  it('触っていない項目は、読み込んだ値のまま送り返す（全項目置換で書き換えない）', () => {
    const withSpaces = withValue(draftOf(detail), 'description', ' # 見出し\n\n本文 ');
    const fields = albumFieldsOf(withValue(withSpaces, 'title', '改題'));

    expect(fields.title).toBe('改題');
    expect(fields.description).toBe(' # 見出し\n\n本文 ');
  });

  it('カバー画像の鍵は欄を持たないが、読み込んだ値を送り返す', () => {
    const fields = albumFieldsOf(draftOf(detail));

    expect(fields.coverImageKey).toBe('covers/album-1.png');
  });

  it('イベントの項目がどれも空なら、イベントを送らない', () => {
    const fields = albumFieldsOf(EMPTY_DRAFT);

    expect(fields.event).toBeUndefined();
  });

  it('イベントの項目が1つでも入力されていれば、入れ子を送る（必須の判定はしない）', () => {
    const fields = albumFieldsOf(withValue(EMPTY_DRAFT, 'event.place', '会場'));

    expect(fields.event).toEqual({
      name: undefined,
      date: undefined,
      place: '会場',
      spaceNumber: undefined,
      note: undefined,
    });
  });
});
