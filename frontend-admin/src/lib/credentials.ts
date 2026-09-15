import { clearEditorRecoveries } from './editor-recovery';
import { get, readonly, writable } from 'svelte/store';

import { exchangeSession, revokeSession, sessionValueOf } from './api/sessions';
import type { ApiResult } from './api/http';

/** 世代は要求を区別するページ内の印で、保存・共有しない。 */
export type AdminSession = {
  readonly token: string;
  readonly expiresAt: string;
  readonly generation: symbol;
};
export const SESSION_STORAGE_KEY = 'abservice.admin.session';
const LEGACY_STORAGE_KEY = 'abservice.admin.api-key';
type State = {
  readonly generation: symbol;
  readonly session: AdminSession | null;
  readonly authenticating: boolean;
};
const state = writable<State>({ generation: Symbol(), session: null, authenticating: false });
export const sessionState = readonly(state);

/** 通信前に保存値を消して世代を進め、古い応答の保存・画面更新を防ぐ。 */
const begin = (authenticating: boolean): symbol => {
  const generation = Symbol();
  sessionStorage.removeItem(LEGACY_STORAGE_KEY);
  sessionStorage.removeItem(SESSION_STORAGE_KEY);
  state.set({ generation, session: null, authenticating });
  return generation;
};
const sameGeneration = (generation: symbol): boolean => get(state).generation === generation;
export const isCurrentSession = (session: AdminSession): boolean =>
  sameGeneration(session.generation) && get(state).session?.token === session.token;

const remember = (
  value: { readonly token: string; readonly expiresAt: string },
  generation: symbol,
): AdminSession => {
  const session = { ...value, generation };
  sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(value));
  state.set({ generation, session, authenticating: false });
  return session;
};
const parsed = (raw: string | null): unknown => {
  try {
    return raw === null ? null : JSON.parse(raw);
  } catch {
    return null;
  }
};

/** 旧APIキーは無条件で消し、有効なトークンと期限だけをこのタブで再利用する。 */
export const storedSession = (): AdminSession | null => {
  sessionStorage.removeItem(LEGACY_STORAGE_KEY);
  const value = sessionValueOf(parsed(sessionStorage.getItem(SESSION_STORAGE_KEY)));
  const current = get(state).session;
  return value === null
    ? (begin(false), null)
    : current?.token === value.token
      ? current
      : remember(value, get(state).generation);
};

/** 古い交換結果は画面に渡さず、払い出されていた場合は最善努力で失効させる。 */
export const authenticate = async (apiKey: string): Promise<ApiResult<AdminSession> | null> => {
  const generation = begin(true);
  const result = await exchangeSession(apiKey);
  const current = sameGeneration(generation);
  void (current ? undefined : result.kind === 'ok' ? revokeSession(result.value.token) : undefined);
  return current
    ? result.kind === 'ok'
      ? { kind: 'ok', value: remember(result.value, generation) }
      : (state.set({ generation, session: null, authenticating: false }), result)
    : null;
};

/** 副作用の境界。古い401で新しいセッションを消すことも防ぐ。 */
export const applySessionResult = <T, R>(
  session: AdminSession,
  result: ApiResult<T>,
  apply: () => R,
): R | undefined =>
  isCurrentSession(session)
    ? (result.kind === 'unauthorized' ? begin(false) : undefined, apply())
    : undefined;

/** ローカル破棄は同期的に完了する。失効通信の成否は別の結果として返す。 */
export const logout = (): { readonly completion: Promise<string | null> } => {
  const recoveryError = clearEditorRecoveries();
  const session = get(state).session;
  const generation = begin(false);
  const completion =
    session === null
      ? Promise.resolve(null)
      : revokeSession(session.token).then((result) =>
          sameGeneration(generation)
            ? [result.kind === 'ok', result.kind === 'unauthorized' && result.status === 401].some(
                Boolean,
              )
              ? 'ログアウトしました。'
              : 'このタブからログアウトしました。サーバーでの失効は確認できませんでした。トークンは発行から最大30分で期限切れになります。'
            : null,
        );
  return {
    completion: completion.then((message) =>
      recoveryError === null
        ? message
        : `${message ?? ''} 退避データを消去できませんでした。このタブを閉じてください。`,
    ),
  };
};
