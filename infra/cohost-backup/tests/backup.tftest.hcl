mock_provider "aws" {}
mock_provider "archive" {}

override_resource {
  target = aws_s3_bucket.backup
  values = { arn = "arn:aws:s3:::example-backup-bucket", id = "example-backup-bucket" }
}
override_resource {
  target = aws_iam_role.monitor
  values = { arn = "arn:aws:iam::123456789012:role/example-monitor" }
}
override_resource {
  target = aws_sns_topic.backup
  values = { arn = "arn:aws:sns:us-east-1:123456789012:example-backup-alarms" }
}
override_resource {
  target = aws_lambda_function.monitor
  values = { arn = "arn:aws:lambda:us-east-1:123456789012:function:example-monitor" }
}
override_resource {
  target = aws_cloudwatch_event_rule.monitor
  values = { arn = "arn:aws:events:us-east-1:123456789012:rule/example-monitor" }
}

variables {
  aws_region         = "us-east-1"
  account_id         = "123456789012"
  bucket_name        = "example-backup-bucket"
  assets_bucket_name = "example-assets-bucket"
  alarm_email        = "operator@example.invalid"
}

run "safe_before_first_backup" {
  command = plan
  assert {
    condition     = !aws_cloudwatch_metric_alarm.backup.actions_enabled && aws_cloudwatch_event_rule.monitor.state == "DISABLED"
    error_message = "Do not enable before a real backup and subscription confirmation."
  }
  assert {
    condition     = aws_s3_bucket_versioning.backup.versioning_configuration[0].status == "Enabled" && !aws_s3_bucket.backup.force_destroy
    error_message = "Keep backup versions and refuse force deletion."
  }
  assert {
    condition     = aws_s3_bucket_public_access_block.backup.block_public_acls && aws_s3_bucket_public_access_block.backup.block_public_policy && aws_s3_bucket_public_access_block.backup.ignore_public_acls && aws_s3_bucket_public_access_block.backup.restrict_public_buckets
    error_message = "Block every form of public access."
  }
  assert {
    condition = alltrue([for s in jsondecode(aws_iam_policy.writer.policy).Statement :
    alltrue([for a in s.Action : !contains(["s3:GetObject", "s3:GetObjectVersion", "s3:DeleteObject", "s3:DeleteObjectVersion"], a)])])
    error_message = "Writer must not read or delete backups."
  }
}

run "enabled_monitor_detects_its_own_absence" {
  command = plan
  variables { monitoring_enabled = true }
  assert {
    condition     = aws_cloudwatch_metric_alarm.backup.actions_enabled && aws_cloudwatch_event_rule.monitor.state == "ENABLED"
    error_message = "Enable the schedule and notifications together."
  }
  assert {
    condition     = aws_cloudwatch_metric_alarm.backup.treat_missing_data == "breaching" && aws_cloudwatch_metric_alarm.backup.datapoints_to_alarm == 2 && aws_cloudwatch_metric_alarm.backup.evaluation_periods == 3
    error_message = "Stopped checker must fail closed without requiring every period to arrive."
  }
}

run "reject_shared_asset_bucket" {
  command = plan
  variables { assets_bucket_name = "example-backup-bucket" }
  expect_failures = [var.assets_bucket_name]
}
