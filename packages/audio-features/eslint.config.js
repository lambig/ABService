import js from '@eslint/js';
import functional from 'eslint-plugin-functional';
import tseslint from 'typescript-eslint';
import abservice from 'abservice-eslint-config';

export default tseslint.config(
  js.configs.recommended,
  ...tseslint.configs.strictTypeChecked,
  functional.configs.externalRecommended,
  ...abservice,
  {
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
  },
);
