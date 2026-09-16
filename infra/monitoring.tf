# 監視と通知（#168）。異常の発見・原因追跡・復旧確認に要る最小限だけを置く。
#
# ログはコンテナの stdout（prod は JSON）を Docker の awslogs ドライバがそのまま運ぶ（docker-compose.logs.yml）。
# ホストのディスクとメモリは標準指標に無いため CloudWatch agent が送り、それ以外はサービスの標準指標を使う。
# 通知先は SNS のトピックをリージョンごとに1つ（主リージョンと us-east-1）で、宛先（メール）は tfvars が持つ。
# 値はリポジトリに書かない。
#
# readiness（/q/*）は配信が /api/* しか流さないため外から引けない。代わりに公開 API の1経路を Route53 の
# ヘルスチェックで引き、公開 URL の失敗として拾う。判断は docs/DECISIONS.md。

locals {
  backend_log_group_name = "/${var.project_name}/${var.environment}/backend"
  # アラームのアクションはアラームと同じリージョンに要る。主リージョンと us-east-1 で別のトピックを持つ
  alarm_actions           = [aws_sns_topic.alarms.arn]
  alarm_actions_us_east_1 = [aws_sns_topic.alarms_us_east_1.arn]
  host_metrics_namespace  = "CWAgent"
  backend_log_namespace   = "${var.project_name}/backend"

  # 形の誤りは validate で落とす（agent は設定を読めないと黙って何も送らない）
  cloudwatch_agent_config = jsondecode(file("${path.module}/monitoring/cloudwatch-agent.json"))
}

# --- ログ ---

resource "aws_cloudwatch_log_group" "backend" {
  name              = local.backend_log_group_name
  retention_in_days = var.log_retention_days
}

# deploy.sh が読み、awslogs ドライバの宛先として compose へ渡す（DB 接続情報と同じ経路）
resource "aws_ssm_parameter" "backend_log_group" {
  name  = "/${var.project_name}/${var.environment}/monitoring/log-group"
  type  = "String"
  value = aws_cloudwatch_log_group.backend.name
}

# 未捕捉の例外や失敗した処理は ERROR で出る（#118）。件数を指標にしてアラームへ繋ぐ
resource "aws_cloudwatch_log_metric_filter" "backend_errors" {
  name           = "${var.project_name}-backend-errors"
  log_group_name = aws_cloudwatch_log_group.backend.name
  pattern        = "{ $.level = \"ERROR\" }"

  metric_transformation {
    name          = "BackendErrorLogs"
    namespace     = local.backend_log_namespace
    value         = "1"
    default_value = "0"
  }
}

# --- ホスト指標（CloudWatch agent） ---

# agent は起動時にこのパラメータから設定を読む（user_data）。設定の実体は monitoring/cloudwatch-agent.json
resource "aws_ssm_parameter" "cloudwatch_agent_config" {
  name  = "/${var.project_name}/${var.environment}/monitoring/cloudwatch-agent"
  type  = "String"
  value = jsonencode(local.cloudwatch_agent_config)
}

# --- 通知先 ---
#
# CloudWatch アラームのアクションはアラームと同じリージョンの SNS でなければならず、Route53 ヘルスチェックの
# 指標とアラームは us-east-1 にしか無い。そのため主リージョンと us-east-1 に1つずつトピックを持ち、宛先は同じ
# メールにする。リージョン間の転送（EventBridge / Lambda）は部品と権限が増えるため採らない。
# メールの購読は宛先が確認を踏むまで届かない（トピックごとに1通。運用側の手順）

resource "aws_sns_topic" "alarms" {
  name = "${var.project_name}-alarms"
}

