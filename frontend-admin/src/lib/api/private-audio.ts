import { sha256 } from '@noble/hashes/sha2.js';
import { PUBLIC_API_BASE_URL } from 'astro:env/client';
import { isCurrentSession, type AdminSession } from '$lib/credentials';
import { requestJson, type ApiResult } from './http';
import type { components } from './schema';

export type Registration = components['schemas']['AudioRegistrationResponse'];
export type Crossfade = components['schemas']['AlbumCrossfadeResponse'];
export type Fingerprint = Readonly<{ byteLength: number; sha256: string }>;
export type PendingAudio = Fingerprint & Readonly<{ audioId: string }>;
export const MAX_AUDIO_BYTES = 256 * 1024 * 1024;
const ROOT = '/api/v1/admin/private-audio/registrations';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/u;
const STATES = ['PENDING', 'INSPECTED', 'CONFIRMED', 'EXPIRED', 'ABANDONED'];
const object = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === 'object';
export const pendingAudio = (value: unknown): value is PendingAudio =>
  object(value) &&
  typeof value['audioId'] === 'string' &&
  UUID.test(value['audioId']) &&
  typeof value['sha256'] === 'string' &&
  /^[0-9a-f]{64}$/u.test(value['sha256']) &&
  typeof value['byteLength'] === 'number' &&
  Number.isInteger(value['byteLength']) &&
  value['byteLength'] > 0 &&
  value['byteLength'] <= MAX_AUDIO_BYTES;
const validRegistration = (value: unknown): value is Registration =>
  object(value) &&
  typeof value['audioId'] === 'string' &&
  UUID.test(value['audioId']) &&
  typeof value['state'] === 'string' &&
  STATES.includes(value['state']) &&
  typeof value['createdAt'] === 'string' &&
  Number.isFinite(Date.parse(value['createdAt'])) &&
  typeof value['expiresAt'] === 'string' &&
  Number.isFinite(Date.parse(value['expiresAt'])) &&
  (['PENDING', 'EXPIRED'].includes(value['state'])
    ? value['metadata'] === null
    : object(value['metadata']) &&
      pendingAudio({ ...value['metadata'], audioId: value['audioId'] }));
const invalid = (): ApiResult<never> => ({
  kind: 'failed',
  reason: 'invalid-response',
  message: '登録状態の応答を確認できません。再送せず状態を確認してください。',
});

const call = <T>(
  session: AdminSession,
  path: string,
  init: RequestInit = {},
  timeoutMs = 30_000,
): Promise<ApiResult<T>> =>
  isCurrentSession(session) && Date.parse(session.expiresAt) > Date.now()
    ? requestJson<T>(
        `${PUBLIC_API_BASE_URL}${path}`,
        {
          ...init,
          redirect: 'error',
          headers: {
            ...Object.fromEntries(new Headers(init.headers)),
            Authorization: `Bearer ${session.token}`,
          },
        },
        fetch,
        timeoutMs,
      )
    : Promise.resolve({ kind: 'unauthorized' });

export const registration = async (
  session: AdminSession,
  audioId?: string,
  init?: RequestInit,
  timeoutMs?: number,
): Promise<ApiResult<Registration>> => {
  const result = await call<unknown>(
    session,
    audioId === undefined ? ROOT : `${ROOT}/${audioId}`,
    init,
    timeoutMs,
  );
  return result.kind === 'ok'
    ? validRegistration(result.value) &&
      [audioId === undefined, result.value.audioId === audioId?.split('/')[0]].some(Boolean)
      ? { kind: 'ok', value: result.value }
      : invalid()
    : result;
};
export const matches = (row: Registration, expected: PendingAudio): boolean =>
  row.audioId === expected.audioId &&
  row.metadata?.sha256 === expected.sha256 &&
  row.metadata.byteLength === expected.byteLength;

/** Bounded chunks avoid allocating a complete large FLAC in browser memory. */
export const fingerprint = async (file: File, signal: AbortSignal): Promise<Fingerprint> => {
  const digest = sha256.create();
  const read = async (offset: number): Promise<string> => {
    signal.throwIfAborted();
    return offset >= file.size
      ? Array.from(digest.digest(), (byte) => byte.toString(16).padStart(2, '0')).join('')
      : await file
          .slice(offset, offset + 65536)
          .arrayBuffer()
          .then(async (bytes) => {
            digest.update(new Uint8Array(bytes));
            return read(offset + bytes.byteLength);
          });
  };
  return file.size > 0 && file.size <= MAX_AUDIO_BYTES
    ? { byteLength: file.size, sha256: await read(0) }
    : Promise.reject(new Error('空でない256MiB以下のFLACを選択してください。'));
};

export const crossfade = async (
  session: AdminSession,
  albumId: string,
  replacement?: Readonly<{ audioId: string; expectedRevision: number }>,
): Promise<ApiResult<Crossfade>> => {
  const result = await call<unknown>(
    session,
    `/api/v1/admin/albums/${encodeURIComponent(albumId)}/listening-audio/crossfade`,
    replacement === undefined
      ? {}
      : {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(replacement),
        },
  );
  return result.kind === 'ok'
    ? object(result.value) &&
      result.value['albumId'] === albumId &&
      result.value['kind'] === 'album-crossfade' &&
      [
        result.value['audioId'] === null,
        typeof result.value['audioId'] === 'string' && UUID.test(result.value['audioId']),
      ].some(Boolean) &&
      typeof result.value['revision'] === 'number' &&
      Number.isInteger(result.value['revision']) &&
      result.value['revision'] >= 0
      ? { kind: 'ok', value: result.value as Crossfade }
      : invalid()
    : result;
};

export const uploadAudio = (
  session: AdminSession,
  audioId: string,
  file: File,
  signal: AbortSignal,
): Promise<ApiResult<Registration>> =>
  registration(
    session,
    `${audioId}/content`,
    { method: 'PUT', headers: { 'Content-Type': 'audio/flac' }, body: file, signal },
    600_000,
  );
export const confirmAudio = (
  session: AdminSession,
  audioId: string,
): Promise<ApiResult<Registration>> =>
  registration(session, `${audioId}/confirm`, { method: 'POST' }, 180_000);

export const connectionReady = async (): Promise<boolean> => {
  const result = await requestJson<unknown>('/__audio-connection', { redirect: 'error' });
  return result.kind === 'ok' && object(result.value) && result.value['kind'] === 'ssm-audio';
};
