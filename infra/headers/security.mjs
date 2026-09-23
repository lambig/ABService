import { readFileSync } from 'node:fs';
import { buildSecurityHeaders } from './build-security-headers.mjs';

// Local E2E has separate ports for the site, API and MinIO. Only these exact
// origins are substituted; Terraform supplies same-origin and one S3 host.
export const securityHeaders = (kind, { apiOrigin, imageOrigin, uploadOrigin }) => {
  const origins = [apiOrigin, imageOrigin, uploadOrigin].map((value) => new URL(value).origin);
  const substitutions = {
    api_sources: `'self' ${origins[0]}`,
    image_sources: `'self' data: ${origins[1]}`,
    upload_origin: origins[2],
  };
  const source = readFileSync(new URL('./security.json', import.meta.url), 'utf8');
  const config = JSON.parse(source.replace(/\$\{([a-z_]+)\}/gu, (_, key) => substitutions[key]));
  return buildSecurityHeaders(config, kind, ['admin', 'api'].includes(kind));
};
