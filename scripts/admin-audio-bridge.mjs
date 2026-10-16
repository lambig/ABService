import { createServer, request as httpRequest } from 'node:http';
import { request as httpsRequest } from 'node:https';
import { Transform } from 'node:stream';

const UUID = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}';
const AUDIO = `/api/v1/admin/private-audio/registrations`;
const MAX_AUDIO = 256 * 1024 * 1024;
const route = (method, path) => {
  if (method === 'POST' && path === '/api/v1/admin/sessions') return 'api';
  if (method === 'DELETE' && path === '/api/v1/admin/sessions/current') return 'api';
  if (method === 'GET' && /^\/api\/v1\/admin\/albums(?:\/[a-zA-Z0-9-]+)?$/.test(path)) return 'api';
  if (['GET', 'PUT'].includes(method) && new RegExp(`^/api/v1/admin/albums/${UUID}/listening-audio/crossfade$`).test(path)) return 'api';
  if (method === 'POST' && path === AUDIO) return 'api';
  if (method === 'GET' && new RegExp(`^${AUDIO}/${UUID}$`).test(path)) return 'api';
  if (method === 'POST' && new RegExp(`^${AUDIO}/${UUID}/confirm$`).test(path)) return 'api';
  if (method === 'PUT' && new RegExp(`^${AUDIO}/${UUID}/content$`).test(path)) return 'upload';
  if (method === 'GET' && /^\/(?:admin(?:\/|$)|assets\/)[a-zA-Z0-9/_.-]*$/.test(path)
    && !path.split('/').includes('..')) return 'static';
  return null;
};
const reply = (response, status, message) => {
  if (response.writableEnded || response.destroyed) return;
  if (response.headersSent) { response.destroy(); return; }
  response.writeHead(status, { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' });
  response.end(message);
};

/**
 * Local-only adapter for an already established management tunnel. Never obtains credentials,
 * opens a tunnel, enables a feature, or substitutes administrator authorization. The caller owns
 * its lifetime and supplies the origin verification secret in memory. Only registration-related
 * API routes are forwarded; this is not a general HTTP proxy.
 */
export async function startAudioBridge({ siteOrigin, apiPort, originToken, port = 0, deadlineMs = 600_000 }) {
  const site = new URL(siteOrigin);
  if (site.protocol !== 'https:' || site.origin !== siteOrigin || site.username || site.password
    || !Number.isInteger(apiPort) || apiPort < 1 || apiPort > 65535
    || !Number.isInteger(port) || port < 0 || port > 65535
    || typeof originToken !== 'string' || !/^[\x21-\x7e]+$/.test(originToken)
    || !Number.isInteger(deadlineMs) || deadlineMs < 1 || deadlineMs > 600_000) throw new Error('Invalid bridge configuration');
  let authority;
  const server = createServer((request, response) => {
    // Reject rebinding and requests initiated by other sites before opening any upstream socket.
    const origin = request.headers.origin;
    if (request.headers.host !== authority || (origin !== undefined && origin !== `http://${authority}`)
      || ['cross-site', 'same-site'].includes(request.headers['sec-fetch-site'])
      || (!['GET', 'HEAD'].includes(request.method) && origin !== `http://${authority}`)) {
      reply(response, 403, 'この接続からの要求ではありません。'); return;
    }
    let url;
    try { url = new URL(request.url, `http://${authority}`); }
    catch { reply(response, 400, '受付できない経路です。'); return; }
    if (url.origin !== `http://${authority}` || !request.url.startsWith('/') || request.url.startsWith('//')
      || url.pathname.includes('%') || request.url.includes('\\') || request.url.split('?')[0].split('/').includes('..')) {
      reply(response, 400, '受付できない経路です。'); return;
    }
    if (request.method === 'GET' && url.pathname === '/__audio-connection') {
      response.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
      response.end(JSON.stringify({ kind: 'ssm-audio', maxBytes: MAX_AUDIO, deadlineMs })); return;
    }
    const kind = route(request.method, url.pathname);
    if (kind === null) { reply(response, 404, 'この接続は試聴用FLACの登録専用です。'); return; }
    if (kind === 'upload' && request.headers['content-type'] !== 'audio/flac') {
      reply(response, 415, 'FLACを選択してください。'); return;
    }
    const limit = kind === 'upload' ? MAX_AUDIO : 65536;
    const length = request.headers['content-length'];
    if (length !== undefined && (!/^\d+$/.test(length) || Number(length) > limit)) {
      reply(response, 413, '送信上限を超えています。'); return;
    }
    // Identify the adapter to the static site's normal edge policy without forwarding browser identity.
    const headers = kind === 'static' ? { 'Accept-Encoding': 'identity', 'User-Agent': 'ABService-Admin-Audio-Bridge/1.0' } : {
      'X-Origin-Verify': originToken,
      ...(request.headers.authorization ? { Authorization: request.headers.authorization } : {}),
      ...(request.headers['content-type'] ? { 'Content-Type': request.headers['content-type'] } : {}),
      ...(length ? { 'Content-Length': length } : {}),
    };
    // The request path must never participate in selecting an upstream authority.
    const destination = kind === 'static'
      ? { protocol: 'https:', hostname: site.hostname, port: site.port || 443 }
      : { protocol: 'http:', hostname: '127.0.0.1', port: apiPort };
    const upstream = (kind === 'static' ? httpsRequest : httpRequest)({
      ...destination, path: url.pathname + url.search, method: request.method, headers,
    }, (incoming) => {
      if (response.writableEnded || response.destroyed) { incoming.destroy(); return; }
      // Redirects could disclose a management token or escape the fixed remote site.
      if (incoming.statusCode >= 300 && incoming.statusCode < 400) {
        incoming.resume(); reply(response, 502, '接続先が変更されています。接続設定を確認してください。'); return;
      }
      const safe = Object.fromEntries(Object.entries(incoming.headers).filter(([name]) =>
        ['content-type', 'content-length', 'content-security-policy', 'x-content-type-options', 'x-frame-options'].includes(name)));
      response.writeHead(incoming.statusCode ?? 502, { ...safe, 'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer' });
      incoming.on('error', () => response.destroy());
      incoming.pipe(response);
    });
    const timer = setTimeout(() => {
      reply(response, 504, '待機期限を超えました。再送せず登録状態を確認してください。'); upstream.destroy();
    }, deadlineMs);
    upstream.on('error', () => reply(response, 502, '接続が切れました。再送せず登録状態を確認してください。'));
    response.on('close', () => { clearTimeout(timer); upstream.destroy(); });
    request.on('aborted', () => upstream.destroy());
    let bytes = 0;
    const bounded = new Transform({ transform(chunk, _encoding, callback) {
      bytes += chunk.length;
      if (bytes > limit) { reply(response, 413, '送信上限を超えています。'); callback(new Error('body limit')); }
      else callback(null, chunk);
    } });
    bounded.on('error', () => upstream.destroy());
    request.pipe(bounded).pipe(upstream);
  });
  server.requestTimeout = deadlineMs;
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(port, '127.0.0.1', resolve); });
  authority = `127.0.0.1:${server.address().port}`;
  return { url: `http://${authority}/admin/`, close: () => new Promise((resolve) => { server.close(resolve); server.closeAllConnections(); }) };
}
