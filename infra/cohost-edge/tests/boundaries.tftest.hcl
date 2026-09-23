mock_provider "aws" {}
mock_provider "aws" { alias = "us_east_1" }
override_data {
  target = data.aws_s3_bucket.assets
  values = { id = "example-assets", arn = "arn:aws:s3:::example-assets", bucket_regional_domain_name = "example-assets.s3.us-east-1.amazonaws.com" }
}
override_resource {
  target = aws_s3_bucket.frontend["public"]
  values = { id = "example-public", arn = "arn:aws:s3:::example-public", bucket_regional_domain_name = "example-public.s3.us-east-1.amazonaws.com" }
}
override_resource {
  target = aws_s3_bucket.frontend["admin"]
  values = { id = "example-admin", arn = "arn:aws:s3:::example-admin", bucket_regional_domain_name = "example-admin.s3.us-east-1.amazonaws.com" }
}
override_resource {
  target = aws_s3_bucket.frontend["releases"]
  values = { id = "example-releases", arn = "arn:aws:s3:::example-releases" }
}
override_resource {
  target = aws_iam_role.static_page_404
  values = { arn = "arn:aws:iam::123456789012:role/example-edge" }
}
override_resource {
  target = aws_lambda_function.static_page_404
  values = { qualified_arn = "arn:aws:lambda:us-east-1:123456789012:function:example-edge:1" }
}
variables {
  region              = "us-east-1"
  account_id          = "123456789012"
  name                = "example"
  assets_bucket       = "example-assets"
  backend_domain      = "origin.example.invalid"
  origin_verify_token = "deliberately-invalid-test-verifier-only"
}
run "disabled_preparation" {
  command = plan
  assert {
    condition     = !aws_cloudfront_distribution.main.enabled && length(aws_cloudfront_distribution.main.aliases) == 0 && length(aws_cloudfront_distribution.main.ordered_cache_behavior) == 3
    error_message = "Preparation must not enable delivery or aliases and must retain all four routes."
  }
  assert {
    condition     = alltrue([for b in concat(tolist(aws_cloudfront_distribution.main.default_cache_behavior), tolist(aws_cloudfront_distribution.main.ordered_cache_behavior)) : b.response_headers_policy_id == null && length(b.forwarded_values) == 0 && length(b.lambda_function_association) == 1 && length([for f in b.function_association : f if f.event_type == "viewer-response"]) == 1])
    error_message = "All routes require error and cache-hit security headers without incompatible legacy policies."
  }
  assert {
    condition     = alltrue([for b in aws_cloudfront_distribution.main.ordered_cache_behavior : b.cache_policy_id == "4135ea2d-6df8-44a3-9df3-4b5a84be39ad" && b.origin_request_policy_id == "b689b0a8-53d0-40ab-baf2-68738e2966ac" && contains(b.allowed_methods, "POST") && contains(b.allowed_methods, "DELETE") if b.path_pattern == "/api/*"])
    error_message = "API must forward authentication and writes without caching."
  }
  assert {
    condition     = toset([for o in aws_cloudfront_distribution.main.origin : o.origin_id]) == toset(["public", "admin", "assets", "api"]) && local.header_noindex.public && local.header_noindex.admin
    error_message = "Release archives must never be served, and preview/admin HTML must stay noindex."
  }
  assert {
    condition     = alltrue([for f in aws_cloudfront_function.security_response : length(f.code) < 10000])
    error_message = "Generated CloudFront Functions must fit the code-size limit."
  }
}
run "reject_unverified_enablement" {
  command = plan
  variables { enabled = true }
  expect_failures = [aws_cloudfront_distribution.main]
}
run "reject_plaintext_enabled_origin" {
  command = plan
  variables {
    enabled            = true
    free_plan_verified = true
    backend_protocol   = "http-only"
  }
  expect_failures = [aws_cloudfront_distribution.main]
}
run "reject_alias_without_certificate" {
  command = plan
  variables { aliases = ["example.invalid"] }
  expect_failures = [aws_cloudfront_distribution.main]
}
run "reject_wrong_certificate_region" {
  command = plan
  variables { viewer_certificate_arn = "arn:aws:acm:ap-northeast-1:123456789012:certificate/example" }
  expect_failures = [var.viewer_certificate_arn]
}
run "reject_short_verifier" {
  command = plan
  variables { origin_verify_token = "invalid" }
  expect_failures = [var.origin_verify_token]
}
