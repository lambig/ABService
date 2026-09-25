import assert from 'node:assert/strict';
import test from 'node:test';
import { createJsonFetcher } from '../src/lib/api/json-fetcher.ts';

test('bulk callers retain every result without overlapping response bodies', async () => {
  let active = 0;
  let maximum = 0;
  const starts = [];
  const releases = [];
  const request = createJsonFetcher('https://example.test', async (url) => {
    active += 1;
    maximum = Math.max(maximum, active);
    starts.push(url);
    return {
      ok: true,
      json: () =>
        new Promise((resolve) => {
          releases.push(() => {
            active -= 1;
            resolve({ url });
          });
        }),
    };
  });
  const paths = Array.from({ length: 150 }, (_, index) => `/items/${index}`);
  const results = Promise.all(paths.map((path) => request(path)));
  for (const [index] of paths.entries()) {
    await new Promise((resolve) => setImmediate(resolve));
    assert.equal(starts.length, index + 1);
    releases[index]();
  }
  assert.deepEqual(
    await results,
    paths.map((path) => ({ url: `https://example.test${path}` })),
  );
  assert.equal(maximum, 1);
});

test('HTTP failure waits for non-empty response body cancellation before the next request', async (t) => {
  const cancellation = Promise.withResolvers();
  t.after(() => cancellation.resolve());
  const events = [];
  const body = new ReadableStream({
    start(controller) {
      controller.enqueue(new TextEncoder().encode('service unavailable'));
    },
    cancel() {
      events.push('cancel-start');
      return cancellation.promise.then(() => events.push('cancel-end'));
    },
  });
  const request = createJsonFetcher('https://example.test', async (url) => {
    events.push(url);
    return url.endsWith('/first')
      ? new Response(body, { status: 503 })
      : Response.json({ retained: true });
  });
  const failed = assert.rejects(request('/first'), /GET \/first.*HTTP 503/);
  const following = request('/next');
  await new Promise((resolve) => setImmediate(resolve));
  assert.deepEqual(events, ['https://example.test/first', 'cancel-start']);
  cancellation.resolve();
  await failed;
  assert.deepEqual(await following, { retained: true });
  assert.deepEqual(events, [
    'https://example.test/first',
    'cancel-start',
    'cancel-end',
    'https://example.test/next',
  ]);
});

for (const failure of ['http', 'network', 'json']) {
  test(`${failure} failure rejects its caller and releases the next request`, async () => {
    let calls = 0;
    const request = createJsonFetcher('https://example.test', async () => {
      calls += 1;
      if (calls === 1) {
        if (failure === 'network') throw new Error('connection failed');
        return failure === 'http'
          ? new Response(null, { status: 503 })
          : new Response('invalid json');
      }
      return Response.json({ retained: true });
    });
    const failed = request('/first');
    const following = request('/next');
    await assert.rejects(failed, failure === 'http' ? /GET \/first.*HTTP 503/ : undefined);
    assert.deepEqual(await following, { retained: true });
    assert.equal(calls, 2);
  });
}
