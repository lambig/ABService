mock_provider "aws" {
  mock_data "aws_availability_zones" {
    defaults = { names = ["ap-northeast-1a", "ap-northeast-1c"] }
  }
}
mock_provider "aws" {
  alias = "us_east_1"
}
mock_provider "archive" {}
mock_provider "random" {}
mock_provider "tls" {
  mock_data "tls_certificate" {
    defaults = { certificates = [{ sha1_fingerprint = "0000000000000000000000000000000000000000" }] }
  }
}

override_resource {
  target = aws_iam_role.static_page_404
  values = {
    arn = "arn:aws:iam::123456789012:role/static-page-404"
  }
}

override_resource {
  target = aws_lambda_function.static_page_404
  values = {
    qualified_arn = "arn:aws:lambda:us-east-1:123456789012:function:static-page-404:1"
  }
}

override_resource {
  target = aws_acm_certificate.cloudfront
  values = {
    arn = "arn:aws:acm:us-east-1:123456789012:certificate/00000000-0000-0000-0000-000000000000"
    domain_validation_options = [{
      domain_name           = "example.invalid"
      resource_record_name  = "_verify.example.invalid"
      resource_record_type  = "CNAME"
      resource_record_value = "_verify.acm-validations.aws"
    }]
  }
}

variables {
  domain_name = "example.invalid"
  alarm_email = "alerts@example.invalid"
}

// Terraform 1.9 mock overrides become known at apply, unlike ACM plan-time values.
// Seed the mocked certificate and policy ID before planning the full stack.
// The policy ID must be known so plan assertions can compare its behavior bindings.
run "prepare_plan_dependencies" {
  command = apply
  plan_options {
    target = [aws_acm_certificate.cloudfront, aws_cloudfront_response_headers_policy.security, aws_lambda_function.static_page_404]
  }
}

run "prepare_apex" {
  command = plan

  assert {
    condition = alltrue([
      for policy in aws_cloudfront_response_headers_policy.security :
      one(policy.security_headers_config).content_security_policy[0].override &&
      length(one(policy.security_headers_config).content_security_policy[0].content_security_policy) <= 1783 &&
      one(policy.security_headers_config).content_type_options[0].override &&
      one(policy.security_headers_config).strict_transport_security[0].access_control_max_age_sec == 31536000 &&
      !one(policy.security_headers_config).strict_transport_security[0].include_subdomains &&
      !one(policy.security_headers_config).strict_transport_security[0].preload &&
      one(policy.security_headers_config).referrer_policy[0].referrer_policy == "strict-origin-when-cross-origin"
    ])
    error_message = "Every policy must enforce CSP, nosniff, scoped HSTS and referrer policy."
  }
  assert {
    condition = alltrue([
      for behavior in aws_cloudfront_distribution.main.ordered_cache_behavior :
      behavior.response_headers_policy_id == aws_cloudfront_response_headers_policy.security[lookup({ "/admin*" = "admin", "/api/*" = "api", "/assets/*" = "assets" }, behavior.path_pattern)].id
    ]) && length(aws_cloudfront_response_headers_policy.security["public"].custom_headers_config) == 1
    error_message = "All behaviors need security headers; public stays noindex before search publication."
  }
  assert {
    condition = (strcontains(local.security_headers.policies.admin, "https://${aws_s3_bucket.assets.bucket_regional_domain_name}") &&
      !strcontains(local.security_headers.policies.public, aws_s3_bucket.assets.bucket_regional_domain_name) &&
    alltrue([for kind in ["public", "admin"] : strcontains(local.security_headers.policies[kind], "https://w.soundcloud.com")]))
    error_message = "Only admin may connect to the upload bucket; both page kinds need the shared embed origin."
  }

  assert {
    condition = alltrue([
      for behavior in concat(
        tolist(aws_cloudfront_distribution.main.default_cache_behavior),
        [for behavior in aws_cloudfront_distribution.main.ordered_cache_behavior : behavior if behavior.path_pattern == "/admin*"]
        ) : length(behavior.lambda_function_association) == 1 && alltrue([
          for association in behavior.lambda_function_association :
          association.event_type == "origin-response" && association.lambda_arn == aws_lambda_function.static_page_404.qualified_arn && !association.include_body
      ])
    ])
    error_message = "Only the two static behaviors must use the versioned origin-response 404 handler."
  }
  assert {
    condition = alltrue([
      for behavior in aws_cloudfront_distribution.main.ordered_cache_behavior :
      length(behavior.lambda_function_association) == 0 && length(behavior.function_association) == 0
      if contains(["/api/*", "/assets/*"], behavior.path_pattern)
    ]) && length(aws_cloudfront_distribution.main.custom_error_response) == 0
    error_message = "API/asset errors must bypass HTML rewriting and distribution-wide custom errors."
  }
  assert {
    condition = alltrue([
      for policy in [data.aws_iam_policy_document.frontend_public_oac, data.aws_iam_policy_document.frontend_admin_oac] :
      length([for statement in policy.statement : statement if contains(statement.actions, "s3:ListBucket")]) == 1
    ]) && alltrue([for statement in data.aws_iam_policy_document.assets_oac.statement : !contains(statement.actions, "s3:ListBucket")])
    error_message = "Missing-key visibility belongs only to the two static OAC policies."
  }
  assert {
    condition     = toset(jsondecode(aws_iam_role_policy.static_page_404.policy).Statement[0].Resource) == toset(["${aws_s3_bucket.frontend_public.arn}/404.html", "${aws_s3_bucket.frontend_admin.arn}/admin/404.html"])
    error_message = "The edge role may read only the two fixed error documents."
  }

  assert {
    condition     = aws_cloudfront_distribution.main.default_cache_behavior[0].response_headers_policy_id == aws_cloudfront_response_headers_policy.security["public"].id
    error_message = "Default preparation must attach noindex to public pages."
  }
  assert {
    condition = alltrue([
      for path in ["/admin*", "/api/*"] :
      one([for behavior in aws_cloudfront_distribution.main.ordered_cache_behavior : behavior if behavior.path_pattern == path]).response_headers_policy_id == aws_cloudfront_response_headers_policy.security[path == "/admin*" ? "admin" : "api"].id
    ])
    error_message = "Admin and API must retain noindex regardless of public indexing."
  }

  assert {
    condition     = alltrue([for record in aws_route53_record.cert_validation : record.type == "CNAME"])
    error_message = "ACM validation record types must be CNAME, including preparation."
  }

  assert {
    condition     = length(aws_route53_record.root) == 0 && length(aws_route53_record.root_ipv6) == 0 && length(aws_route53_record.www) == 0
    error_message = "Preparation must not manage serving DNS records."
  }
  assert {
    condition     = aws_cloudfront_distribution.main.aliases == toset(["example.invalid"])
    error_message = "www must be an explicit deployment choice."
  }
}

