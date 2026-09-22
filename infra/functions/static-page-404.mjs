// Associated only with the two static origins at origin-response. Missing S3
// objects must be 404 (scoped OAC ListBucket), never infer absence from a 403.
const MAX_PAGE_BYTES = 512 * 1024;
const isPage = (uri) => uri.endsWith('.html')
  && !/^\/(?:api|assets|_astro)(?:\/|$)/u.test(uri)
  && !uri.startsWith('/admin/_astro/');

const entityHeaders = new Set(['content-type', 'content-length', 'content-encoding', 'etag', 'last-modified', 'cache-control', 'content-range']);

const withBody = (response, body, method, status, contentType) => ({
  ...response,
  status,
  statusDescription: status === '404' ? 'Not Found' : 'Service Unavailable',
  // Via and Transfer-Encoding are read-only in origin-response. Preserve them.
  headers: {
    ...Object.fromEntries(Object.entries(response.headers).filter(([key]) => !entityHeaders.has(key))),
    'content-type': [{ key: 'Content-Type', value: contentType }],
    'content-length': [{ key: 'Content-Length', value: String(Buffer.byteLength(body)) }],
    'cache-control': [{ key: 'Cache-Control', value: 'no-store' }],
  },
  body: method === 'HEAD' ? '' : body,
  bodyEncoding: 'text',
});

// Inject only the storage boundary; the E2E server runs this same response logic.
export const renderStaticNotFound = async (event, loadPage) => {
  const { request, response } = event.Records[0].cf;
  if (response.status !== '404' || !request.origin?.s3
      || !['GET', 'HEAD'].includes(request.method) || !isPage(request.uri)) return response;
  try {
    const body = await loadPage(request);
    if (Buffer.byteLength(body) > MAX_PAGE_BYTES) throw new Error('404 page exceeds edge response budget');
    return withBody(response, body, request.method, '404', 'text/html; charset=utf-8');
  } catch {
    // A missing/broken error artifact is a delivery failure, not a successful
    // custom 404. Never emit storage details or retry via the viewer's URL.
    console.error('Static 404 artifact could not be read');
    return withBody(response, 'Service Unavailable', request.method, '503', 'text/plain; charset=utf-8');
  }
};

let storage;
export const loadS3Page = async (request) => {
  // Node.js 22 Lambda runtime supplies AWS SDK v3. No viewer credentials, Host,
  // query string, or path are passed to S3. Terraform packages the origin map.
  storage ??= Promise.all([import('@aws-sdk/client-s3'), import('node:fs/promises')]).then(async ([sdk, fs]) => ({
    sdk,
    config: JSON.parse(await fs.readFile(new URL('./origins.json', import.meta.url), 'utf8')),
  }));
  const { sdk, config } = await storage;
  return readErrorPage(request, config, sdk);
};

export const readErrorPage = async (request, config, sdk) => {
  const origin = config.origins[request.origin.s3.domainName];
  if (!origin || request.origin.s3.path) throw new Error('Unexpected static origin');
  const client = new sdk.S3Client({ region: config.region, maxAttempts: 1 });
  try {
    const object = await client.send(new sdk.GetObjectCommand({ Bucket: origin.bucket, Key: origin.key }), {
      abortSignal: AbortSignal.timeout(4000),
    });
    if (object.ContentLength > MAX_PAGE_BYTES || !object.ContentType?.startsWith('text/html')) {
      object.Body?.destroy();
      throw new Error('Invalid 404 artifact');
    }
    return await object.Body.transformToString('utf-8');
  } finally {
    client.destroy();
  }
};

export const handler = (event) => renderStaticNotFound(event, loadS3Page);
