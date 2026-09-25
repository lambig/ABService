import { describe, expect, it } from 'vitest';

import { renderMarkup, renderMarkupParts } from './index.js';

const ASSET_BASE_PATH = '/assets';

describe('説明と補足の分割', () => {
  const parts = (text: string) => renderMarkupParts(text, { assetBasePath: ASSET_BASE_PATH });
  it('文書全体の変換結果を保持し、次の最上位見出しで分ける', () => {
    const text = '# 説明\n\n[出典][ref]\n\n## 補足\n\n注記[^a]\n\n[ref]: https://example.com/source\n\n[^a]: 脚注';
    const result = parts(text);
    expect(result.lead).toContain('href="https://example.com/source"');
    expect(result.lead).not.toContain('<h2>補足</h2>');
    expect(result.details).toContain('<h2>補足</h2>');
    expect(result.details).toContain('脚注');
    expect(result.lead + result.details).toBe(render(text));
  });
  it('引用・コード・折畳み内の見出しでは分けない', () => {
    const text = '> ## 引用\n> 説明\n\n```md\n## code\n```\n\n:::details[参照]\n## 内部\n本文\n:::\n\n## 補足\n本文';
    const result = parts(text);
    expect(result.lead).toContain('</blockquote>');
    expect(result.lead).toContain('</details>');
    expect(result.details).toContain('<h2>補足</h2>');
    expect(result.lead + result.details).toBe(render(text));
  });
  it('見出し無しと空文書では説明だけを返す', () => {
    expect(parts('説明')).toEqual({ lead: '<p>説明</p>', details: '' });
    expect(parts('')).toEqual({ lead: '', details: '' });
  });
  it('両領域で同じサニタイザーと画像制限を維持する', () => {
    const text = '[bad](javascript:alert(1))\n\n## 補足\n\n![bad](/other/image.png)\n\n<script>alert(1)</script>';
    const result = parts(text);
    expect(result.lead + result.details).toBe(render(text));
    expect(result.lead + result.details).not.toContain('javascript:');
    expect(result.details).not.toContain('<script');
    expect(result.details).not.toContain('src="/other');
  });
});

function render(markdown: string): string {
  return renderMarkup(markdown, { assetBasePath: ASSET_BASE_PATH });
}

describe('マークアップ描画', () => {
  describe('生HTMLをパースしない', () => {
    it('script はタグとして出力されない', () => {
      expect(render('<script>alert(1)</script>')).not.toContain('<script');
    });

    it('onerror 付きの img はタグとして出力されない', () => {
      expect(render('<img src=x onerror=alert(1)>')).not.toContain('<img');
    });

    it('details の直書きはタグとして出力されない', () => {
      expect(render('<details><summary>x</summary>y</details>')).not.toContain(
        '<details',
      );
    });

    it('生HTMLはタグもテキストも残らない（描画の対象にしない）', () => {
      expect(render('<script>alert(1)</script>')).not.toContain('alert(1)');
    });

    it('生HTMLの前後の Markdown は描画される（落ちるのは生HTMLだけ）', () => {
      const html = render('## 見出し\n\n<script>alert(1)</script>\n\n本文');
      expect(html).toContain('<h2>見出し</h2>');
      expect(html).toContain('本文');
      expect(html).not.toContain('alert(1)');
    });
  });

  describe('CommonMark + GFM を通す', () => {
    it('見出しを描画する', () => {
      expect(render('## 概要')).toContain('<h2>概要</h2>');
    });

    it('表を描画する', () => {
      const html = render('| a | b |\n| --- | --- |\n| 1 | 2 |');
      expect(html).toContain('<table>');
      expect(html).toContain('<th>a</th>');
      expect(html).toContain('<td>1</td>');
    });

    it('取り消し線を描画する', () => {
      expect(render('~~廃止~~')).toContain('<del>廃止</del>');
    });

    it('タスクリストを描画する', () => {
      expect(render('- [x] 完了\n- [ ] 未了')).toContain('type="checkbox"');
    });

    it('リンクを描画する', () => {
      expect(render('[表題](https://example.com/)')).toContain(
        'href="https://example.com/"',
      );
    });
  });

  describe('折りたたみのディレクティブ', () => {
    it('details ディレクティブを details/summary へ変換する', () => {
      const html = render(
        ':::details[収録曲について]\n本文がここに入る。\n:::',
      );
      expect(html).toContain('<details>');
      expect(html).toContain('<summary>収録曲について</summary>');
      expect(html).toContain('本文がここに入る。');
    });

    it('details の中の装飾やリストも描画する', () => {
      const html = render(':::details[詳細]\n**強調**\n\n- 項目\n:::');
      expect(html).toContain('<strong>強調</strong>');
      expect(html).toContain('<li>項目</li>');
    });

    it('ラベルのない details も変換する', () => {
      expect(render(':::details\n本文\n:::')).toContain('<details>');
    });

    it('未知のディレクティブは中身ごと落とす', () => {
      const html = render(':::note\n落とされる本文\n:::');
      expect(html).not.toContain('落とされる本文');
      expect(html).not.toContain('note');
    });

    it('未知のディレクティブは記法のままテキストにもしない', () => {
      expect(render(':::note\n本文\n:::')).not.toContain(':::');
    });
  });

  describe('画像は配信ベースパス配下に限る', () => {
    it('配信ベースパス配下の画像は残る', () => {
      const html = render('![表紙](/assets/cover.png)');
      expect(html).toContain('src="/assets/cover.png"');
      expect(html).toContain('alt="表紙"');
    });

    it('外部URLの画像は落とす', () => {
      expect(render('![外部](https://example.com/x.png)')).not.toContain(
        '<img',
      );
    });

    it('data URI の画像は落とす', () => {
      expect(
        render('![埋め込み](data:image/png;base64,iVBORw0KGgo=)'),
      ).not.toContain('<img');
    });

    it('配信ベースパスに前方一致するだけの別パスは落とす', () => {
      expect(render('![別](/assets-public/x.png)')).not.toContain('<img');
    });
  });

  describe('スタイルを焼き付けない', () => {
    it('出力にクラス属性を含まない', () => {
      const html = render(
        '## 見出し\n\n| a |\n| --- |\n| 1 |\n\n:::details[ラベル]\n本文\n:::\n\n![表紙](/assets/cover.png)',
      );
      expect(html).not.toContain('class=');
    });
  });
});

