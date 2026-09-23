# This prepares a compatible distribution; it does NOT subscribe to a plan.
variable "cloudfront_free_compatible" {
  description = "Use managed cache policies and edge security headers for flat-rate Free validation. Subscription and AWS acceptance are separate."
  type        = bool
  default     = false
}

locals {
  # AWS-managed policies, not custom policies (Free does not support those).
  managed_cache_optimized = "658327ea-f89d-4fab-a63d-7e88639e58f6"
  managed_cache_disabled  = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad"
  managed_origin_api      = "b689b0a8-53d0-40ab-baf2-68738e2966ac" # AllViewerExceptHostHeader
  edge_security_config = {
    security = local.security_headers
    noindex  = local.header_noindex
    origins = {
      (aws_s3_bucket.frontend_public.bucket_regional_domain_name) = "public"
      (aws_s3_bucket.frontend_admin.bucket_regional_domain_name)  = "admin"
      (aws_s3_bucket.assets.bucket_regional_domain_name)          = "assets"
      (aws_instance.backend.public_dns)                           = "api"
    }
  }
}

resource "aws_cloudfront_function" "security_response" {
  for_each = var.cloudfront_free_compatible ? toset(["public", "admin", "api", "assets"]) : toset([])
  name     = "${var.project_name}-${each.key}-security-response"
  runtime  = "cloudfront-js-2.0"
  comment  = "Shared security headers, including cached responses"
  publish  = true
  code = templatefile("${path.module}/functions/security-response.js.tftpl", {
    renderer = replace(file("${path.module}/headers/build-security-headers.mjs"), "export function", "function")
    config   = jsonencode(local.security_headers)
    kind     = jsonencode(each.key)
    noindex  = jsonencode(local.header_noindex[each.key])
  })
}

output "cloudfront_free_validation" {
  description = "Compatibility configuration only; does not certify or activate the billing plan."
  value = {
    enabled          = var.cloudfront_free_compatible
    distribution_arn = aws_cloudfront_distribution.main.arn
    web_acl_arn      = aws_wafv2_web_acl.cloudfront.arn
    hosted_zone_id   = data.aws_route53_zone.primary.zone_id
  }
}
