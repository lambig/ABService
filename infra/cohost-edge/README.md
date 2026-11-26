# Independent CloudFront edge

This opt-in root references an existing asset bucket and origin DNS name. It owns
only public/admin/release artifact buckets, CloudFront, WAF, edge functions and
their narrowly scoped roles. It never creates compute, a database, DNS records,
an origin certificate, or a second asset bucket/policy. Keep actual deployment
values and backend configuration in private operational configuration.

Use a separate encrypted remote state key. The origin verifier is sensitive but
is still present in Terraform state and saved plans; supply it through protected
temporary input and keep those files out of Git and logs.

## Deployment sequence

1. Start with `enabled=false`, no aliases, and indexing disabled. Review the plan
   against the intended account and existing resource ownership before applying.
2. Read back the PricingPlanManager subscription: `FREE`, `ACTIVE`, and exactly
   this distribution and WebACL. The CloudFormation stack owns only that
   subscription; Terraform owns the associated resources. There is no paid-tier
   fallback. A failed subscription must leave delivery disabled.
3. Prepare a valid publicly trusted TLS certificate on the API origin, restrict
   its firewall to CloudFront origin-facing ranges, and verify the application's
   origin-header enforcement. Keep PostgreSQL and health/management endpoints
   private. HTTPS is required before enabling delivery.
4. In the existing asset bucket's owning root, grant this distribution OAC read
   access to published assets only and configure the required upload CORS origins.
   Do not create a competing bucket-policy resource in this root.
5. Build and publish public/admin artifacts with the existing release tools. The
   release archive bucket has no CloudFront origin or read grant.
6. Set `free_plan_verified=true` only after the readback, then enable the
   distribution. Test all four paths, successful and error responses, signed
   upload/CORS, authorization/query forwarding, cache behavior, withdrawal,
   invalidation and origin bypass rejection before DNS cutover. Keep preview
   HTML noindex. Supply an issued us-east-1 ACM certificate before adding aliases.

The verification input is an operator assertion, not an automatic subscription
health check. Recheck the subscription when changing resources. Route53 is not
attached to this subscription. Lambda@Edge and other excluded services require
separate cost accounting. Disabling delivery does not remove resources or their
charges. Subscription deletion can schedule cancellation at the billing-period
boundary; read back actual status rather than infer it from stack deletion.

Managed cache policies keep public/admin/API uncached and cache immutable assets.
`backend_response_timeout` preserves the existing 30-second API origin wait by
default; an explicit integer up to 60 seconds is supported by this configuration
and its pinned provider. It applies to the shared API origin, not the S3 origins.
For private audio, verify the distribution quota and measure the real upload,
inspection and storage timings before choosing this value. A longer Nginx wait
does not override CloudFront's limit. A 504 or lost response does not establish
that saving failed: query the registration and use the confirmation/recovery
contract rather than blindly uploading again. Keep the audio bucket out of CDN
origins/OAC and validate anonymous rejection independently.

Private FLAC uploads also need an explicit `private_audio_upload_enabled=true`
WAF opt-in. The managed `SizeRestrictions_BODY` rule otherwise rejects valid
large files before they reach the origin. The opt-in changes only that rule to
Count and blocks its label again unless the request is an exact `PUT` to
`/api/v1/admin/private-audio/registrations/{lowercase UUID}/content` with
`Content-Type: audio/flac`. Other methods, paths and content types retain the
managed size rejection. Both managed rule groups and every other rule still run;
this is neither an early Allow nor an authentication exception. The origin must
still enforce administrator authorization, the feature flag, streaming 256MiB
limits and input deadlines. WAF inspects only its supported body prefix and
cannot validate a whole FLAC or enforce this larger upload limit.

Keep this opt-in false until the origin is ready, and restore it to false when
temporarily disabling upload acceptance. It does not enable the listening PWA
or grant access to stored audio. After applying, test a real FLAC through the
CDN, the unchanged non-audio size rejection, unauthenticated rejection and the
origin's over-limit rejection. Inspect the terminating WAF rule for any further
403; do not broadly exempt binary content from the remaining protections.
The label-and-exception pattern follows the
[AWS WAF upload guidance](https://repost.aws/knowledge-center/waf-upload-blocked-files).

The Free candidate uses `PriceClass_All`. Read back actual subscription admission;
a valid CloudFront configuration alone does not prove eligibility for Free.
Viewer-response functions cover normal/cache-hit responses; Lambda@Edge supplies
headers on origin errors and renders only static page 404s. WAF blocks and errors
generated by CloudFront before reaching an origin are outside that handler's
header guarantee. Do not claim those responses were tested from local unit tests.

`terraform init -backend=false`, `terraform validate` and `terraform test` check
the offline configuration. These tests use a mock AWS provider; this root's
archive is deferred until its bucket domains are known. CI checks the shared
handler package through the legacy root's fixture. After this root's real apply,
extract its `.terraform/static-page-404.zip` and run
`node infra/functions/check-edge-package.mjs /path/to/extracted-package` before
enabling delivery. Local checks do not replace AWS delivery acceptance.

## Offline listening route

`/offline-player*` is a separate behavior backed by the private admin bucket's
`/offline-player` origin path. Its viewer-request function defaults to
`listening_enabled=false`: direct HTML, worker and immutable asset URLs return
404 even when objects are already cached. Enabling it admits only the canonical
entry, worker and revisioned shell paths. Preview audio and distribution
fixtures are never published. The API retains its independent private-audio
feature flag and listener authorization.

The listening CSP permits same-origin modules/workers/fonts, local blob images
and playback, and exact `listening_audio_origins` for signed audio downloads.
Those values are the audio storage origins, while the audio bucket's
`private_audio_download_origins` contains the PWA's origin. Keep actual values
in private operations. The route stays noindex independently of public indexing.
Origin error headers use the trusted S3 origin path, not viewer input, to
distinguish listening from admin responses.

Apply this route with listening disabled before deploying its first artifacts.
Review the role's prefix permissions and CSP/CORS settings, deploy a verified
candidate, then explicitly enable and check HTML, SW scope/MIME, immutable
assets, API authorization, signed GET and offline preparation. Exercise an
update, interrupted delivery and rollback before accepting the release.
Disabling the edge route blocks future network requests; it does not erase
already installed offline shells or audio. Device credential revocation is
separate and does not retract already issued download URLs before expiration.

The local staged-shell browser check uses synthetic fixtures with the same
gate and CSP template. It validates shell preparation, offline restart, playback,
DSP worker and fonts; AWS propagation, real CORS and device performance remain
operational acceptance checks.
