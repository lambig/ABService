import { createHash } from 'node:crypto';
import { describe, expect, it } from 'vitest';
import { fingerprint, MAX_AUDIO_BYTES, pendingAudio } from './private-audio';

describe('FLAC原本の照合', () => {
  it('チャンク境界をまたぐ原本をSHA256で照合する', async () => {
    const bytes = new Uint8Array(131073).fill(23);
    expect(
      await fingerprint(new File([bytes], 'fixture.flac'), new AbortController().signal),
    ).toEqual({
      byteLength: bytes.length,
      sha256: createHash('sha256').update(bytes).digest('hex'),
    });
  });
  it('空・上限超過・中断では照合値を返さない', async () => {
    await expect(
      fingerprint(new File([], 'empty.flac'), new AbortController().signal),
    ).rejects.toThrow();
    const huge = new File(['fixture'], 'oversize.flac');
    /* eslint-disable-next-line functional/immutable-data -- BOUNDARY-FIXTURE: 256MiBを確保せずFileの容量境界だけを検査する。 */
    Object.defineProperty(huge, 'size', { value: MAX_AUDIO_BYTES + 1 });
    await expect(fingerprint(huge, new AbortController().signal)).rejects.toThrow();
    await expect(
      fingerprint(new File(['fixture'], 'abort.flac'), AbortSignal.abort()),
    ).rejects.toThrow();
  });
  it('破損した復旧記録を採用しない', () => {
    expect(pendingAudio({ audioId: '../other', byteLength: 1, sha256: 'a'.repeat(64) })).toBe(
      false,
    );
    expect(pendingAudio(null)).toBe(false);
    expect(
      pendingAudio({
        audioId: '0192f8a0-0000-7000-8000-000000000001',
        byteLength: -1,
        sha256: 'a'.repeat(64),
      }),
    ).toBe(false);
  });
});
