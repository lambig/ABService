# Independent root: applying it must not create the legacy EC2/RDS or change DNS.
terraform {
  required_version = ">= 1.9.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.7"
    }
  }
  backend "s3" {}
}

provider "aws" {
  region              = var.aws_region
  allowed_account_ids = [var.account_id]
}

locals {
  runs       = "${var.prefix}/runs/"
  assets_arn = "arn:aws:s3:::${var.assets_bucket_name}"
  backup_arn = "arn:aws:s3:::${var.bucket_name}"
}

resource "aws_s3_bucket" "backup" {
  bucket        = var.bucket_name
  force_destroy = false
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_public_access_block" "backup" {
  bucket                  = aws_s3_bucket.backup.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "backup" {
  bucket = aws_s3_bucket.backup.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "backup" {
  bucket = aws_s3_bucket.backup.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_policy" "backup" {
  bucket = aws_s3_bucket.backup.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Deny", Principal = "*", Action = "s3:*"
      Resource  = [aws_s3_bucket.backup.arn, "${aws_s3_bucket.backup.arn}/*"]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })
}

resource "aws_s3_bucket_lifecycle_configuration" "backup" {
  bucket     = aws_s3_bucket.backup.id
  depends_on = [aws_s3_bucket_versioning.backup]
  rule {
    id     = "backup-retention"
    status = "Enabled"
    filter { prefix = local.runs }
    expiration { days = 14 }
    noncurrent_version_expiration { noncurrent_days = 1 }
    abort_incomplete_multipart_upload { days_after_initiation = 1 }
  }
  rule {
    id     = "expired-markers"
    status = "Enabled"
    filter { prefix = local.runs }
    expiration { expired_object_delete_marker = true }
  }
}

# Attach only to the separate backup workload role. No credentials or CA are created here.
resource "aws_iam_policy" "writer" {
  name = "${var.name}-writer"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Effect = "Allow", Action = ["s3:GetBucketVersioning", "s3:GetBucketPublicAccessBlock"],
      Resource = [local.backup_arn, local.assets_arn] },
      { Effect = "Allow", Action = ["s3:GetLifecycleConfiguration"], Resource = local.assets_arn },
      { Effect = "Allow", Action = ["s3:ListBucketVersions"], Resource = local.assets_arn,
      Condition = { StringLike = { "s3:prefix" = ["assets/*"] } } },
      { Effect = "Allow", Action = ["s3:PutObject"], Resource = "${local.backup_arn}/${local.runs}*" }
    ]
  })
}

resource "aws_sns_topic" "backup" {
  name = "${var.name}-alarms"
}

resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.backup.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

resource "aws_iam_role" "monitor" {
  name = "${var.name}-monitor"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = "sts:AssumeRole", Principal = { Service = "lambda.amazonaws.com" } }]
  })
}

resource "aws_cloudwatch_log_group" "monitor" {
  name              = "/aws/lambda/${var.name}-monitor"
  retention_in_days = 14
}

resource "aws_iam_role_policy" "monitor" {
  role = aws_iam_role.monitor.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Effect = "Allow", Action = "s3:ListBucket", Resource = aws_s3_bucket.backup.arn,
      Condition = { StringLike = { "s3:prefix" = ["${local.runs}*"] } } },
      { Effect = "Allow", Action = "s3:GetObject", Resource = "${aws_s3_bucket.backup.arn}/${local.runs}*/manifest.json" },
      { Effect = "Allow", Action = "s3:GetObjectVersion", Resource = "${aws_s3_bucket.backup.arn}/${local.runs}*" },
      { Effect = "Allow", Action = "cloudwatch:PutMetricData", Resource = "*",
      Condition = { StringEquals = { "cloudwatch:namespace" = "ABService/Backup" } } },
      { Effect = "Allow", Action = ["logs:CreateLogStream", "logs:PutLogEvents"],
      Resource = "${aws_cloudwatch_log_group.monitor.arn}:*" }
    ]
  })
}

data "archive_file" "monitor" {
  type        = "zip"
  output_path = "${path.module}/.terraform/backup-monitor.zip"
  source {
    filename = "monitor.py"
    content  = file("${path.module}/monitor.py")
  }
}

resource "aws_lambda_function" "monitor" {
  function_name    = "${var.name}-monitor"
  role             = aws_iam_role.monitor.arn
  handler          = "monitor.handler"
  runtime          = "python3.13"
  architectures    = ["arm64"]
  filename         = data.archive_file.monitor.output_path
  source_code_hash = data.archive_file.monitor.output_base64sha256
  memory_size      = 128
  timeout          = 60
  environment {
    variables = {
      BACKUP_BUCKET = aws_s3_bucket.backup.id
      BACKUP_PREFIX = var.prefix
      BACKUP_NAME   = var.name
      MAX_AGE_HOURS = tostring(var.max_age_hours)
    }
  }
  depends_on = [aws_iam_role_policy.monitor]
}

resource "aws_cloudwatch_event_rule" "monitor" {
  name                = "${var.name}-monitor"
  schedule_expression = "rate(15 minutes)"
  state               = var.monitoring_enabled ? "ENABLED" : "DISABLED"
}

resource "aws_lambda_permission" "schedule" {
  statement_id   = "ScheduledBackupCheck"
  action         = "lambda:InvokeFunction"
  function_name  = aws_lambda_function.monitor.function_name
  principal      = "events.amazonaws.com"
  source_arn     = aws_cloudwatch_event_rule.monitor.arn
  source_account = var.account_id
}

resource "aws_cloudwatch_event_target" "monitor" {
  rule       = aws_cloudwatch_event_rule.monitor.name
  target_id  = "backup-monitor"
  arn        = aws_lambda_function.monitor.arn
  depends_on = [aws_lambda_permission.schedule]
  retry_policy {
    maximum_event_age_in_seconds = 300
    maximum_retry_attempts       = 2
  }
}

resource "aws_cloudwatch_metric_alarm" "backup" {
  alarm_name          = "${var.name}-unhealthy"
  alarm_description   = "Backup stale, absent, unreadable, or external checker stopped. Inspect the last complete snapshot."
  namespace           = "ABService/Backup"
  metric_name         = "Unhealthy"
  dimensions          = { Backup = var.name }
  statistic           = "Maximum"
  period              = 900
  evaluation_periods  = 3
  datapoints_to_alarm = 2
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "breaching"
  actions_enabled     = var.monitoring_enabled
  alarm_actions       = [aws_sns_topic.backup.arn]
  ok_actions          = [aws_sns_topic.backup.arn]
}

output "backup_config" {
  value = { region = var.aws_region, bucket = aws_s3_bucket.backup.id, prefix = var.prefix }
}
output "writer_policy_arn" {
  value = aws_iam_policy.writer.arn
}
output "monitor_function_name" {
  value = aws_lambda_function.monitor.function_name
}
output "alarm_topic_arn" {
  value = aws_sns_topic.backup.arn
}
