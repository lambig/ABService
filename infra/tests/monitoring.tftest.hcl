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

override_resource {
  target = aws_sns_topic.alarms
  values = {
    arn = "arn:aws:sns:ap-northeast-1:123456789012:abservice-alarms"
  }
}

override_resource {
  target = aws_cloudwatch_log_group.backend
  values = {
    arn = "arn:aws:logs:ap-northeast-1:123456789012:log-group:/abservice/production/backend"
  }
}

variables {
  domain_name        = "example.invalid"
  alarm_email        = "alerts@example.invalid"
  log_retention_days = 14
}

// Known values for the notification target and the log group are needed before the alarms and the
// instance role can be asserted against them at plan time.
run "prepare_plan_dependencies" {
  command = apply
  plan_options {
    target = [aws_acm_certificate.cloudfront, aws_cloudfront_response_headers_policy.security, aws_lambda_function.static_page_404, aws_sns_topic.alarms, aws_cloudwatch_log_group.backend]
  }
}

run "alarms_before_cutover" {
  command = plan

  assert {
    condition = alltrue([
      for alarm in [
        aws_cloudwatch_metric_alarm.ec2_status_check,
        aws_cloudwatch_metric_alarm.ec2_cpu,
        aws_cloudwatch_metric_alarm.host_disk,
        aws_cloudwatch_metric_alarm.host_memory,
        aws_cloudwatch_metric_alarm.rds_free_storage,
        aws_cloudwatch_metric_alarm.rds_connections,
        aws_cloudwatch_metric_alarm.backend_errors,
        aws_cloudwatch_metric_alarm.cloudfront_5xx,
        aws_cloudwatch_metric_alarm.public_api_health,
      ] : alarm.alarm_actions == toset([aws_sns_topic.alarms.arn]) && alarm.ok_actions == toset([aws_sns_topic.alarms.arn])
    ])
    error_message = "Every alarm must notify the single topic on both alarm and recovery."
  }

  assert {
    condition     = aws_sns_topic_subscription.alarm_email.protocol == "email" && aws_sns_topic_subscription.alarm_email.topic_arn == aws_sns_topic.alarms.arn
    error_message = "The notification target is an email subscription on the alarm topic."
  }

  assert {
    condition     = aws_cloudwatch_log_group.backend.retention_in_days == 14
    error_message = "Log retention must follow the variable."
  }

  assert {
    condition     = aws_ssm_parameter.backend_log_group.value == aws_cloudwatch_log_group.backend.name
    error_message = "deploy.sh reads the log group name from Parameter Store; it must point at the real group."
  }

  assert {
    condition     = strcontains(aws_cloudwatch_log_metric_filter.backend_errors.pattern, "ERROR") && aws_cloudwatch_metric_alarm.backend_errors.metric_name == aws_cloudwatch_log_metric_filter.backend_errors.metric_transformation[0].name
    error_message = "The error alarm must read the metric the ERROR filter emits."
  }

  assert {
    condition     = aws_route53_health_check.public_api.type == "HTTPS" && aws_route53_health_check.public_api.resource_path == "/api/v1/albums" && aws_route53_health_check.public_api.port == 443
    error_message = "The synthetic check must exercise a public API route over HTTPS."
  }

  assert {
    condition     = aws_cloudwatch_metric_alarm.cloudfront_5xx.dimensions["Region"] == "Global"
    error_message = "CloudFront metrics are global."
  }
}

// The instance policy embeds ARNs that are unknown until apply (repository, buckets), so its
// statements can only be read after applying it against the mocked provider.
run "instance_role_scopes_observability" {
  command = apply
  plan_options {
    target = [aws_iam_role_policy.app]
  }

  // Terraform 1.9 evaluates every clause of a `for ... if` filter, so statements are first selected
  // by Sid alone and only then inspected (other statements carry a string Action or no Condition).
  assert {
    condition = (
      one([for statement in jsondecode(aws_iam_role_policy.app.policy).Statement : statement if statement.Sid == "WriteBackendLogs"]).Resource == "${aws_cloudwatch_log_group.backend.arn}:*"
      && toset(one([for statement in jsondecode(aws_iam_role_policy.app.policy).Statement : statement if statement.Sid == "WriteBackendLogs"]).Action) == toset(["logs:CreateLogStream", "logs:PutLogEvents"])
    )
    error_message = "The instance may write only to the backend log group."
  }

  assert {
    condition     = try(one([for statement in jsondecode(aws_iam_role_policy.app.policy).Statement : statement if statement.Sid == "PutHostMetrics"]).Condition.StringEquals["cloudwatch:namespace"], "") == "CWAgent"
    error_message = "The agent may publish metrics only into its own namespace."
  }
}

run "health_check_follows_cutover" {
  command = plan
  variables {
    dns_cutover_enabled = true
  }

  assert {
    condition     = aws_route53_health_check.public_api.fqdn == "example.invalid"
    error_message = "After cutover the public check must target the canonical domain."
  }
}
