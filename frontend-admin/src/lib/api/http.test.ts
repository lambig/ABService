import { describe, expect, it } from 'vitest';
import { requestEmpty, requestJson } from './http';

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
