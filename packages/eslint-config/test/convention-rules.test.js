import { ESLint } from 'eslint';
import { describe, expect, it } from 'vitest';

import {
  commentsPlugin,
  commentsPluginName,
  conventionRules,
  localPlugin,
  localPluginName,
  publicApiJsdoc,
} from '../index.js';

/*
 * BEHAVIOUR-OVER-SHAPE: 設定の中身を突き合わせるのではなく、eslint を実際に走らせて「弾かれるか」を見る。
 * ルール名やオプションの綴りが変わっても、契約（この書き方は通らない）が守られていれば通る。
 *
 * 型情報を使うルール（strictTypeChecked）は tsconfig を要するため、ここでは載せない。検査する対象
 * （輸入の制限・行コメント・JSDoc）はいずれも構文だけで判定できる。
 */
const lintWith = (config) =>
  new ESLint({
    overrideConfigFile: true,
    overrideConfig: [
      { languageOptions: { ecmaVersion: 2022, sourceType: 'module' } },
      ...(Array.isArray(config) ? config : [config]),
    ],
  });

const messagesOf = async (eslint, code, filePath) => {
  const results = await eslint.lintText(code, { filePath });
  return results.flatMap((result) => result.messages);
};

const rulesOf = async (eslint, code, filePath) => {
  const messages = await messagesOf(eslint, code, filePath);
  return messages.map((message) => message.ruleId);
};

const conventionConfig = {
  files: ['**/*.js'],
  plugins: {
    [commentsPluginName]: commentsPlugin,
    [localPluginName]: localPlugin,
  },
  rules: {
    'no-restricted-imports': conventionRules['no-restricted-imports'],
    'no-restricted-syntax': conventionRules['no-restricted-syntax'],
    [`${commentsPluginName}/require-description`]:
      conventionRules[`${commentsPluginName}/require-description`],
    [`${localPluginName}/inline-comment-requires-why-not-prefix`]:
      conventionRules[`${localPluginName}/inline-comment-requires-why-not-prefix`],
  },
};

describe('アサーションの輸入元', () => {
  /*
   * SPECIFIER-VARIANTS: Node の assert は `node:` の有無と `/strict` の有無で4通りに綴れる。
   * 1つでも落とすと迂回路が残るため、4通りすべてを個別に確かめる。
   */
  it.each(['node:assert', 'node:assert/strict', 'assert', 'assert/strict', 'chai'])(
    '%s の輸入を塞ぐ',
    async (specifier) => {
      const rules = await rulesOf(
        lintWith(conventionConfig),
        `import x from '${specifier}';\nexport const a = x;\n`,
        'probe.js',
      );

      expect(rules).toContain('no-restricted-imports');
    },
  );

  it('vitest の輸入は塞がない', async () => {
    const rules = await rulesOf(
      lintWith(conventionConfig),
      "import { expect } from 'vitest';\nexport const a = expect;\n",
      'probe.js',
    );

    expect(rules).not.toContain('no-restricted-imports');
  });
});

describe('深い相対パス', () => {
  it('2つ以上上をたどる相対パスを塞ぐ', async () => {
    const rules = await rulesOf(
      lintWith(conventionConfig),
      "import x from '../../elsewhere/thing.js';\nexport const a = x;\n",
      'probe.js',
    );

    expect(rules).toContain('no-restricted-imports');
  });

  it('1つ上は塞がない', async () => {
    const rules = await rulesOf(
      lintWith(conventionConfig),
      "import x from '../thing.js';\nexport const a = x;\n",
      'probe.js',
    );

    expect(rules).not.toContain('no-restricted-imports');
  });
});

