import { describe, expect, it } from 'vitest';
import { toEmbedUrl } from './index';

describe('外部音源の埋め込み先', () => {
  it.each([
    'https://soundcloud.com/example/track',
    'https://soundcloud.com/example/曲?secret_token=s-test&auto_play=true#fragment',
    'javascript:alert("example")',
    'https://example.com/unsupported',
    '',
  ])('未検証の入力も単一の値として固定したプレイヤーへ渡す: %s', (input) => {
    const embed = new URL(toEmbedUrl(input));
    expect(embed.origin).toBe('https://w.soundcloud.com');
    expect(embed.pathname).toBe('/player/');
    expect([...embed.searchParams.entries()]).toEqual([
      ['url', input],
      ['auto_play', 'false'],
      ['show_user', 'true'],
      ['visual', 'true'],
    ]);
    expect(embed.hash).toBe('');
  });
});
