# ドメインのホストゾーンは#129（ドメイン・DNS・SSL証明書を準備する）の前提として
# 事前にRoute53へ作成済みであることを想定する（レジストラ側のネームサーバー委譲を含む）。
data "aws_route53_zone" "primary" {
  name = var.domain_name
}

resource "aws_acm_certificate" "cloudfront" {
  provider          = aws.us_east_1
  domain_name       = var.domain_name
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "cert_validation" {
  for_each = {
    for dvo in aws_acm_certificate.cloudfront.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      type   = dvo.resource_record_type
      record = dvo.resource_record_value
    }
  }

  zone_id = data.aws_route53_zone.primary.zone_id
  name    = each.value.name
  type    = each.value.type
  ttl     = 300
  records = [each.value.record]
}

resource "aws_acm_certificate_validation" "cloudfront" {
  provider                = aws.us_east_1
  certificate_arn         = aws_acm_certificate.cloudfront.arn
  validation_record_fqdns = [for r in aws_route53_record.cert_validation : r.fqdn]
}

resource "aws_wafv2_web_acl" "cloudfront" {
  provider    = aws.us_east_1
  name        = "${var.project_name}-cloudfront-waf"
  description = "CloudFrontディストリビューション用WAF（AWSマネージドルールによる基本的な保護）"
  scope       = "CLOUDFRONT"

  default_action {
    allow {}
  }

  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 0

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.project_name}-common-rule-set"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "AWSManagedRulesKnownBadInputsRuleSet"
    priority = 1

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.project_name}-known-bad-inputs"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.project_name}-cloudfront-waf"
    sampled_requests_enabled   = true
  }
}

# /admin* と /api/* は検索エンジンにインデックスさせない（SEOペナルティ回避のためrobots.txtのDisallowと併用する）。
resource "aws_cloudfront_response_headers_policy" "noindex" {
  name = "${var.project_name}-noindex"

  custom_headers_config {
    items {
      header   = "X-Robots-Tag"
      value    = "noindex, nofollow"
      override = true
    }
  }
}

# 静的サイトの要求経路を実オブジェクトキーへ解決する（ディレクトリ索引の代わり）。
# 綴りとその理由は functions/resolve-static-uri.js を参照。E2Eの配信も同じファイルを読んで適用する。
resource "aws_cloudfront_function" "resolve_static_uri" {
  name    = "${var.project_name}-resolve-static-uri"
  runtime = "cloudfront-js-2.0"
  comment = "ディレクトリ索引を持たないS3(OAC)向けに、経路末尾へindex.htmlを補う"
  publish = true
  code    = file("${path.module}/functions/resolve-static-uri.js")
}

resource "aws_cloudfront_origin_access_control" "s3" {
  name                              = "${var.project_name}-s3-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

locals {
  public_origin_id  = "s3-frontend-public"
  admin_origin_id   = "s3-frontend-admin"
  assets_origin_id  = "s3-assets"
  backend_origin_id = "ec2-backend"
}

resource "aws_cloudfront_distribution" "main" {
  enabled         = true
  is_ipv6_enabled = true
  price_class     = var.cloudfront_price_class
  aliases         = [var.domain_name]

  # 配信直下は index.html。resolve_static_uri でも同じ結果になるが、どちらが先に走るかへ
  # 依存させないため両方を宣言する。この宣言はサブディレクトリには効かない。
  default_root_object = "index.html"

  # / -> frontend-public（S3, OAC経由）
  origin {
    domain_name              = aws_s3_bucket.frontend_public.bucket_regional_domain_name
    origin_id                = local.public_origin_id
    origin_access_control_id = aws_cloudfront_origin_access_control.s3.id
  }

  # /admin* -> frontend-admin（S3, OAC経由）
  origin {
    domain_name              = aws_s3_bucket.frontend_admin.bucket_regional_domain_name
    origin_id                = local.admin_origin_id
    origin_access_control_id = aws_cloudfront_origin_access_control.s3.id
  }

  # /assets/* -> アセット（S3, OAC経由）。オブジェクトキーの接頭辞を assets/ に揃えているため origin_path は不要
  origin {
    domain_name              = aws_s3_bucket.assets.bucket_regional_domain_name
    origin_id                = local.assets_origin_id
    origin_access_control_id = aws_cloudfront_origin_access_control.s3.id
  }

  # /api/* -> backend（EC2、AWSバックボーン内はHTTPのまま。ビューア向けTLSはCloudFrontで終端）
  origin {
    domain_name = aws_instance.backend.public_dns
    origin_id   = local.backend_origin_id

    custom_origin_config {
      http_port              = var.backend_app_port
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    target_origin_id       = local.public_origin_id
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.resolve_static_uri.arn
    }

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }
  }

  # パターンは `/admin/*` ではなく `/admin*`。`/admin/*` は末尾スラッシュのない `/admin` に
  # 一致せず、管理画面の入口が既定の振り分け（公開サイトのバケット）へ流れる。
  # 代償として `/administrators` のような綴りも管理画面側へ向くが、公開サイトはその経路を持たない。
  ordered_cache_behavior {
    path_pattern               = "/admin*"
    target_origin_id           = local.admin_origin_id
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    response_headers_policy_id = aws_cloudfront_response_headers_policy.noindex.id

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.resolve_static_uri.arn
    }

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }
  }

  # アセットは確定後に内容が変わらない（キーがUUIDv7で一意）ため長期キャッシュしてよい
  ordered_cache_behavior {
    path_pattern           = "/assets/*"
    target_origin_id       = local.assets_origin_id
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }

    min_ttl     = 0
    default_ttl = 86400
    max_ttl     = 31536000
  }

  ordered_cache_behavior {
    path_pattern               = "/api/*"
    target_origin_id           = local.backend_origin_id
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    response_headers_policy_id = aws_cloudfront_response_headers_policy.noindex.id

    forwarded_values {
      query_string = true
      headers      = ["Authorization"]
      cookies {
        forward = "none"
      }
    }

    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate_validation.cloudfront.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  web_acl_id = aws_wafv2_web_acl.cloudfront.arn

  tags = {
    Name = "${var.project_name}-cdn"
  }
}

resource "aws_route53_record" "root" {
  zone_id = data.aws_route53_zone.primary.zone_id
  name    = var.domain_name
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.main.domain_name
    zone_id                = aws_cloudfront_distribution.main.hosted_zone_id
    evaluate_target_health = false
  }
}

# --- S3バケットポリシー（CloudFront OACからのみ読み取りを許可） ---

data "aws_iam_policy_document" "frontend_public_oac" {
  statement {
    sid       = "AllowCloudFrontServicePrincipalReadOnly"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.frontend_public.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.main.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "frontend_public" {
  bucket = aws_s3_bucket.frontend_public.id
  policy = data.aws_iam_policy_document.frontend_public_oac.json
}

data "aws_iam_policy_document" "frontend_admin_oac" {
  statement {
    sid       = "AllowCloudFrontServicePrincipalReadOnly"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.frontend_admin.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.main.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "frontend_admin" {
  bucket = aws_s3_bucket.frontend_admin.id
  policy = data.aws_iam_policy_document.frontend_admin_oac.json
}

data "aws_iam_policy_document" "assets_oac" {
  statement {
    sid       = "AllowCloudFrontServicePrincipalReadOnly"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.assets.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.main.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "assets" {
  bucket = aws_s3_bucket.assets.id
  policy = data.aws_iam_policy_document.assets_oac.json
}
