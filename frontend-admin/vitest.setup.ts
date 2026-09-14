import { cleanup } from '@testing-library/svelte';
import { afterEach } from 'vitest';

/*
 * 描いたコンポーネントを1件ごとに片付ける。`@testing-library/svelte` は `afterEach` が大域にあるとき
 * 自分で登録するが、このリポジトリは `globals` を立てず `vitest` から輸入する書き方に揃えている
 * （既存の `src/lib/**` のテストと同じ）。したがって登録はここで明示する。
 *
 * 片付けないと、前のテストが描いた DOM が次のテストにも残り、位置や名前で引く検索が複数に当たる。
 */
afterEach(cleanup);
