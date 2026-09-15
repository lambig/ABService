import { get } from 'svelte/store';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EMPTY_DRAFT as ARTICLE } from '$lib/api/article-form';
import { EMPTY_DRAFT as ALBUM } from '$lib/api/album-form';
import { EMPTY_TRACK, EMPTY_TUNE } from '$lib/api/track-form';
import { clearEditorRecoveries, createEditorRecovery, recoveryKey } from '$lib/editor-recovery';
import { albumRecoveryValues, articleRecoveryValues } from '$lib/recovery-values';

const identity = { editor: 'article', target: 'article-a' } as const;
const initial = { values: ARTICLE, revision: 3 };
const input = { values: { ...ARTICLE, title: '', body: ' **書きかけ\n' }, revision: 3 };
const key = recoveryKey(identity);
const open = () => createEditorRecovery(identity, initial, articleRecoveryValues);
const storeDraft = (): void => {
  const editor = open();
  editor.capture(input);
  editor.dispose();
};

beforeEach(() => {
  sessionStorage.clear();
  vi.useFakeTimers();
});
afterEach(() => {
  vi.clearAllTimers();
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe('タブ内の入力復旧', () => {
  it('入力をdebounceし、最後の不完全な文字列と世代をそのまま退避する', () => {
    const editor = open();
    editor.capture(input);
    vi.advanceTimersByTime(400);
    editor.capture({ ...input, values: { ...input.values, body: ' **続き\n' } });
    vi.advanceTimersByTime(499);
    expect(sessionStorage.getItem(key)).toBeNull();
    vi.advanceTimersByTime(1);
    const restored = open().restore();
    expect(restored?.values.body).toBe(' **続き\n');
    expect(restored?.values.title).toBe('');
    expect(restored?.revision).toBe(3);
    expect(Number.isFinite(Date.parse(restored?.savedAt ?? ''))).toBe(true);
  });

  it('debounce前の離脱で最後の入力を退避し、選択中は上書きしない', () => {
    storeDraft();
    const raw = sessionStorage.getItem(key);
    const reopened = open();
    reopened.capture(initial);
    vi.advanceTimersByTime(1000);
    reopened.dispose();
    expect(sessionStorage.getItem(key)).toBe(raw);
    expect(get(reopened).awaitingChoice).toBe(true);
  });

  it('復元は最新の世代に置き換えず、復元後も離脱に備えて保持する', () => {
    storeDraft();
    const editor = createEditorRecovery(
      identity,
      { ...initial, revision: 7 },
      articleRecoveryValues,
    );
    expect(editor.restore()).toMatchObject(input);
    editor.dispose();
    expect(open().restore()).toMatchObject(input);
  });

  it('保存成功・破棄は保留タイマーと離脱時の再作成を止める', () => {
    storeDraft();
    const editor = open();
    editor.restore();
    editor.capture(input);
    editor.clear();
    vi.advanceTimersByTime(1000);
    editor.dispose();
    expect(sessionStorage.getItem(key)).toBeNull();
  });

  it('元の入力に戻せば不要になったスナップショットを消す', () => {
    const editor = open();
    editor.capture(input);
    vi.advanceTimersByTime(500);
    editor.capture(initial);
    vi.advanceTimersByTime(500);
    expect(sessionStorage.getItem(key)).toBeNull();
  });

  it('別の対象・新規作成・別editorへ復旧内容を渡さない', () => {
    storeDraft();
    const others = [
      { editor: 'article', target: 'article-b' },
      { editor: 'article', target: null },
      { editor: 'album', target: 'article-a' },
    ] as const;
    others.forEach((other) => {
      expect(createEditorRecovery(other, initial, articleRecoveryValues).restore()).toBeNull();
    });
    expect(open().restore()).toMatchObject(input);
  });

  it.each(['{', '{"version":2}', 'null', '[]'])(
    '破損・未対応の保存値 %s は明示破棄まで保持し、入力へ適用しない',
    (raw) => {
      sessionStorage.setItem(key, raw);
      const editor = open();
      expect(get(editor).awaitingChoice).toBe(true);
      expect(get(editor).pending).toBeNull();
      expect(get(editor).error).toContain('形式');
      editor.capture(input);
      editor.flush();
      expect(sessionStorage.getItem(key)).toBe(raw);
      editor.clear();
      expect(get(editor).awaitingChoice).toBe(false);
      expect(sessionStorage.getItem(key)).toBeNull();
    },
  );

  it('payloadの対象が保存キーと違う場合も復元しない', () => {
    storeDraft();
    sessionStorage.setItem(
      key,
      (sessionStorage.getItem(key) ?? '').replace('article-a', 'article-b'),
    );
    expect(open().restore()).toBeNull();
  });

  it('保存領域の読取拒否・容量不足を入力操作から隔離する', () => {
    const rejected = (): Storage => {
      throw new Error('disabled');
    };
    const unreadable = createEditorRecovery(identity, initial, articleRecoveryValues, rejected);
    expect(get(unreadable).error).toContain('退避ができません');
    expect(() => {
      unreadable.capture(input);
      unreadable.dispose();
    }).not.toThrow();
    const editor = open();
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('quota');
    });
    editor.capture(input);
    editor.flush();
    expect(get(editor).error).toContain('退避ができません');
    expect(get(editor).savedAt).toBeNull();
  });

  it('再認証で保持していた入力には復元選択を重ねず、退避自体は残す', () => {
    storeDraft();
    const editor = createEditorRecovery(
      identity,
      input,
      articleRecoveryValues,
      () => sessionStorage,
      true,
    );
    expect(get(editor).awaitingChoice).toBe(false);
    editor.capture(input);
    editor.dispose();
    expect(open().restore()).toMatchObject(input);
  });

  it('ログアウトは全対象を消し、待機タイマー・離脱イベントでも復活させない', () => {
    storeDraft();
    const editor = open();
    editor.restore();
    editor.capture(input);
    sessionStorage.setItem(recoveryKey({ editor: 'album', target: null }), 'draft');
    sessionStorage.setItem('unrelated', 'keep');
    expect(clearEditorRecoveries()).toBeNull();
    vi.advanceTimersByTime(1000);
    editor.dispose();
    expect(sessionStorage.length).toBe(1);
    expect(sessionStorage.getItem('unrelated')).toBe('keep');
  });

  it('記事の既知の入力欄だけを復元する', () => {
    expect(
      articleRecoveryValues({
        ...ARTICLE,
        session: { token: 'not-a-real-token' },
        tags: ['ignored'],
      }),
    ).toEqual(ARTICLE);
    expect(articleRecoveryValues({ ...ARTICLE, body: 4 })).toBeNull();
  });

  it('作品の曲目・チューン・外部音源の未完成値と順序を保持し、未知の欄を落とす', () => {
    const values = {
      draft: { ...ALBUM, 'event.date': '2026-', 'basePrice.amount': '-' },
      audios: [{ externalAudioId: null, url: 'https://' }],
      tracks: [
        {
          ...EMPTY_TRACK,
          tunes: [
            { ...EMPTY_TUNE, tuneTitle: ' ' },
            { ...EMPTY_TUNE, tuneTitle: '次' },
          ],
        },
      ],
      coverImageUrl: null,
    };
    expect(
      albumRecoveryValues({
        ...values,
        upload: { signedUrl: 'not-a-real-url' },
        session: 'ignored',
      }),
    ).toEqual(values);
    expect(
      albumRecoveryValues({ ...values, tracks: [{ ...EMPTY_TRACK, tunes: [null] }] }),
    ).toBeNull();
  });
});
