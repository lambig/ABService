import { describe, expect, it } from 'vitest';
import { bridgeEntry } from './listening-entry';

const valid = {
  kind: 'ssm-audio',
  preparationUrl: 'https://example.test/admin/listening/',
  listeningUrl: 'https://example.test/offline-player/',
};
describe('試聴機へ渡す正式入口', () => {
  it('同じ正式originの固定パスだけを採用する', () => {
    expect(bridgeEntry(valid)).toEqual({ ...valid, kind: 'bridge' });
  });
  it.each([
    { ...valid, preparationUrl: 'http://127.0.0.1:18092/admin/listening/' },
    { ...valid, preparationUrl: 'https://localhost/admin/listening/' },
    { ...valid, preparationUrl: 'https://secret@example.test/admin/listening/' },
    { ...valid, listeningUrl: 'https://other.test/offline-player/' },
    { ...valid, listeningUrl: 'https://example.test/offline-player/?token=synthetic' },
    null,
  ])('ローカル接続・資格情報・別サイトや秘密値のクエリを案内しない', (value) => {
    expect(bridgeEntry(value)).toBeNull();
  });
});
