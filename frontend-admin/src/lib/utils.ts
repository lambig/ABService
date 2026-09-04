import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * クラス名を結合し、Tailwind の競合するユーティリティを後勝ちで解決する。
 *
 * shadcn-svelte のコンポーネントが `$lib/utils` から参照する。呼び出し側が渡した class を
 * 既定の class より後に置くことで、コンポーネントの見た目を利用側から上書きできる。
 *
 * 綴りは `frontend-public` の同名ファイルと揃える（どちらも shadcn-svelte の生成物が要求する
 * 受け皿であり、置き場は生成器が出す `$lib/utils` に固定される）。片方だけ変えると、同じ
 * コンポーネントの見た目の解決が2つのアプリで食い違う。
 */
export const cn = (...inputs: readonly ClassValue[]): string => twMerge(clsx(inputs));

/**
 * 要素参照（`bind:this`）を受け取れるようにした属性型。
 *
 * shadcn-svelte のコンポーネントが参照する。
 */
export type WithElementRef<T, U extends HTMLElement = HTMLElement> = T & { ref?: U | null };

/**
 * 子の指定を外した属性型。
 *
 * 子を自前で組み立てるコンポーネント（dialog の content 等）が、利用側から子を渡されないことを
 * 型で示すために参照する。
 */
export type WithoutChildrenOrChild<T> = Omit<T, 'children' | 'child'>;
