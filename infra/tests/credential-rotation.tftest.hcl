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

// The apply run's teardown evaluates the whole configuration, so every value another resource keys
// on (for_each, index) has to be known even though this test never asserts on them.
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

// Each credential regenerates only when its own rotation variable changes. The keeper is the
// declared trigger; without it a rotation would need `terraform apply -replace` by hand.
run "rotation_variables_drive_the_generators" {
  command = plan
  plan_options {
    target = [random_password.admin_api_key, random_password.db, random_password.origin_verify_token]
  }
  variables {
    admin_api_key_rotation       = "2026-09-16"
    db_password_rotation         = "initial"
    origin_verify_token_rotation = "2026-01-01"
  }

  assert {
    condition     = random_password.admin_api_key.keepers.rotation == "2026-09-16"
    error_message = "The admin API key must regenerate on its own rotation variable."
  }
  assert {
    condition     = random_password.db.keepers.rotation == "initial"
    error_message = "The DB password must regenerate on its own rotation variable."
  }
  assert {
    condition     = random_password.origin_verify_token.keepers.rotation == "2026-01-01"
    error_message = "The origin verification token must regenerate on its own rotation variable."
  }
  assert {
    condition     = length(distinct([random_password.admin_api_key.keepers.rotation, random_password.db.keepers.rotation, random_password.origin_verify_token.keepers.rotation])) == 3
    error_message = "The three credentials must not share one trigger; they are rotated one at a time."
  }
}

// The generated value must reach every supplier the runbook lists, so a rotation apply changes them
// together: Parameter Store for all three, RDS for the DB password, CloudFront for the origin token.
// Generated values are only known after apply, so this run applies against the mocked providers.
// The certificate, the headers policy and the 404 function are applied alongside so their overridden
// values are in state when the teardown plans the destroy of the whole configuration.
run "generated_values_reach_their_suppliers" {
  command = apply
  plan_options {
    target = [aws_ssm_parameter.admin_api_key, aws_ssm_parameter.db_password, aws_ssm_parameter.origin_verify_token, aws_db_instance.main, aws_acm_certificate.cloudfront, aws_cloudfront_response_headers_policy.security, aws_lambda_function.static_page_404]
  }

  assert {
    condition     = aws_ssm_parameter.admin_api_key.value == random_password.admin_api_key.result && aws_ssm_parameter.admin_api_key.type == "SecureString"
    error_message = "The admin API key parameter must carry the generated value as a SecureString."
  }
  assert {
    condition     = aws_ssm_parameter.db_password.value == random_password.db.result && aws_db_instance.main.password == random_password.db.result
    error_message = "RDS and Parameter Store must carry the same generated DB password."
  }
  assert {
    condition     = aws_ssm_parameter.origin_verify_token.value == random_password.origin_verify_token.result
    error_message = "The origin token parameter must carry the generated value."
  }
}
