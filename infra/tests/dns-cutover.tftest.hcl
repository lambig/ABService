mock_provider "aws" {
  mock_data "aws_availability_zones" {
    defaults = { names = ["ap-northeast-1a", "ap-northeast-1c"] }
  }
}
mock_provider "aws" {
  alias = "us_east_1"
}
mock_provider "random" {}
mock_provider "tls" {
  mock_data "tls_certificate" {
    defaults = { certificates = [{ sha1_fingerprint = "0000000000000000000000000000000000000000" }] }
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
}

// Terraform 1.9 mock overrides become known at apply, unlike ACM plan-time values.
// Seed the mocked certificate and policy ID before planning the full stack.
// The policy ID must be known so plan assertions can compare its behavior bindings.
run "prepare_plan_dependencies" {
  command = apply
  plan_options {
    target = [aws_acm_certificate.cloudfront, aws_cloudfront_response_headers_policy.noindex]
  }
}

run "prepare_apex" {
  command = plan

  assert {
    condition     = aws_cloudfront_distribution.main.default_cache_behavior[0].response_headers_policy_id == aws_cloudfront_response_headers_policy.noindex.id
    error_message = "Default preparation must attach noindex to public pages."
  }
  assert {
    condition = alltrue([
      for path in ["/admin*", "/api/*"] :
      one([for behavior in aws_cloudfront_distribution.main.ordered_cache_behavior : behavior if behavior.path_pattern == path]).response_headers_policy_id == aws_cloudfront_response_headers_policy.noindex.id
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
    condition     = aws_cloudfront_distribution.main.default_cache_behavior[0].response_headers_policy_id == null
    error_message = "Search publication must remove the public noindex policy."
  }
  assert {
    condition = alltrue([
      for path in ["/admin*", "/api/*"] :
      one([for behavior in aws_cloudfront_distribution.main.ordered_cache_behavior : behavior if behavior.path_pattern == path]).response_headers_policy_id == aws_cloudfront_response_headers_policy.noindex.id
    ])
    error_message = "Admin and API must retain noindex regardless of public indexing."
  }

  assert {
    condition = alltrue([
      for item in one(aws_cloudfront_response_headers_policy.noindex.custom_headers_config).items :
      item.header == "X-Robots-Tag" && item.value == "noindex, nofollow" && item.override
    ]) && length(one(aws_cloudfront_response_headers_policy.noindex.custom_headers_config).items) == 1
    error_message = "The retained policy must enforce X-Robots-Tag noindex, nofollow."
  }
}