describe('構文の制限', () => {
  it.each([
    ['if 文', 'export const a = () => {\n  if (true) {\n    return 1;\n  }\n  return 2;\n};\n'],
    ['switch 文', 'export const a = (v) => {\n  switch (v) {\n    default:\n      return 1;\n  }\n};\n'],
    ['論理和演算子', 'export const a = (v) => v || 1;\n'],
    ['否定', 'export const a = (v) => !v;\n'],
  ])('%s を塞ぐ', async (_name, code) => {
    const rules = await rulesOf(lintWith(conventionConfig), code, 'probe.js');

    expect(rules).toContain('no-restricted-syntax');
  });

  it('三項と ?? は塞がない', async () => {
    const rules = await rulesOf(
      lintWith(conventionConfig),
      'export const a = (v) => (v === null ? 1 : (v ?? 2));\n',
      'probe.js',
    );

    expect(rules).not.toContain('no-restricted-syntax');
  });
});

describe('eslint-disable の理由', () => {
  it('理由の無いディレクティブを塞ぐ', async () => {
    const rules = await rulesOf(
      lintWith(conventionConfig),
      '/* eslint-disable no-restricted-syntax */\nexport const a = 1;\n',
      'probe.js',
    );

    expect(rules).toContain(`${commentsPluginName}/require-description`);
  });

  it('理由を書いたディレクティブは通す', async () => {
    const rules = await rulesOf(
      lintWith(conventionConfig),
      '/* eslint-disable no-restricted-syntax -- この箇所固有の理由 */\nexport const a = 1;\n',
      'probe.js',
    );

    expect(rules).not.toContain(`${commentsPluginName}/require-description`);
  });
});

describe('行コメント', () => {
  it('プレフィックスの無い行コメントを塞ぐ', async () => {
    const rules = await rulesOf(
      lintWith(conventionConfig),
      '// 一般的な理由\nexport const a = 1;\n',
      'probe.js',
    );

    expect(rules).toContain(`${localPluginName}/inline-comment-requires-why-not-prefix`);
  });

  it('プレフィックスを伴う行コメントは通す', async () => {
    const rules = await rulesOf(
      lintWith(conventionConfig),
      '// HACK: この箇所固有の理由\nexport const a = 1;\n',
      'probe.js',
    );

    expect(rules).not.toContain(`${localPluginName}/inline-comment-requires-why-not-prefix`);
  });
});

describe('公開 API の JSDoc', () => {
  const jsdocConfig = [
    publicApiJsdoc({ files: ['**/index.js'] }),
    { files: ['**/index.js'], languageOptions: { ecmaVersion: 2022, sourceType: 'module' } },
  ];

  it('JSDoc の無い輸出を塞ぐ', async () => {
    const rules = await rulesOf(
      lintWith(jsdocConfig),
      'export const a = () => 1;\n',
      'src/index.js',
    );

    expect(rules).toContain('jsdoc/require-jsdoc');
  });

  it('説明の無い JSDoc を塞ぐ', async () => {
    const rules = await rulesOf(
      lintWith(jsdocConfig),
      '/** */\nexport const a = () => 1;\n',
      'src/index.js',
    );

    expect(rules).toContain('jsdoc/require-description');
  });

  it('説明を持つ JSDoc は通す', async () => {
    const rules = await rulesOf(
      lintWith(jsdocConfig),
      '/** 何かを返す。 */\nexport const a = () => 1;\n',
      'src/index.js',
    );

    expect(rules).toEqual([]);
  });

  it('輸出していない宣言は求めない', async () => {
    const rules = await rulesOf(
      lintWith(jsdocConfig),
      'const internal = () => 1;\n\n/** 内側の値を返す。 */\nexport const a = () => internal();\n',
      'src/index.js',
    );

    expect(rules).toEqual([]);
  });

  it('default で輸出した宣言にも求める', async () => {
    /*
     * PUBLIC-REACH: publicOnly は輸出の綴りではなく到達可能性で判定する。`export default a` は
     * `a` を公開するため、宣言そのものに JSDoc が要る。
     */
    const rules = await rulesOf(
      lintWith(jsdocConfig),
      'const a = () => 1;\nexport default a;\n',
      'src/index.js',
    );

    expect(rules).toContain('jsdoc/require-jsdoc');
  });
});