resource "aws_sns_topic_subscription" "alarm_email" {
  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

resource "aws_sns_topic" "alarms_us_east_1" {
  provider = aws.us_east_1
  name     = "${var.project_name}-alarms"
}

resource "aws_sns_topic_subscription" "alarm_email_us_east_1" {
  provider  = aws.us_east_1
  topic_arn = aws_sns_topic.alarms_us_east_1.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

# --- 公開 URL の合成監視 ---

# 切替前は配信のドメイン名、切替後は正規ドメインを引く。引く経路は公開 Query の1つで、
# CloudFront → WAF → オリジン識別 → backend → DB まで通る
resource "aws_route53_health_check" "public_api" {
  fqdn              = var.dns_cutover_enabled ? var.domain_name : aws_cloudfront_distribution.main.domain_name
  type              = "HTTPS"
  port              = 443
  resource_path     = "/api/v1/albums"
  request_interval  = 30
  failure_threshold = 3

  tags = {
    Name = "${var.project_name}-public-api"
  }
}

# --- アラーム ---
# 通知先は自分のリージョンのトピック。復旧（OK）も通知し、止まったことと戻ったことの両方を残す

resource "aws_cloudwatch_metric_alarm" "ec2_status_check" {
  alarm_name          = "${var.project_name}-ec2-status-check"
  alarm_description   = "backend EC2 のステータスチェックが失敗している（インスタンスまたはホストの障害）"
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed"
  dimensions          = { InstanceId = aws_instance.backend.id }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "breaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "ec2_cpu" {
  alarm_name          = "${var.project_name}-ec2-cpu"
  alarm_description   = "backend EC2 の CPU 使用率が高止まりしている"
  namespace           = "AWS/EC2"
  metric_name         = "CPUUtilization"
  dimensions          = { InstanceId = aws_instance.backend.id }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  threshold           = var.cpu_utilization_alarm_percent
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

# ディスクは標準指標に無い。逼迫の主因はデプロイごとに溜まる旧世代のイメージ（#336）
resource "aws_cloudwatch_metric_alarm" "host_disk" {
  alarm_name          = "${var.project_name}-host-disk"
  alarm_description   = "backend EC2 のルートボリュームの使用率が高い（旧世代イメージの滞留など。#336）"
  namespace           = local.host_metrics_namespace
  metric_name         = "disk_used_percent"
  dimensions          = { InstanceId = aws_instance.backend.id }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 1
  threshold           = var.disk_used_alarm_percent
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "host_memory" {
  alarm_name          = "${var.project_name}-host-memory"
  alarm_description   = "backend EC2 のメモリ使用率が高い"
  namespace           = local.host_metrics_namespace
  metric_name         = "mem_used_percent"
  dimensions          = { InstanceId = aws_instance.backend.id }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  threshold           = var.memory_used_alarm_percent
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "rds_free_storage" {
  alarm_name          = "${var.project_name}-rds-free-storage"
  alarm_description   = "RDS の空きストレージが少ない"
  namespace           = "AWS/RDS"
  metric_name         = "FreeStorageSpace"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.main.identifier }
  statistic           = "Minimum"
  period              = 300
  evaluation_periods  = 1
  threshold           = var.rds_free_storage_alarm_bytes
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "rds_connections" {
  alarm_name          = "${var.project_name}-rds-connections"
  alarm_description   = "RDS の接続数が上限に近い"
  namespace           = "AWS/RDS"
  metric_name         = "DatabaseConnections"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.main.identifier }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 2
  threshold           = var.rds_connections_alarm_count
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "backend_errors" {
  alarm_name          = "${var.project_name}-backend-errors"
  alarm_description   = "backend が ERROR を出している（未捕捉の例外など。ログで原因を追う）"
  namespace           = local.backend_log_namespace
  metric_name         = aws_cloudwatch_log_metric_filter.backend_errors.metric_transformation[0].name
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = var.error_log_alarm_count
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

# CloudFront と Route53 ヘルスチェックの指標は us-east-1 にしか無い。通知先も同じリージョンのトピック
resource "aws_cloudwatch_metric_alarm" "cloudfront_5xx" {
  provider            = aws.us_east_1
  alarm_name          = "${var.project_name}-cloudfront-5xx"
  alarm_description   = "配信の 5xx 率が高い（API の失敗、オリジン到達不能）"
  namespace           = "AWS/CloudFront"
  metric_name         = "5xxErrorRate"
  dimensions          = { DistributionId = aws_cloudfront_distribution.main.id, Region = "Global" }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 1
  threshold           = var.cloudfront_5xx_alarm_percent
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions_us_east_1
  ok_actions          = local.alarm_actions_us_east_1
}

resource "aws_cloudwatch_metric_alarm" "public_api_health" {
  provider            = aws.us_east_1
  alarm_name          = "${var.project_name}-public-api-health"
  alarm_description   = "公開 API が外から応答していない（Route53 ヘルスチェック）"
  namespace           = "AWS/Route53"
  metric_name         = "HealthCheckStatus"
  dimensions          = { HealthCheckId = aws_route53_health_check.public_api.id }
  statistic           = "Minimum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"
  alarm_actions       = local.alarm_actions_us_east_1
  ok_actions          = local.alarm_actions_us_east_1
}
