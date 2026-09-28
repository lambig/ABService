import { getPlaybackItems, parseManifest } from 'abservice-installation';
import type { InstallationManifest } from 'abservice-installation';
import type {
  DistributionClient,
  DistributionConnection,
  DistributionError,
  DistributionResult,
} from './index';

/* The message carries only the classification, so an escaped Failure cannot disclose a token or URL. */
class Failure extends Error {
  constructor(
    readonly code: DistributionError,
    readonly status?: number,
  ) {
    super(code);
  }
}
const fail = (code: DistributionError, status?: number): never => {
  throw new Failure(code, status);
};
const capture = <T>(
  signal: AbortSignal,
  action: () => Promise<T>,
): Promise<DistributionResult<T>> =>
  action().then(
    (value) => ({ kind: 'ok', value }),
    (error: unknown) =>
      error instanceof Failure
        ? {
            kind: 'error',
            error: error.code,
            ...(error.status === undefined ? {} : { status: error.status }),
          }
        : { kind: 'error', error: signal.aborted ? 'aborted' : 'network' },
  );

type Source = 'api' | 'content';
/* 404 means different things per endpoint; the caller names it. */
const rejected = (
  response: Response,
  source: Source,
  notFound: DistributionError,
): never =>
  fail(
    response.status >= 500
      ? 'server'
      : source === 'content'
        ? 'source-rejected'
        : ((
            {
              401: 'unauthorized',
              403: 'forbidden',
              404: notFound,
            } as Readonly<Partial<Record<number, DistributionError>>>
          )[response.status] ?? 'invalid-response'),
    response.status,
  );
const checkAbort = (signal: AbortSignal): void =>
  signal.aborted ? fail('aborted') : undefined;
const request = async (
  send: typeof fetch,
  url: string,
  headers: Readonly<Record<string, string>>,
  signal: AbortSignal,
): Promise<Response> => {
  checkAbort(signal);
  try {
    /* Credentials travel only in the Authorization header; cookies and redirects are never followed. */
    return await send(url, {
      headers,
      signal,
      cache: 'no-store',
      credentials: 'omit',
      redirect: 'error',
    });
  } catch {
    return fail(signal.aborted ? 'aborted' : 'network');
  }
};
const read = async <T>(
  signal: AbortSignal,
  body: () => Promise<T>,
  malformed: DistributionError,
): Promise<T> => {
  try {
    return await body();
  } catch {
    return fail(signal.aborted ? 'aborted' : malformed);
  }
};
const isRecord = (value: unknown): value is Readonly<Record<string, unknown>> =>
  typeof value === 'object' && value !== null;
const resolvedUrl = (value: unknown, assetId: string): string =>
  isRecord(value) &&
  value.assetId === assetId &&
  typeof value.url === 'string' &&
  /^https?:\/\//.test(value.url)
    ? value.url
    : fail('invalid-response');
const audioAssetIds = (manifest: InstallationManifest): readonly string[] =>
  getPlaybackItems(manifest).map((item) => item.audioAssetId);
const presentationAssetIds = (
  manifest: InstallationManifest,
): readonly string[] => [
  ...manifest.presentationAssetIds,
  ...manifest.albums.flatMap((album) =>
    'artworkAssetId' in album && album.artworkAssetId !== undefined
      ? [album.artworkAssetId]
      : [],
  ),
];

export const buildClient = ({
  token,
  apiBase = '',
  assetBase = '/assets',
  fetch: send = fetch,
}: DistributionConnection): DistributionClient => {
  const api = {
    Authorization: `Bearer ${token}`,
    Accept: 'application/json',
  } as const;
  const json = async (
    path: string,
    notFound: DistributionError,
    signal: AbortSignal,
  ): Promise<unknown> => {
    const response = await request(send, `${apiBase}${path}`, api, signal);
    return response.ok
      ? read(
          signal,
          () => response.json() as Promise<unknown>,
          'invalid-response',
        )
      : rejected(response, 'api', notFound);
  };
  const content = async (url: string, signal: AbortSignal): Promise<Blob> => {
    const response = await request(send, url, {}, signal);
    return response.ok
      ? read(signal, () => response.blob(), 'network')
      : rejected(response, 'content', 'source-rejected');
  };
  const privateAudio = async (
    assetId: string,
    signal: AbortSignal,
  ): Promise<Blob> => {
    /* Resolved immediately before use: a signed URL expires, and it is never stored. */
    const resolved = await json(
      `/api/v1/listening/package/assets/${encodeURIComponent(assetId)}/url`,
      'not-distributed',
      signal,
    );
    return content(resolvedUrl(resolved, assetId), signal);
  };
  return {
    package: (signal) =>
      capture(signal, async () => {
        const parsed = parseManifest(
          await json('/api/v1/listening/package', 'unavailable', signal),
        );
        return parsed.kind === 'manifest'
          ? parsed.manifest
          : fail(
              parsed.kind === 'unsupported-schema'
                ? 'unsupported-schema'
                : 'invalid-response',
            );
      }),
    asset: (manifest, assetId, signal) =>
      capture(signal, async () => {
        const known = manifest.assets.some(
          (asset) => asset.assetId === assetId,
        );
        return known && audioAssetIds(manifest).includes(assetId)
          ? privateAudio(assetId, signal)
          : known && presentationAssetIds(manifest).includes(assetId)
            ? content(`${assetBase}/${encodeURIComponent(assetId)}`, signal)
            : fail('unsupported-asset');
      }),
  };
};
