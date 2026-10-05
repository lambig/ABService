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
    condition = length(aws_wafv2_web_acl.cloudfront.rule) == 2 && alltrue([
      for r in aws_wafv2_web_acl.cloudfront.rule : length(one(one(r.statement).managed_rule_group_statement).rule_action_override) == 0
    ])
    error_message = "Default WAF must retain both managed groups without body-size overrides."
  }
  assert {
    condition     = alltrue([for origin in aws_cloudfront_distribution.main.origin : one(origin.custom_origin_config).origin_read_timeout == 30 if origin.origin_id == "api"])
    error_message = "Keep the existing default API response wait."
  }
  assert {
    condition     = !aws_cloudfront_distribution.main.enabled && aws_cloudfront_distribution.main.price_class == "PriceClass_All" && length(aws_cloudfront_distribution.main.aliases) == 0 && length(aws_cloudfront_distribution.main.ordered_cache_behavior) == 4
    error_message = "Preparation must keep global delivery disabled, without aliases, and retain all five routes."
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
    condition     = toset([for o in aws_cloudfront_distribution.main.origin : o.origin_id]) == toset(["public", "admin", "assets", "api", "listening"]) && local.header_noindex.public && local.header_noindex.admin && local.header_noindex.listening
    error_message = "Release archives must never be served, and preview/admin HTML must stay noindex."
  }
  assert {
    condition     = alltrue([for f in aws_cloudfront_function.security_response : length(f.code) < 10000])
    error_message = "Generated CloudFront Functions must fit the code-size limit."
  }
  assert {
    condition     = strcontains(aws_cloudfront_function.listening_request.code, "var enabled = false;") && alltrue([for o in aws_cloudfront_distribution.main.origin : o.origin_path == "/offline-player" if o.origin_id == "listening"])
    error_message = "Listening must start closed and use only its private prefix."
  }
}
run "private_audio_body_size_opt_in" {
  command = plan
  variables { private_audio_upload_enabled = true }
  assert {
    condition = length(aws_wafv2_web_acl.cloudfront.rule) == 3 && alltrue([
      for r in aws_wafv2_web_acl.cloudfront.rule :
      length(r.action) == 0 && length(one(r.override_action).none) == 1 &&
      length(one(one(r.statement).managed_rule_group_statement).scope_down_statement) == 0
      if startswith(r.name, "AWSManagedRules")
    ])
    error_message = "Both managed groups must keep evaluating all requests; no early Allow or scope-down."
  }
  assert {
    condition = alltrue([
      for r in aws_wafv2_web_acl.cloudfront.rule :
      length(one(one(r.statement).managed_rule_group_statement).rule_action_override) == 1 &&
      one(one(one(r.statement).managed_rule_group_statement).rule_action_override).name == "SizeRestrictions_BODY" &&
      length(one(one(one(one(r.statement).managed_rule_group_statement).rule_action_override).action_to_use).count) == 1
      if r.name == "AWSManagedRulesCommonRuleSet"
      ]) && alltrue([
      for r in aws_wafv2_web_acl.cloudfront.rule : length(one(one(r.statement).managed_rule_group_statement).rule_action_override) == 0
      if r.name == "AWSManagedRulesKnownBadInputsRuleSet"
    ])
    error_message = "Only the CommonRuleSet body-size rule may be changed to Count."
  }
  assert {
    condition = alltrue([
      for r in aws_wafv2_web_acl.cloudfront.rule :
      r.priority == 2 && length(one(r.action).block) == 1 &&
      length(one(one(r.statement).and_statement).statement) == 2 &&
      length([for s in one(one(r.statement).and_statement).statement : s
        if try(one(s.label_match_statement).key == "awswaf:managed:aws:core-rule-set:SizeRestrictions_Body" && one(s.label_match_statement).scope == "LABEL", false)
      ]) == 1 &&
      length([for s in one(one(r.statement).and_statement).statement : s if length(s.not_statement) == 1]) == 1
      if r.name == "BodySizeExceptPrivateAudioUpload"
    ])
    error_message = "After both groups, restore body-size blocking using the exact AWS label AND the negated exception."
  }
  assert {
    condition = alltrue(flatten([
      for r in aws_wafv2_web_acl.cloudfront.rule : [
        for s in one(one(r.statement).and_statement).statement :
        length(one(one(one(s.not_statement).statement).and_statement).statement) == 3 &&
        length([for match in one(one(one(s.not_statement).statement).and_statement).statement : match
          if try(one(match.byte_match_statement).search_string == "PUT" && one(match.byte_match_statement).positional_constraint == "EXACTLY" && length(one(one(match.byte_match_statement).field_to_match).method) == 1 && one(one(match.byte_match_statement).text_transformation).type == "NONE", false)
        ]) == 1 &&
        length([for match in one(one(one(s.not_statement).statement).and_statement).statement : match
          if try(one(match.byte_match_statement).search_string == "audio/flac" && one(match.byte_match_statement).positional_constraint == "EXACTLY" && one(one(one(match.byte_match_statement).field_to_match).single_header).name == "content-type" && one(one(match.byte_match_statement).text_transformation).type == "NONE", false)
        ]) == 1 &&
        length([for match in one(one(one(s.not_statement).statement).and_statement).statement : match
          if try(one(match.regex_match_statement).regex_string == local.private_audio_upload_path && length(one(one(match.regex_match_statement).field_to_match).uri_path) == 1 && one(one(match.regex_match_statement).text_transformation).type == "NONE", false)
        ]) == 1
        if length(s.not_statement) == 1
      ] if r.name == "BodySizeExceptPrivateAudioUpload"
    ]))
    error_message = "Exception must require exact PUT AND the untransformed canonical path AND exact audio/flac."
  }
  assert {
    condition = can(regex(local.private_audio_upload_path, "/api/v1/admin/private-audio/registrations/12345678-1234-1234-1234-123456789abc/content")) && alltrue([
      for path in [
        "/api/v1/admin/private-audio/registrations",
        "/api/v1/admin/private-audio/registrations/12345678-1234-1234-1234-123456789abc/confirm",
        "/api/v1/admin/private-audio/registrations/12345678-1234-1234-1234-123456789abc/content/extra",
        "/prefix/api/v1/admin/private-audio/registrations/12345678-1234-1234-1234-123456789abc/content",
        "/api/v1/admin/private-audio/registrations/not-a-uuid/content",
        "/api/v1/admin/private-audio/registrations/12345678-1234-1234-1234-123456789ABC/content",
        "/api/v1/admin/private-audio/registrations/12345678-1234-1234-1234-123456789abc/%63ontent",
        "/api/v1/admin/articles", "/api/v1/assets/upload", "/offline-player/"
      ] : !can(regex(local.private_audio_upload_path, path))
    ])
    error_message = "Accept only a lowercase UUID content path; reject suffix, prefix, encoding and unrelated endpoints."
  }
  assert {
    condition     = !var.listening_enabled && strcontains(aws_cloudfront_function.listening_request.code, "var enabled = false;")
    error_message = "Upload opt-in must not open the listening PWA."
  }
}
run "explicit_listening_enablement" {
  command = plan
  variables {
    listening_enabled       = true
    listening_audio_origins = ["https://audio.example.invalid"]
  }
  assert {
    condition     = strcontains(aws_cloudfront_function.listening_request.code, "var enabled = true;") && strcontains(local.security_headers.policies.listening, "connect-src 'self' https://audio.example.invalid;") && !strcontains(local.security_headers.policies.public, "audio.example.invalid")
    error_message = "Listening opt-in and download CSP must not widen the public policy."
  }
}
run "reject_wildcard_audio_origin" {
  command = plan
  variables { listening_audio_origins = ["https://*.example.invalid"] }
  expect_failures = [var.listening_audio_origins]
}
run "reject_csp_injection" {
  command = plan
  variables { listening_audio_origins = ["https://audio.example.invalid; script-src *"] }
  expect_failures = [var.listening_audio_origins]
}
run "explicit_api_response_wait" {
  command = plan
  variables { backend_response_timeout = 60 }
  assert {
    condition     = alltrue([for origin in aws_cloudfront_distribution.main.origin : one(origin.custom_origin_config).origin_read_timeout == 60 if origin.origin_id == "api"])
    error_message = "The requested API wait must reach the custom origin."
  }
}
run "reject_unbounded_api_response_wait" {
  command = plan
  variables { backend_response_timeout = 61 }
  expect_failures = [var.backend_response_timeout]
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
