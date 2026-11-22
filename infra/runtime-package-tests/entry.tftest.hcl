# AWS is mocked; archive_file must build the real zip from static-page-404.tf.
# Run separately for both cloudfront_free_compatible values, checking each zip
# before the next run overwrites it.
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

# Keep certificate validation keys known during mock teardown as well.
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

run "package_entry" {
  command = apply
  plan_options {
    target = [aws_acm_certificate.cloudfront, data.archive_file.static_page_404]
  }
}
