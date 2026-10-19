import { PUBLIC_API_BASE_URL } from 'astro:env/client';
import { isCurrentSession, type AdminSession } from '$lib/credentials';
import { requestJson, requestEmpty, type ApiResult } from './http';
import type { components } from './schema';

export type Authorization = components['schemas']['ListeningAuthorizationResponse'];
export type Device = components['schemas']['ListeningDeviceResponse'];

const call = <T>(session: AdminSession, path: string, body?: unknown): Promise<ApiResult<T>> =>
  isCurrentSession(session) && Date.parse(session.expiresAt) > Date.now()
    ? requestJson<T>(`${PUBLIC_API_BASE_URL}/api/v1/admin/${path}`, {
        method: body === undefined ? 'GET' : 'POST',
        redirect: 'error',
        cache: 'no-store',
        headers: { Authorization: `Bearer ${session.token}`, 'Content-Type': 'application/json' },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      })
    : Promise.resolve({ kind: 'unauthorized' });

export const resolveAuthorization = (
  session: AdminSession,
  code: string,
): Promise<ApiResult<Authorization>> => call(session, 'listening-authorizations/resolve', { code });
export const decideAuthorization = (
  session: AdminSession,
  id: string,
  approve: boolean,
  validDays: number,
): Promise<ApiResult<Authorization>> =>
  call(session, `listening-authorizations/${encodeURIComponent(id)}/decision`, {
    approve,
    validDays,
  });
export const listeningDevices = (
  session: AdminSession,
): Promise<ApiResult<components['schemas']['ListeningDeviceListResponse']>> =>
  call(session, 'listening-devices');
export const revokeDevice = (session: AdminSession, id: string): Promise<ApiResult<void>> =>
  isCurrentSession(session) && Date.parse(session.expiresAt) > Date.now()
    ? requestEmpty(
        `${PUBLIC_API_BASE_URL}/api/v1/admin/listening-devices/${encodeURIComponent(id)}`,
        {
          method: 'DELETE',
          redirect: 'error',
          headers: { Authorization: `Bearer ${session.token}` },
        },
      )
    : Promise.resolve({ kind: 'unauthorized' });
