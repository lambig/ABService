import { publicApiJsdoc, typescriptWorkspace } from 'abservice-eslint-config';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['node_modules/**', 'eslint.config.js'] },
  ...typescriptWorkspace({ tsconfigRootDir: import.meta.dirname }),
  publicApiJsdoc({ files: ['src/index.ts'] }),
);
