// Pure shared renderer: Terraform-generated edge functions and local delivery
// use the same header values. No I/O or runtime-specific APIs belong here.
export function buildSecurityHeaders(config, kind, noindex) {
  if (typeof config.policies[kind] !== 'string') throw new Error('Unknown response kind');
  const headers = {
    'Content-Security-Policy': config.policies[kind],
    'Strict-Transport-Security': `max-age=${config.hstsMaxAge}`,
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': config.referrerPolicy,
    'X-Frame-Options': ['api', 'assets'].includes(kind) ? 'DENY' : config.frameOptions,
  };
  // CloudFront's JS 2.0 runtime does not implement object spread syntax.
  if (noindex) headers['X-Robots-Tag'] = 'noindex, nofollow';
  return headers;
}
