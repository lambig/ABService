import { PUBLIC_API_BASE_URL } from 'astro:env/client';
import { requestEmpty, requestJson, type ApiResult } from './http';
import type { components } from './schema';

type SessionValue = components['schemas']['AdminSessionResponse'];
/** 保存値と応答を検査し、期限切れや破損した値を認証済みにしない。 */
export const sessionValueOf = (value: unknown): SessionValue | null =>
  typeof value === 'object' &&
  value !== null &&
  'token' in value &&
  'expiresAt' in value &&
  typeof value.token === 'string' &&
  /^abs_session_[A-Za-z0-9_-]+$/.test(value.token) &&
  typeof value.expiresAt === 'string' &&
  Date.parse(value.expiresAt) > Date.now()
    ? { token: value.token, expiresAt: value.expiresAt }
    : null;

export const exchangeSession = async (apiKey: string): Promise<ApiResult<SessionValue>> => {
  const result = await requestJson<unknown>(`${PUBLIC_API_BASE_URL}/api/v1/admin/sessions`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${apiKey}` },
    cache: 'no-store',
  });
  const value = result.kind === 'ok' ? sessionValueOf(result.value) : null;
  return result.kind !== 'ok'
    ? result
    : value === null
      ? {
          kind: 'failed',
          reason: 'invalid-response',
          message: '管理APIから有効なセッションが返りませんでした。',
        }
      : { kind: 'ok', value };
};
export const revokeSession = (token: string): Promise<ApiResult<void>> =>
  requestEmpty(`${PUBLIC_API_BASE_URL}/api/v1/admin/sessions/current`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
