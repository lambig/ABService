import { readFileSync } from 'node:fs';

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
  return {
    'Content-Security-Policy': config.policies[kind],
    'Strict-Transport-Security': `max-age=${config.hstsMaxAge}`,
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': config.referrerPolicy,
    'X-Frame-Options': ['api', 'assets'].includes(kind) ? 'DENY' : config.frameOptions,
    ...(['admin', 'api'].includes(kind) ? { 'X-Robots-Tag': 'noindex, nofollow' } : {}),
  };
};
