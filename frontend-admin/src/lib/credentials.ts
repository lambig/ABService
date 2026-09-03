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
 * 閉じれば残らない状態にする。持続させる必要が出たら、鍵そのものではなく期限付きのトークンを持つ形へ
 * 移す（認証の方式は #116 が持つ）。
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
