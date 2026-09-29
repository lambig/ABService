/* eslint-disable functional/immutable-data -- The fake fetch records each call so tests can inspect what was sent. */
import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import type { InstallationManifest } from 'abservice-installation';
import { parseManifest } from 'abservice-installation';
import { createDistributionClient, parseListenerToken } from './index';
import type { DistributionResult, ListenerToken } from './index';

/** backend の配布応答と同じ見本。 */
const example: unknown = JSON.parse(
  readFileSync(
    new URL(
      '../../installation/fixtures/manifest-v3.example.json',
      import.meta.url,
    ),
    'utf8',
  ),
);
const manifest = ((): InstallationManifest => {
  const parsed = parseManifest(example);
  return parsed.kind === 'manifest'
    ? parsed.manifest
    : ((): never => {
        throw new Error('the example must be a valid manifest');
      })();
})();
const audio = '0192f8a0-0000-7000-8000-000000000001';
const artwork = '0192f8a0-0000-7000-8000-000000000010.png';
const secret = `abs_device_${'a'.repeat(64)}`;
const token = parseListenerToken(secret) as ListenerToken;
const signed = `https://audio.example.test/audio/verified/${audio}.flac?X-Amz-Signature=secret-signature`;

type Call = Readonly<{ url: string; init: RequestInit | undefined }>;
type Route = (url: string) => Response | Error;
const json = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
const problem = (status: number): Response =>
  new Response(
    JSON.stringify({ type: 'urn:abservice:error:X', title: 'X', status }),
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
const bytes = (text: string): Response => new Response(new Blob([text]));
const urlOf = (input: RequestInfo | URL): string =>
  typeof input === 'string'
    ? input
    : input instanceof URL
      ? input.href
      : input.url;
const routes =
  (overrides: Readonly<Record<string, () => Response | Error>> = {}): Route =>
  (url) =>
    (
      overrides[url] ??
      {
        '/api/v1/listening/package': () => json(example),
        [`/api/v1/listening/package/assets/${audio}/url`]: () =>
          json({
            assetId: audio,
            url: signed,
            expiresAt: '2026-01-01T00:10:00Z',
          }),
        [signed]: () => bytes('flac'),
        [`/assets/${encodeURIComponent(artwork)}`]: () => bytes('png'),
      }[url] ??
      (() => new Response(null, { status: 599 }))
    )();
const connect = (route: Route = routes()) => {
  const calls: Call[] = [];
  const client = createDistributionClient({
    token,
    fetch: (input, init) => {
      const url = urlOf(input);
      calls.push({ url, init });
      const response = route(url);
      return response instanceof Error
        ? Promise.reject(response)
        : Promise.resolve(response);
    },
  });
  return { client, calls };
};
const signal = () => new AbortController().signal;
const text = async (result: DistributionResult<Blob>) =>
  result.kind === 'ok' ? result.value.text() : result;
const disclosed = (value: unknown) => {
  const serialized = JSON.stringify(value);
  expect(serialized).not.toContain(secret);
  expect(serialized).not.toContain('secret-signature');
};

describe('listener token', () => {
  it('accepts only the issued shape', () => {
    expect(parseListenerToken(secret)).toBe(secret);
    for (const input of [
      '',
      `abs_device_${'a'.repeat(63)}`,
      `abs_device_${'A'.repeat(64)}`,
      ` ${secret}`,
      `abs_admin_${'a'.repeat(64)}`,
    ]) {
      expect(parseListenerToken(input), input).toBeUndefined();
    }
  });
});

describe('package', () => {
  it('sends the listener token and returns the strictly parsed manifest', async () => {
    const { client, calls } = connect();
    const result = await client.package(signal());
    expect(result).toEqual({ kind: 'ok', value: manifest });
    expect(calls).toHaveLength(1);
    expect(calls[0]?.init).toMatchObject({
      headers: { Authorization: `Bearer ${secret}` },
      cache: 'no-store',
      credentials: 'omit',
      redirect: 'error',
    });
  });

  it('uses the configured API base', async () => {
    const calls: string[] = [];
    await createDistributionClient({
      token,
      apiBase: 'https://listening.example.test',
      fetch: (input) => {
        calls.push(urlOf(input));
        return Promise.resolve(json(example));
      },
    }).package(signal());
    expect(calls).toEqual([
      'https://listening.example.test/api/v1/listening/package',
    ]);
  });

  it('classifies API responses so the caller can choose the next step', async () => {
    const cases: readonly (readonly [() => Response | Error, unknown])[] = [
      [() => problem(401), { error: 'unauthorized', status: 401 }],
      [() => problem(403), { error: 'forbidden', status: 403 }],
      [() => problem(404), { error: 'unavailable', status: 404 }],
      [() => problem(400), { error: 'invalid-response', status: 400 }],
      [() => problem(503), { error: 'server', status: 503 }],
      [() => new TypeError('Failed to fetch'), { error: 'network' }],
      [
        () => new Response('<html>', { status: 200 }),
        { error: 'invalid-response' },
      ],
      [
        () => json({ ...(example as object), unknown: true }),
        { error: 'invalid-response' },
      ],
      [
        () => json({ ...(example as object), schemaVersion: 99 }),
        { error: 'unsupported-schema' },
      ],
    ];
    for (const [response, expected] of cases) {
      const { client } = connect(
        routes({ '/api/v1/listening/package': response }),
      );
      const result = await client.package(signal());
      expect(result).toEqual({ kind: 'error', ...(expected as object) });
      disclosed(result);
    }
  });
});

describe('asset', () => {
  it('resolves a signed URL right before fetching audio, without sending credentials to the storage', async () => {
    const { client, calls } = connect();
    expect(await text(await client.asset(manifest, audio, signal()))).toBe(
      'flac',
    );
    expect(calls.map((call) => call.url)).toEqual([
      `/api/v1/listening/package/assets/${audio}/url`,
      signed,
    ]);
    expect(calls[0]?.init?.headers).toMatchObject({
      Authorization: `Bearer ${secret}`,
    });
    expect(calls[1]?.init).toMatchObject({
      headers: {},
      credentials: 'omit',
      cache: 'no-store',
    });
  });

  it('fetches presentation assets from the public delivery path without the token', async () => {
    const { client, calls } = connect();
    expect(await text(await client.asset(manifest, artwork, signal()))).toBe(
      'png',
    );
    expect(calls.map((call) => call.url)).toEqual([
      `/assets/${encodeURIComponent(artwork)}`,
    ]);
    expect(calls[0]?.init?.headers).toEqual({});
  });

  it('refuses assets the manifest does not reference as audio or presentation', async () => {
    const orphan = {
      ...manifest,
      assets: [
        ...manifest.assets,
        { ...manifest.assets[0], assetId: 'orphan' },
      ],
    } as InstallationManifest;
    for (const assetId of ['orphan', 'absent']) {
      const { client, calls } = connect();
      expect(await client.asset(orphan, assetId, signal())).toEqual({
        kind: 'error',
        error: 'unsupported-asset',
      });
      expect(calls).toHaveLength(0);
    }
  });

  it('classifies URL resolution and storage failures without disclosing the token or signed URL', async () => {
    const url = `/api/v1/listening/package/assets/${audio}/url`;
    const cases: readonly (readonly [
      Readonly<Record<string, () => Response | Error>>,
      unknown,
    ])[] = [
      [{ [url]: () => problem(401) }, { error: 'unauthorized', status: 401 }],
      [
        { [url]: () => problem(404) },
        { error: 'not-distributed', status: 404 },
      ],
      [
        { [url]: () => json({ assetId: 'other', url: signed }) },
        { error: 'invalid-response' },
      ],
      [
        { [url]: () => json({ assetId: audio, url: 'javascript:alert(1)' }) },
        { error: 'invalid-response' },
      ],
      [
        { [signed]: () => new Response('expired', { status: 403 }) },
        { error: 'source-rejected', status: 403 },
      ],
      [
        { [signed]: () => new Response(null, { status: 500 }) },
        { error: 'server', status: 500 },
      ],
      [
        { [signed]: () => new TypeError('CORS rejected') },
        { error: 'network' },
      ],
    ];
    for (const [overrides, expected] of cases) {
      const { client } = connect(routes(overrides));
      const result = await client.asset(manifest, audio, signal());
      expect(result).toEqual({ kind: 'error', ...(expected as object) });
      disclosed(result);
    }
    const { client } = connect(
      routes({
        [`/assets/${encodeURIComponent(artwork)}`]: () =>
          new Response(null, { status: 404 }),
      }),
    );
    expect(await client.asset(manifest, artwork, signal())).toEqual({
      kind: 'error',
      error: 'source-rejected',
      status: 404,
    });
  });
});

describe('cancellation', () => {
  it('reports aborted before and during a request', async () => {
    const controller = new AbortController();
    controller.abort('cancelled by user');
    const { client, calls } = connect();
    expect(await client.package(controller.signal)).toEqual({
      kind: 'error',
      error: 'aborted',
    });
    expect(await client.asset(manifest, audio, controller.signal)).toEqual({
      kind: 'error',
      error: 'aborted',
    });
    expect(calls).toHaveLength(0);
    const during = new AbortController();
    const aborting = connect((url) => {
      during.abort();
      return url === signed
        ? new DOMException('aborted', 'AbortError')
        : routes()(url);
    });
    expect(await aborting.client.asset(manifest, audio, during.signal)).toEqual(
      {
        kind: 'error',
        error: 'aborted',
      },
    );
  });
});
