import { afterEach, describe, expect, it, vi } from 'vitest';
import { REQUEST_TIMEOUT_MS, requestEmpty, requestJson } from './http';

const reply =
  (response: Response): typeof fetch =>
  () =>
    Promise.resolve(response);
const problem = {
  type: 'about:blank',
  title: 'Invalid input',
  status: 400,
  errors: [{ field: 'tracks[0].title', message: '必須', code: 'REQUIRED' }],
};

describe('API response boundary', () => {
  it.each([400, 409])('HTTP %sでProblem Detailsとfieldを保持する', async (status) => {
    const result = await requestJson('/test', {}, reply(Response.json(problem, { status })));
    expect(result).toMatchObject({ kind: 'failed', reason: 'http', status, problem });
  });
  it.each([401, 403])('HTTP %sを認証失敗へ分ける', async (status) => {
    expect(await requestJson('/test', {}, reply(Response.json(problem, { status })))).toMatchObject(
      { kind: 'unauthorized', status, problem },
    );
  });
  it('HTMLのエラー本文でもHTTP番号を失わない', async () => {
    expect(
      await requestJson('/test', {}, reply(new Response('<html>error</html>', { status: 502 }))),
    ).toMatchObject({ kind: 'failed', reason: 'http', status: 502 });
  });
  it('通信失敗をHTTPエラーと区別する', async () => {
    const disconnected: typeof fetch = () => Promise.reject(new Error('offline'));
    expect(await requestJson('/test', {}, disconnected)).toMatchObject({
      kind: 'failed',
      reason: 'network',
    });
  });
  it('204専用経路は空本文で成功する', async () => {
    expect(await requestEmpty('/test', {}, reply(new Response(null, { status: 204 })))).toEqual({
      kind: 'ok',
      value: undefined,
    });
  });
  it('JSONが必須なら204は応答契約違反', async () => {
    expect(
      await requestJson('/test', {}, reply(new Response(null, { status: 204 }))),
    ).toMatchObject({ kind: 'failed', reason: 'invalid-response' });
  });
  it('正常なJSONを渡す', async () => {
    expect(await requestJson('/test', {}, reply(Response.json({ id: '1' })))).toEqual({
      kind: 'ok',
      value: { id: '1' },
    });
  });
  it('不正なerrorsをフォーム用契約へcastしない', async () => {
    expect(
      await requestJson(
        '/test',
        {},
        reply(Response.json({ errors: [{ field: 1 }] }, { status: 400 })),
      ),
    ).not.toHaveProperty('problem');
  });
});

describe('通信の待機期限と中断', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('応答しない要求を30秒で中断し、再送せず遅い成功も捨てる', async () => {
    vi.useFakeTimers();
    const pending = Promise.withResolvers<Response>();
    const fetcher = vi.fn<typeof fetch>().mockReturnValue(pending.promise);
    const result = requestJson('/test', { method: 'POST' }, fetcher);
    await vi.advanceTimersByTimeAsync(REQUEST_TIMEOUT_MS);
    expect(await result).toMatchObject({ kind: 'failed', reason: 'timeout' });
    expect(fetcher.mock.calls[0]?.[1]?.signal?.aborted).toBe(true);
    pending.resolve(Response.json({ done: true }));
    expect(await result).toMatchObject({ kind: 'failed', reason: 'timeout' });
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);
  });

  it.each([200, 503])('HTTP %iのヘッダー後に本文が止まっても待機を終える', async (status) => {
    vi.useFakeTimers();
    const response = new Response(new ReadableStream(), { status });
    const result = requestJson('/test', {}, reply(response));
    await vi.advanceTimersByTimeAsync(REQUEST_TIMEOUT_MS);
    expect(await result).toMatchObject({ kind: 'failed', reason: 'timeout' });
  });

  it('呼び出し側の中断でも待機を終え、期限タイマーを除去する', async () => {
    vi.useFakeTimers();
    const controller = new AbortController();
    const fetcher = vi.fn<typeof fetch>().mockReturnValue(new Promise(() => undefined));
    const result = requestEmpty('/test', { signal: controller.signal }, fetcher);
    controller.abort();
    expect(await result).toMatchObject({ kind: 'failed', reason: 'aborted' });
    expect(fetcher.mock.calls[0]?.[1]?.signal?.aborted).toBe(true);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('開始前に中断済みなら送信しない', async () => {
    const fetcher = vi.fn<typeof fetch>();
    expect(await requestJson('/test', { signal: AbortSignal.abort() }, fetcher)).toMatchObject({
      kind: 'failed',
      reason: 'aborted',
    });
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('正常終了後は期限タイマーを除去する', async () => {
    vi.useFakeTimers();
    await requestJson('/test', {}, reply(Response.json({})));
    expect(vi.getTimerCount()).toBe(0);
  });
});
