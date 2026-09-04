import { RuleTester } from 'eslint';
import { describe, it } from 'vitest';

import { inlineCommentRequiresWhyNotPrefix } from '../rules/inline-comment-requires-why-not-prefix.js';

/*
 * RULE-TESTER: eslint の RuleTester は describe / it を自身では持たず、走らせる側の枠組みを受け取る。
 * vitest のものを渡すことで、失敗が1ケースずつ報告される。
 */
RuleTester.describe = describe;
RuleTester.it = it;

const ruleTester = new RuleTester({
  languageOptions: { ecmaVersion: 2022, sourceType: 'module' },
});

ruleTester.run('inline-comment-requires-why-not-prefix', inlineCommentRequiresWhyNotPrefix, {
  valid: [
    {
      name: '大文字のプレフィックスを伴う行コメントは許す',
      code: '// HACK: 上流の不具合を避けるため\nconst a = 1;\n',
    },
    {
      name: 'ハイフンを含むプレフィックスも許す',
      code: '// PARSER-ORDER: svelte のパーサより前に置く必要がある\nconst a = 1;\n',
    },
    {
      name: 'プレフィックスの無いブロックコメントは対象外',
      code: '/* 一般的な説明。ブロックコメントは縛らない */\nconst a = 1;\n',
    },
    {
      name: '複数行のブロックコメントも対象外',
      code: '/*\n * 一般的な説明。\n */\nconst a = 1;\n',
    },
    {
      name: '文字列中の URL はコメントではない',
      code: "const url = 'https://example.com/';\n",
    },
    {
      name: 'トリプルスラッシュの指令は対象外',
      code: '/// <reference types="node" />\nconst a = 1;\n',
    },
    {
      name: 'コメントが無ければ何も言わない',
      code: 'const a = 1;\n',
    },
  ],

  invalid: [
    {
      name: 'プレフィックスの無い行コメントを塞ぐ',
      code: '// 一般的な理由をそのまま書いた\nconst a = 1;\n',
      errors: [{ messageId: 'missingPrefix' }],
    },
    {
      name: '行末に付けた行コメントも塞ぐ',
      code: 'const a = 1; // 一般的な理由\n',
      errors: [{ messageId: 'missingPrefix' }],
    },
    {
      name: '小文字のプレフィックスは許さない',
      code: '// hack: 小文字では区別が付かない\nconst a = 1;\n',
      errors: [{ messageId: 'missingPrefix' }],
    },
    {
      name: 'コロンが無ければ許さない',
      code: '// HACK 理由\nconst a = 1;\n',
      errors: [{ messageId: 'missingPrefix' }],
    },
    {
      name: '複数の違反をそれぞれ報告する',
      code: '// 1つ目\n// 2つ目\nconst a = 1;\n',
      errors: [{ messageId: 'missingPrefix' }, { messageId: 'missingPrefix' }],
    },
  ],
});
