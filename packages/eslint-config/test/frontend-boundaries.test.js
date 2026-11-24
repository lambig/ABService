import { fileURLToPath } from 'node:url';
import { ESLint } from 'eslint';
import tseslint from 'typescript-eslint';
import { describe, expect, it } from 'vitest';

/* 実アプリの設定を読む。仮想ファイルは tsconfig に存在しないため型検査だけを外す。 */
const appLint = (app) =>
  new ESLint({
    cwd: fileURLToPath(new URL(`../../../${app}/`, import.meta.url)),
    overrideConfig: [tseslint.configs.disableTypeChecked],
  });
const messages = async (eslint, code, filePath) =>
  (await eslint.lintText(code, { filePath })).flatMap(
    (result) => result.messages,
  );

describe.each(['frontend-admin', 'frontend-public'])(
  '%s dependency boundaries',
  (app) => {
    const eslint = appLint(app);

    it.each([
      '$components/Panel.svelte',
      '$layouts/Base.astro',
      '../components/Panel.svelte',
      '../../components/Panel.svelte',
      './../../../components/Panel.svelte',
      'src/components/Panel.svelte',
      '/src/components/Panel.svelte',
      '$lib/../components/Panel.svelte',
    ])('rejects lib dependency on %s', async (specifier) => {
      expect(
        await messages(
          eslint,
          `export { default } from '${specifier}';`,
          'src/lib/probe.ts',
        ),
      ).toEqual(
        expect.arrayContaining([
          expect.objectContaining({ ruleId: 'no-restricted-imports' }),
        ]),
      );
    });

    it.each([
      '$lib/api/client',
      '../api/client',
      '$lib/session/credentials',
      '$lib/editor/editor-recovery',
      '$lib/site/site',
    ])('rejects presentation runtime dependency on %s', async (specifier) => {
      expect(
        await messages(
          eslint,
          `export { value } from '${specifier}';`,
          'src/lib/presentation/probe.ts',
        ),
      ).toEqual(
        expect.arrayContaining([
          expect.objectContaining({ ruleId: 'no-restricted-imports' }),
        ]),
      );
    });

    it('allows an API type without introducing runtime I/O', async () => {
      expect(
        await messages(
          eslint,
          "export type { Value } from '$lib/api/client';",
          'src/lib/presentation/probe.ts',
        ),
      ).toEqual([]);
    });
    it.each([
      ['src/components/probe.ts', '$lib/presentation/format'],
      ['src/layouts/probe.ts', '$components/Panel.svelte'],
      ['src/pages/probe.ts', '$layouts/Base.astro'],
      ['src/lib/site/probe.ts', '$lib/api/client'],
    ])('allows inward dependency from %s', async (filePath, specifier) => {
      expect(
        await messages(
          eslint,
          `export { value } from '${specifier}';`,
          filePath,
        ),
      ).toEqual([]);
    });
    it.each([
      "export const value = import('$components/Panel.svelte');",
      "export const value = require('$components/Panel.svelte');",
      "export type Value = import('$components/Panel.svelte').Value;",
    ])('rejects an unchecked lib import form', async (code) => {
      expect(await messages(eslint, code, 'src/lib/probe.ts')).toEqual(
        expect.arrayContaining([
          expect.objectContaining({ ruleId: 'no-restricted-syntax' }),
        ]),
      );
    });
    it.each(['js', 'mjs'])(
      'retains syntax and import rules for .%s',
      async (extension) => {
        const result = await messages(
          eslint,
          "import 'node:assert'; if (true) {}",
          `scripts/probe.${extension}`,
        );
        expect(result.map(({ ruleId }) => ruleId)).toEqual(
          expect.arrayContaining([
            'no-restricted-imports',
            'no-restricted-syntax',
          ]),
        );
        expect(
          await messages(
            eslint,
            "import { readFile } from 'node:fs/promises'; export const read = () => readFile('input');",
            `scripts/valid.${extension}`,
          ),
        ).toEqual([]);
      },
    );
    it('checks CommonJS scripts and rejects require in lib', async () => {
      expect(
        (await messages(eslint, 'if (true) {}', 'scripts/probe.cjs')).map(
          ({ ruleId }) => ruleId,
        ),
      ).toContain('no-restricted-syntax');
      expect(
        (
          await messages(
            eslint,
            "module.exports = require('$components/Panel.svelte');",
            'src/lib/probe.cjs',
          )
        ).map(({ ruleId }) => ruleId),
      ).toContain('no-restricted-syntax');
    });
    it.each([
      [
        'astro',
        "---\nimport Panel from '$layouts/Base.astro';\n---\n<Panel />",
      ],
      [
        'svelte',
        '<script lang="ts">import Panel from \'$layouts/Base.svelte\';</script><Panel />',
      ],
    ])('checks %s component imports', async (extension, code) => {
      const result = await messages(
        eslint,
        code,
        `src/components/Probe.${extension}`,
      );
      expect(
        result.map(({ ruleId }) => ruleId),
        JSON.stringify(result),
      ).toContain('no-restricted-imports');
    });
    it('keeps a new lib .mjs file inside the boundary', async () => {
      expect(
        await messages(
          eslint,
          "export { value } from '$components/Panel.svelte';",
          'src/lib/probe.mjs',
        ),
      ).toEqual(
        expect.arrayContaining([
          expect.objectContaining({ ruleId: 'no-restricted-imports' }),
        ]),
      );
    });
  },
);
