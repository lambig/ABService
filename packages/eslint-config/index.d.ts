import type { ESLint, Linter } from 'eslint';
import type { ConfigArray, ConfigWithExtends } from 'typescript-eslint';

/**
 * 型の宣言を手で置く。このパッケージは素の JavaScript で書かれており、TypeScript は
 * `node_modules` 配下の `.js` から型を推論しない。宣言が無いと、参照側で暗黙の `any` になる
 * （規約が禁じているもの）。
 */

export declare const DEEP_RELATIVE_IMPORT: string;

export declare const DEEP_RELATIVE_IMPORT_MESSAGE: string;

export declare const SRC_ABSOLUTE_IMPORT: string;

export declare const SRC_ABSOLUTE_IMPORT_MESSAGE: string;

export declare const commentsPluginName: string;

export declare const commentsPlugin: ESLint.Plugin;

export declare const localPluginName: string;

export declare const localPlugin: ESLint.Plugin;

export interface RestrictedSyntaxEntry {
  readonly selector: string;
  readonly message: string;
}

export declare const restrictedSyntax: readonly RestrictedSyntaxEntry[];

export interface RestrictedPathEntry {
  readonly name: string;
  readonly message: string;
}

export declare const forbiddenAssertionPaths: readonly RestrictedPathEntry[];

export declare const conventionRules: Linter.RulesRecord;

export interface PublicApiJsdocOptions {
  /** 公開 API を持つファイル（各パッケージの入口） */
  readonly files: readonly string[];
}

export declare const publicApiJsdoc: (options: PublicApiJsdocOptions) => ConfigWithExtends;

export interface TypeCheckedLayerOptions {
  /** 参照側の `import.meta.dirname`。tsconfig の解決をそのワークスペースの位置から始める */
  readonly tsconfigRootDir: string;
  readonly files?: readonly string[];
  readonly extraFileExtensions?: readonly string[];
}

export declare const typeCheckedLayer: (options: TypeCheckedLayerOptions) => ConfigWithExtends;

export interface WorkspaceOptions {
  /** 参照側の `import.meta.dirname` */
  readonly tsconfigRootDir: string;
}

export declare const typescriptWorkspace: (options: WorkspaceOptions) => ConfigArray;
