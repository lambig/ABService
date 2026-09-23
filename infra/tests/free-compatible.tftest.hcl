mock_provider "aws" {
  mock_resource "aws_cloudfront_function" {
    defaults = { arn = "arn:aws:cloudfront::123456789012:function/test-function" }
  }
  mock_data "aws_availability_zones" {
    defaults = { names = ["ap-northeast-1a", "ap-northeast-1c"] }
  }
}
mock_provider "aws" {
  alias = "us_east_1"
}
# Use the real local archive provider to check the packaged Lambda entry point.
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
  cloudfront_free_compatible = true
  domain_name                = "example.invalid"
  alarm_email                = "alerts@example.invalid"
}


run "prepare_free_dependencies" {
  command = apply
  plan_options {
    target = [aws_acm_certificate.cloudfront, aws_lambda_function.static_page_404, aws_cloudfront_function.security_response, aws_wafv2_web_acl.cloudfront]
  }
  assert {
    condition     = alltrue([for f in aws_cloudfront_function.security_response : length(f.code) < 10000])
    error_message = "Generated viewer response functions must fit the code size limit."
  }
}

run "free_compatible_preserves_security" {
  command = apply
  plan_options {
    target = [aws_cloudfront_distribution.main]
  }
  assert {
    condition = length(aws_cloudfront_response_headers_policy.security) == 0 && alltrue([
      for behavior in concat(tolist(aws_cloudfront_distribution.main.default_cache_behavior), tolist(aws_cloudfront_distribution.main.ordered_cache_behavior)) :
      length(behavior.forwarded_values) == 0 && behavior.response_headers_policy_id == null &&
      length(behavior.lambda_function_association) == 1 &&
      alltrue([for edge in behavior.lambda_function_association : edge.event_type == "origin-response" && edge.lambda_arn == aws_lambda_function.static_page_404.qualified_arn]) &&
      length([for edge in behavior.function_association : edge if edge.event_type == "viewer-response"]) == 1
    ])
    error_message = "All four behaviors need error and cache-hit headers, with no legacy forwarding or custom headers policies."
  }
  assert {
    condition = alltrue([
      for behavior in aws_cloudfront_distribution.main.ordered_cache_behavior :
      behavior.cache_policy_id == "4135ea2d-6df8-44a3-9df3-4b5a84be39ad" &&
      behavior.origin_request_policy_id == "b689b0a8-53d0-40ab-baf2-68738e2966ac" &&
      contains(behavior.allowed_methods, "POST") && contains(behavior.allowed_methods, "DELETE")
      if behavior.path_pattern == "/api/*"
    ])
    error_message = "API must remain uncached and forward Authorization/query values using the managed policy."
  }
  assert {
    condition = alltrue([
      for behavior in concat(tolist(aws_cloudfront_distribution.main.default_cache_behavior), [for b in aws_cloudfront_distribution.main.ordered_cache_behavior : b if b.path_pattern == "/admin*"]) :
      behavior.cache_policy_id == "4135ea2d-6df8-44a3-9df3-4b5a84be39ad"
      ]) && alltrue([
      for behavior in aws_cloudfront_distribution.main.ordered_cache_behavior :
      behavior.cache_policy_id == "658327ea-f89d-4fab-a63d-7e88639e58f6" if behavior.path_pattern == "/assets/*"
    ]) && length(aws_cloudfront_distribution.main.custom_error_response) == 0
    error_message = "HTML must not acquire a minimum cache TTL; only immutable assets use CachingOptimized. API errors must not be rewritten."
  }
  assert {
    condition = (length(aws_wafv2_web_acl.cloudfront.rule) == 2 &&
      aws_cloudfront_distribution.main.web_acl_id == aws_wafv2_web_acl.cloudfront.arn &&
      length(aws_route53_record.root) == 0 && length(aws_route53_record.www) == 0 &&
    local.edge_security_config.noindex.public && local.edge_security_config.noindex.admin && local.edge_security_config.noindex.api)
    error_message = "Compatibility must retain WAF, noindex and the DNS cutover gate."
  }
  assert {
    condition = (aws_cloudfront_distribution.main.price_class == "PriceClass_All" &&
      length(aws_cloudfront_distribution.main.origin) == 4 &&
    length(aws_cloudfront_distribution.main.ordered_cache_behavior) == 3)
    error_message = "The candidate must use global delivery and stay within Free behavior limits."
  }
}

run "free_search_publication" {
  command = plan
  variables { public_indexing_enabled = true }
  assert {
    condition = (!local.edge_security_config.noindex.public &&
      local.edge_security_config.noindex.admin && local.edge_security_config.noindex.api &&
    !local.edge_security_config.noindex.assets)
    error_message = "Public indexing must not expose admin/API to indexing."
  }
}
