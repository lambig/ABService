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

// The apply runs' teardown evaluates the whole configuration, so every value another resource keys
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

run "prepare_plan_dependencies" {
  command = apply
  plan_options {
    target = [aws_acm_certificate.cloudfront, aws_cloudfront_response_headers_policy.security, aws_lambda_function.static_page_404]
  }
}

// The snapshot identifiers only matter at creation (changes afterwards are ignored so the instances are
// not rebuilt by accident), so both are asserted while the instances do not exist yet.
run "permanent_instance_can_be_rebuilt_from_a_snapshot" {
  command = plan
  plan_options {
    target = [aws_db_instance.main]
  }
  variables {
    db_main_snapshot_identifier = "abservice-db-restored-2026-09-16"
    db_deletion_protection      = false
  }

  assert {
    condition     = aws_db_instance.main.snapshot_identifier == "abservice-db-restored-2026-09-16" && aws_db_instance.main.deletion_protection == false
    error_message = "Rebuilding the permanent instance takes the snapshot and needs protection turned off explicitly."
  }
}

run "restored_instance_can_come_from_a_snapshot" {
  command = plan
  plan_options {
    target = [aws_db_instance.restored]
  }
  variables {
    db_restore_snapshot_identifier = "abservice-db-2026-09-16"
  }

  assert {
    condition     = aws_db_instance.restored[0].snapshot_identifier == "abservice-db-2026-09-16" && length(aws_db_instance.restored[0].restore_to_point_in_time) == 0
    error_message = "A snapshot identifier must restore the instance from that snapshot, not from a point in time."
  }
}

// By default there is one database, it cannot be destroyed by accident, and the backend reads it.
run "one_protected_instance_by_default" {
  command = apply
  plan_options {
    target = [aws_ssm_parameter.db_host, aws_iam_role_policy.app]
  }

  assert {
    condition     = aws_db_instance.main.deletion_protection == true
    error_message = "The permanent instance must refuse deletion unless the operator turns protection off."
  }
  assert {
    condition     = length(aws_db_instance.restored) == 0
    error_message = "No restored instance exists until a restore point is given."
  }
  assert {
    condition     = aws_ssm_parameter.db_host.value == aws_db_instance.main.address
    error_message = "The backend must read the permanent instance by default."
  }
}

// Confirmed images are append-only: the backend may delete only what is still pending acceptance, so a
// database restored to an earlier point always finds the images it references.
run "backend_cannot_delete_confirmed_assets" {
  command = apply
  plan_options {
    target = [aws_iam_role_policy.app]
  }

  assert {
    condition     = !contains(one([for s in jsondecode(aws_iam_role_policy.app.policy).Statement : s if s.Sid == "AssetBucketAccess"]).Action, "s3:DeleteObject")
    error_message = "The general asset statement must not grant DeleteObject."
  }
  assert {
    condition     = endswith(one([for s in jsondecode(aws_iam_role_policy.app.policy).Statement : s if s.Sid == "AssetPendingDelete"]).Resource, "/pending/*")
    error_message = "DeleteObject must be limited to the pending prefix."
  }
  assert {
    condition     = length([for s in jsondecode(aws_iam_role_policy.app.policy).Statement : s if s.Sid == "AssetPendingDelete"]) == 1
    error_message = "Exactly one statement grants the pending delete."
  }
}

// A restore point creates a second instance next to the permanent one, in the same network position,
// without moving the backend yet.
run "restore_point_creates_a_second_instance" {
  command = apply
  plan_options {
    target = [aws_db_instance.restored, aws_ssm_parameter.db_host]
  }
  variables {
    db_restore_to_time = "2026-09-16T00:00:00Z"
  }

  assert {
    condition     = length(aws_db_instance.restored) == 1
    error_message = "A restore point must create the restored instance."
  }
  assert {
    condition     = aws_db_instance.restored[0].db_subnet_group_name == aws_db_instance.main.db_subnet_group_name && aws_db_instance.restored[0].vpc_security_group_ids == aws_db_instance.main.vpc_security_group_ids
    error_message = "The restored instance must sit in the same subnet group and security group as the permanent one."
  }
  assert {
    condition     = aws_db_instance.restored[0].restore_to_point_in_time[0].source_db_instance_identifier == aws_db_instance.main.identifier && aws_db_instance.restored[0].restore_to_point_in_time[0].restore_time == "2026-09-16T00:00:00Z"
    error_message = "The restored instance must be restored from the permanent instance at the given time."
  }
  assert {
    condition     = aws_db_instance.restored[0].deletion_protection == false && aws_db_instance.restored[0].skip_final_snapshot == true
    error_message = "The restored instance is temporary: removable, and without a final snapshot."
  }
  assert {
    condition     = aws_db_instance.restored[0].password == random_password.db.result
    error_message = "The restored instance must take the generated password so Parameter Store stays right."
  }
  assert {
    condition     = aws_ssm_parameter.db_host.value == aws_db_instance.main.address
    error_message = "Creating the restored instance must not move the backend by itself."
  }
}

// Switching the backend is a separate, explicit step.
run "backend_follows_db_active" {
  command = apply
  plan_options {
    target = [aws_ssm_parameter.db_host]
  }
  variables {
    db_restore_to_time = "2026-09-16T00:00:00Z"
    db_active          = "restored"
  }

  assert {
    condition     = aws_ssm_parameter.db_host.value == aws_db_instance.restored[0].address && aws_ssm_parameter.db_host.value != aws_db_instance.main.address
    error_message = "With db_active = restored the backend must read the restored instance."
  }
}

run "restored_cannot_be_active_without_a_restore_point" {
  command = plan
  plan_options {
    target = [aws_ssm_parameter.db_host]
  }
  variables {
    db_active = "restored"
  }

  expect_failures = [aws_ssm_parameter.db_host]
}

run "one_restore_point_at_a_time" {
  command = plan
  plan_options {
    target = [aws_db_instance.restored]
  }
  variables {
    db_restore_to_time             = "2026-09-16T00:00:00Z"
    db_restore_snapshot_identifier = "abservice-db-2026-09-16"
  }

  expect_failures = [aws_db_instance.restored]
}
