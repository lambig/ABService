import { expect, test, vi } from 'vitest';
import { createJsonFetcher } from '../src/lib/api/json-fetcher.ts';

const nextTurn = () => new Promise((resolve) => setImmediate(resolve));

test('bulk callers retain every result without overlapping response bodies', async () => {
  const paths = Array.from({ length: 150 }, (_, index) => `/items/${index}`);
  const releases = paths.map(() => Promise.withResolvers());
  const fetcher = vi.fn(async (url) => ({
    ok: true,
    json: () => releases[paths.indexOf(new URL(url).pathname)].promise.then(() => ({ url })),
  }));
  const request = createJsonFetcher('https://example.test', fetcher);
  const results = Promise.all(paths.map((path) => request(path)));
  for (const [index, release] of releases.entries()) {
    await nextTurn();
    expect(fetcher).toHaveBeenCalledTimes(index + 1);
    release.resolve();
  }
  expect(await results).toEqual(paths.map((path) => ({ url: `https://example.test${path}` })));
});

test('HTTP failure waits for non-empty response body cancellation before the next request', async () => {
  const cancellation = Promise.withResolvers();
  const events = vi.fn();
  const body = new ReadableStream({
    start(controller) {
      controller.enqueue(new TextEncoder().encode('service unavailable'));
    },
    cancel() {
      events('cancel-start');
      return cancellation.promise.then(() => events('cancel-end'));
    },
  });
  const request = createJsonFetcher('https://example.test', async (url) => {
    events(url);
    return url.endsWith('/first')
      ? new Response(body, { status: 503 })
      : Response.json({ retained: true });
  });
  const failed = expect(request('/first')).rejects.toThrow(/GET \/first.*HTTP 503/);
  const following = request('/next');
  try {
    await nextTurn();
    expect(events.mock.calls).toEqual([['https://example.test/first'], ['cancel-start']]);
  } finally {
    cancellation.resolve();
  }
  await failed;
  expect(await following).toEqual({ retained: true });
  expect(events.mock.calls).toEqual([
    ['https://example.test/first'],
    ['cancel-start'],
    ['cancel-end'],
    ['https://example.test/next'],
  ]);
});

test.each([
  ['http', () => Promise.resolve(new Response(null, { status: 503 })), /GET \/first.*HTTP 503/],
  ['network', () => Promise.reject(new Error('connection failed')), /connection failed/],
  ['json', () => Promise.resolve(new Response('invalid json')), /JSON/],
])('%s failure rejects its caller and releases the next request', async (_kind, fail, message) => {
  const fetcher = vi
    .fn()
    .mockImplementationOnce(fail)
    .mockResolvedValue(Response.json({ retained: true }));
  const request = createJsonFetcher('https://example.test', fetcher);
  const failed = request('/first');
  const following = request('/next');
  await expect(failed).rejects.toThrow(message);
  expect(await following).toEqual({ retained: true });
  expect(fetcher).toHaveBeenCalledTimes(2);
});