run "prepare_www_certificate" {
  command = apply
  variables {
    serve_www = true
  }
  plan_options {
    target = [aws_acm_certificate.cloudfront]
  }
}

run "prepare_www" {
  command = plan
  variables {
    serve_www = true
  }
  assert {
    condition     = length(aws_route53_record.www) == 0 && length(aws_route53_record.root) == 0
    error_message = "Preparing www must not switch DNS."
  }
  assert {
    condition     = contains(aws_acm_certificate.cloudfront.subject_alternative_names, "www.example.invalid") && contains(aws_cloudfront_distribution.main.aliases, "www.example.invalid")
    error_message = "www needs both a certificate and a distribution alias."
  }
  assert {
    condition     = contains(one(aws_s3_bucket_cors_configuration.assets.cors_rule).allowed_origins, "https://www.example.invalid")
    error_message = "www admin uploads need the same origin in S3 CORS."
  }
}

run "cutover_both_families" {
  command = plan
  variables {
    serve_www           = true
    dns_cutover_enabled = true
  }
  assert {
    condition     = length(aws_route53_record.root) == 1 && length(aws_route53_record.root_ipv6) == 1 && length(aws_route53_record.www) == 2
    error_message = "Cutover must include A and AAAA for both enabled names."
  }
}

run "enable_public_indexing" {
  command = plan
  variables {
    serve_www               = true
    dns_cutover_enabled     = true
    public_indexing_enabled = true
  }

  assert {
    condition     = aws_cloudfront_distribution.main.default_cache_behavior[0].response_headers_policy_id == aws_cloudfront_response_headers_policy.security["public"].id && length(aws_cloudfront_response_headers_policy.security["public"].custom_headers_config) == 0
    error_message = "Search publication must retain security headers and remove only public noindex."
  }
  assert {
    condition = alltrue([
      for path in ["/admin*", "/api/*"] :
      one([for behavior in aws_cloudfront_distribution.main.ordered_cache_behavior : behavior if behavior.path_pattern == path]).response_headers_policy_id == aws_cloudfront_response_headers_policy.security[path == "/admin*" ? "admin" : "api"].id
    ])
    error_message = "Admin and API must retain noindex regardless of public indexing."
  }

  assert {
    condition = alltrue([
      for item in one(aws_cloudfront_response_headers_policy.security["admin"].custom_headers_config).items :
      item.header == "X-Robots-Tag" && item.value == "noindex, nofollow" && item.override
    ]) && length(one(aws_cloudfront_response_headers_policy.security["admin"].custom_headers_config).items) == 1
    error_message = "The retained policy must enforce X-Robots-Tag noindex, nofollow."
  }
}