describe('画像のURL正規化後の境界 (#289)', () => {
  it.each([
    '/assets/../api/v1/albums',
    '/assets/%2e%2e/api/v1/albums',
    '/assets/%2E./api/v1/albums',
    '/assets/.%2e/api/v1/albums',
    '/assets/..\\api/v1/albums',
    '/assets/..%5capi/v1/albums',
    '//example.com/assets/cover.png',
    '/\\example.com/assets/cover.png',
    'https://asset-validation.invalid/assets/cover.png',
    '/assets/a/../../api/v1/albums',
    '/assets/a/../cover.png',
  ])('曖昧なパスまたは外部originを描画しない: %s', (src) => {
    expect(render(`![画像](<${src}>)`)).not.toContain('<img');
  });

  it.each(['/assets', '/assets/'])(
    '末尾スラッシュの有無で許可範囲が変わらない: %s',
    (assetBasePath) => {
      const html = renderMarkup(
        '![表紙](/assets/album/cover.png?size=small#image)',
        { assetBasePath },
      );
      const src = /src="([^"]+)"/u.exec(html)?.[1];
      expect(src).toBe('/assets/album/cover.png?size=small#image');
      const destination = new URL(src ?? '', 'https://portfolio.example');
      expect(destination.origin).toBe('https://portfolio.example');
      expect(destination.pathname).toBe('/assets/album/cover.png');
    },
  );

  it('独自のベースパスも正規化後の境界で検査する', () => {
    const options = { assetBasePath: '/media/images' };
    const html = renderMarkup('![表紙](/media/images/cover.png)', options);
    const src = /src="([^"]+)"/u.exec(html)?.[1];
    expect(new URL(src ?? '', 'https://portfolio.example').pathname).toBe(
      '/media/images/cover.png',
    );
    expect(
      renderMarkup('![別](/media/images-other/cover.png)', options),
    ).not.toContain('<img');
    expect(
      renderMarkup('![逸脱](/media/images/%2e%2e/private.png)', options),
    ).not.toContain('<img');
  });

  it.each([
    '',
    '/',
    'assets',
    '//example.com/assets',
    'https://example.com/assets',
    '/assets/..',
    '/assets?x=1',
    '/assets#x',
  ])('不正な設定は画像を許可しない: %s', (assetBasePath) => {
    expect(
      renderMarkup('![表紙](/assets/cover.png)', { assetBasePath }),
    ).not.toContain('<img');
  });

  it('除去する画像が連続しても後続の画像を検査する', () => {
    const html = render(
      '![1](/assets/../api/a) ![2](/assets/%2e%2e/api/b) ![3](/assets/cover.png)',
    );
    expect(html.match(/<img/gu)).toHaveLength(1);
    expect(html).toContain('src="/assets/cover.png"');
  });
});
