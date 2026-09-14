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
// Seed only the mocked certificate before planning the full stack.
run "prepare_certificate" {
  command = apply
  plan_options {
    target = [aws_acm_certificate.cloudfront]
  }
}

run "prepare_apex" {
  command = plan

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
