import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * クラス名を結合し、Tailwind の競合するユーティリティを後勝ちで解決する。
 *
 * shadcn-svelte のコンポーネントが `$lib/utils` から参照する。呼び出し側が渡した class を
 * 既定の class より後に置くことで、コンポーネントの見た目を利用側から上書きできる。
 */
export const cn = (...inputs: readonly ClassValue[]): string => twMerge(clsx(inputs));

/**
 * 要素参照（`bind:this`）を受け取れるようにした属性型。
 *
 * shadcn-svelte のコンポーネントが参照する。上流が置く `WithoutChild` 系の型は、それを要求する
 * コンポーネントを入れるときに足す。
 */
export type WithElementRef<T, U extends HTMLElement = HTMLElement> = T & { ref?: U | null };
