import type { ApiResult } from './api/http';

/** 鍵の置き場。アプリ名で修飾し、同じ生成元に他のものが入っても衝突させない */
const STORAGE_KEY = 'abservice.admin.api-key';

/**
 * 管理APIの鍵を、そのタブが開いている間だけ保持する。
 *
 * <p>
 * 鍵をビルドへ焼き込まない（#122）。管理画面は静的な成果物として配信されるため、埋め込むと成果物を
 * 受け取れる誰もが管理操作をできることになる。入力を受け取り、保持はブラウザ側に閉じる。
 * </p>
 *
 * <p>
 * `localStorage` ではなく `sessionStorage` に置く。共用の端末で開いたまま離れたときに、タブを
 * 閉じれば残らない状態にする。ただしブラウザが鍵そのものを持つ形自体を、失効させられて期限も切れる
 * トークンを持つ形へ移す（#264）。
 * </p>
 */
export const storedApiKey = (): string | null => sessionStorage.getItem(STORAGE_KEY);

/** 受け付けられた鍵を覚える。 */
export const rememberApiKey = (apiKey: string): void => {
  sessionStorage.setItem(STORAGE_KEY, apiKey);
};

/** 覚えている鍵を捨てる。 */
export const forgetApiKey = (): void => {
  sessionStorage.removeItem(STORAGE_KEY);
};

/**
 * 応答の枝ごとに、鍵を覚えるか捨てるか。
 *
 * <p>
 * 受け付けられた鍵だけを覚える。断られた鍵を残すと、次に開いたときも同じ失敗から始まる。到達できない
 * だけの失敗では捨てない（鍵の正しさとは別の理由のため）。この規則は画面をまたいで同じであるため、
 * 一覧と編集の双方がここを通す。
 * </p>
 */
export const KEY_STORE = {
  ok: rememberApiKey,
  unauthorized: forgetApiKey,
  failed: rememberApiKey,
} satisfies Record<ApiResult<unknown>['kind'], (apiKey: string) => void>;
