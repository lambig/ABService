/** Problem Detailsの検証エラー。fieldはAPI入力パスをそのまま保持する。 */
export type FieldError = Readonly<{ field: string; message: string; code?: string | null }>;

/** RFC 9457の標準項目とフォーム用errors拡張。 */
export type ProblemDetails = Readonly<{
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: readonly FieldError[];
}>;

/** 既存UIの3枝を維持し、原因とHTTP応答を追加情報として渡す。 */
export type ApiResult<T> =
  | Readonly<{ kind: 'ok'; value: T }>
  | Readonly<{ kind: 'unauthorized'; status?: number; problem?: ProblemDetails }>
  | Readonly<{
      kind: 'failed';
      message: string;
      reason?: 'network' | 'http' | 'invalid-response';
      status?: number;
      problem?: ProblemDetails;
    }>;

const record = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === 'object' && Object.getPrototypeOf(value) === Object.prototype;
const optionalString = (value: unknown): boolean =>
  [value === undefined, typeof value === 'string'].some(Boolean);
const fieldError = (value: unknown): value is FieldError =>
  record(value) &&
  typeof value['field'] === 'string' &&
  typeof value['message'] === 'string' &&
  [value['code'] === null, optionalString(value['code'])].some(Boolean);
const problemDetails = (value: unknown): value is ProblemDetails =>
  record(value) &&
  ['type', 'title', 'detail', 'instance'].every((key) => optionalString(value[key])) &&
  [
    value['status'] === undefined,
    typeof value['status'] === 'number' && Number.isInteger(value['status']),
  ].some(Boolean) &&
  [
    value['errors'] === undefined,
    Array.isArray(value['errors']) && value['errors'].every(fieldError),
  ].some(Boolean);

const unreadable = Symbol('unreadable');
const json = async (response: Response): Promise<unknown> =>
  (response.json() as Promise<unknown>).catch(() => unreadable);

const failure = async (response: Response): Promise<Exclude<ApiResult<never>, { kind: 'ok' }>> => {
  const body = await json(response);
  const problem = problemDetails(body) ? { problem: body } : {};
  return [401, 403].includes(response.status)
    ? { kind: 'unauthorized', status: response.status, ...problem }
    : {
        kind: 'failed',
        reason: 'http',
        status: response.status,
        message: `管理APIが失敗しました（HTTP ${String(response.status)}）。`,
        ...problem,
      };
};

const send = async <T>(
  url: string,
  init: RequestInit,
  decode: (response: Response) => Promise<ApiResult<T>>,
  fetcher: typeof fetch,
): Promise<ApiResult<T>> => {
  const response = await fetcher(url, init).catch(() => null);
  return response === null
    ? { kind: 'failed', reason: 'network', message: '管理APIへ接続できません。' }
    : response.ok
      ? decode(response)
      : failure(response);
};

/** JSON本体を必要とする操作。204をTに偽装しない。 */
export const requestJson = <T>(
  url: string,
  init: RequestInit,
  fetcher: typeof fetch = fetch,
): Promise<ApiResult<T>> =>
  send(
    url,
    init,
    async (response) => {
      const body = await json(response);
      return body === unreadable
        ? {
            kind: 'failed',
            reason: 'invalid-response',
            status: response.status,
            message: '管理APIの応答を読み取れません。',
          }
        : { kind: 'ok', value: body as T };
    },
    fetcher,
  );

/** 本体を返さない操作の専用経路。204成功時はJSON読み取りを行わない。 */
export const requestEmpty = (
  url: string,
  init: RequestInit,
  fetcher: typeof fetch = fetch,
): Promise<ApiResult<void>> =>
  send(
    url,
    init,
    (response) =>
      Promise.resolve(
        response.status === 204
          ? { kind: 'ok', value: undefined }
          : {
              kind: 'failed',
              reason: 'invalid-response',
              status: response.status,
              message: '管理APIの応答が204ではありません。',
            },
      ),
    fetcher,
  );
