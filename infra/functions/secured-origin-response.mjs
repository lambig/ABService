import { buildSecurityHeaders } from '../headers/build-security-headers.mjs';
import { renderStaticNotFound, loadS3Page } from './static-page-404.mjs';

// Identify the behavior by its configured origin, never by a viewer Host or an
// unnormalized URI. Viewer-response functions don't run on origin 4xx/5xx.
export async function secureOriginResponse(event, loadPage, config) {
  const request = event.Records[0].cf.request;
  const domain = request.origin?.s3?.domainName ?? request.origin?.custom?.domainName;
  const kind = Object.hasOwn(config.origins, domain) ? config.origins[domain] : undefined;
  if (!kind) throw new Error('Unexpected response origin');
  const response = ['public', 'admin'].includes(kind)
    ? await renderStaticNotFound(event, loadPage)
    : event.Records[0].cf.response;
  const headers = { ...response.headers };
  delete headers['x-robots-tag'];
  for (const [key, value] of Object.entries(buildSecurityHeaders(config.security, kind, config.noindex[kind]))) {
    headers[key.toLowerCase()] = [{ key, value }];
  }
  return { ...response, headers };
}

let configuration;
export async function handler(event) {
  configuration ??= import('node:fs/promises').then(async (fs) =>
    JSON.parse(await fs.readFile(new URL('./security-config.json', import.meta.url), 'utf8')));
  return secureOriginResponse(event, loadS3Page, await configuration);
}
