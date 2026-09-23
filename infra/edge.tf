# ドメインのホストゾーンは#129（ドメイン・DNS・SSL証明書を準備する）の前提として
# 事前にRoute53へ作成済みであることを想定する（レジストラ側のネームサーバー委譲を含む）。
data "aws_route53_zone" "primary" {
  name = var.domain_name
}

resource "aws_acm_certificate" "cloudfront" {
  provider                  = aws.us_east_1
  domain_name               = var.domain_name
  validation_method         = "DNS"
  subject_alternative_names = var.serve_www ? ["www.${var.domain_name}"] : []

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "cert_validation" {
  for_each = {
    for dvo in aws_acm_certificate.cloudfront.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
    }
  }

  zone_id = data.aws_route53_zone.primary.zone_id
  name    = each.value.name
  # ACM DNS validation uses CNAME; keep the type known in the initial plan.
  type    = "CNAME"
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
  description = "CloudFront protection with AWS managed rule groups"
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
  price_class     = var.cloudfront_free_compatible ? "PriceClass_All" : var.cloudfront_price_class
  aliases         = var.serve_www ? [var.domain_name, "www.${var.domain_name}"] : [var.domain_name]

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

    # セキュリティグループが許すのはCloudFront共通の送信元範囲で、他の配信も含まれる。この配信だけが
    # 付ける値をbackendが検査し、一致しない要求を拒む（#286）。値はParameter Store経由でbackendへも渡る。
    custom_header {
      name  = "X-Origin-Verify"
      value = random_password.origin_verify_token.result
    }
  }

  default_cache_behavior {
    target_origin_id           = local.public_origin_id
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    response_headers_policy_id = var.cloudfront_free_compatible ? null : aws_cloudfront_response_headers_policy.security["public"].id
    # HTML uses max-age=0,must-revalidate; CachingOptimized forces a 1s floor.
    cache_policy_id          = var.cloudfront_free_compatible ? local.managed_cache_disabled : null
    origin_request_policy_id = null

    dynamic "function_association" {
      for_each = var.cloudfront_free_compatible ? [true] : []
      content {
        event_type   = "viewer-response"
        function_arn = aws_cloudfront_function.security_response["public"].arn
      }
    }

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.resolve_static_uri.arn
    }

    lambda_function_association {
      event_type   = "origin-response"
      lambda_arn   = aws_lambda_function.static_page_404.qualified_arn
      include_body = false
    }

    dynamic "forwarded_values" {
      for_each = var.cloudfront_free_compatible ? [] : [true]
      content {
        query_string = false
        cookies {
          forward = "none"
        }
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
    response_headers_policy_id = var.cloudfront_free_compatible ? null : aws_cloudfront_response_headers_policy.security["admin"].id
    cache_policy_id            = var.cloudfront_free_compatible ? local.managed_cache_disabled : null
    origin_request_policy_id   = null

    dynamic "function_association" {
      for_each = var.cloudfront_free_compatible ? [true] : []
      content {
        event_type   = "viewer-response"
        function_arn = aws_cloudfront_function.security_response["admin"].arn
      }
    }

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.resolve_static_uri.arn
    }

    lambda_function_association {
      event_type   = "origin-response"
      lambda_arn   = aws_lambda_function.static_page_404.qualified_arn
      include_body = false
    }

    dynamic "forwarded_values" {
      for_each = var.cloudfront_free_compatible ? [] : [true]
      content {
        query_string = false
        cookies {
          forward = "none"
        }
      }
    }
  }

  # アセットは確定後に内容が変わらない（キーがUUIDv7で一意）ため長期キャッシュしてよい
  ordered_cache_behavior {
    path_pattern               = "/assets/*"
    response_headers_policy_id = var.cloudfront_free_compatible ? null : aws_cloudfront_response_headers_policy.security["assets"].id
    cache_policy_id            = var.cloudfront_free_compatible ? local.managed_cache_optimized : null
    origin_request_policy_id   = null

    dynamic "function_association" {
      for_each = var.cloudfront_free_compatible ? [true] : []
      content {
        event_type   = "viewer-response"
        function_arn = aws_cloudfront_function.security_response["assets"].arn
      }
    }
    dynamic "lambda_function_association" {
      for_each = var.cloudfront_free_compatible ? [true] : []
      content {
        event_type   = "origin-response"
        lambda_arn   = aws_lambda_function.static_page_404.qualified_arn
        include_body = false
      }
    }
    target_origin_id       = local.assets_origin_id
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    dynamic "forwarded_values" {
      for_each = var.cloudfront_free_compatible ? [] : [true]
      content {
        query_string = false
        cookies {
          forward = "none"
        }
      }
    }

    min_ttl     = var.cloudfront_free_compatible ? null : 0
    default_ttl = var.cloudfront_free_compatible ? null : 86400
    max_ttl     = var.cloudfront_free_compatible ? null : 31536000
  }

  ordered_cache_behavior {
    path_pattern               = "/api/*"
    target_origin_id           = local.backend_origin_id
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    response_headers_policy_id = var.cloudfront_free_compatible ? null : aws_cloudfront_response_headers_policy.security["api"].id
    cache_policy_id            = var.cloudfront_free_compatible ? local.managed_cache_disabled : null
    origin_request_policy_id   = var.cloudfront_free_compatible ? local.managed_origin_api : null

    dynamic "function_association" {
      for_each = var.cloudfront_free_compatible ? [true] : []
      content {
        event_type   = "viewer-response"
        function_arn = aws_cloudfront_function.security_response["api"].arn
      }
    }
    dynamic "lambda_function_association" {
      for_each = var.cloudfront_free_compatible ? [true] : []
      content {
        event_type   = "origin-response"
        lambda_arn   = aws_lambda_function.static_page_404.qualified_arn
        include_body = false
      }
    }

    dynamic "forwarded_values" {
      for_each = var.cloudfront_free_compatible ? [] : [true]
      content {
        query_string = true
        headers      = ["Authorization"]
        cookies {
          forward = "none"
        }
      }
    }

    min_ttl     = var.cloudfront_free_compatible ? null : 0
    default_ttl = var.cloudfront_free_compatible ? null : 0
    max_ttl     = var.cloudfront_free_compatible ? null : 0
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

moved {
  from = aws_route53_record.root
  to   = aws_route53_record.root[0]
}

resource "aws_route53_record" "root" {
  count = var.dns_cutover_enabled ? 1 : 0

  # Disabling cutover after adoption must not delete the live DNS record.
  lifecycle {
    prevent_destroy = true
  }

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

  # With ListBucket, S3 distinguishes a missing key (404) from denied access
  # (403). Only this distribution can use it; viewer query strings are dropped
  # and the root URI is resolved to index.html, so this is not a listing route.
  statement {
    sid       = "DistinguishMissingStaticObjects"
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.frontend_public.arn]

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

  # With ListBucket, S3 distinguishes a missing key (404) from denied access
  # (403). Only this distribution can use it; viewer query strings are dropped
  # and the root URI is resolved to index.html, so this is not a listing route.
  statement {
    sid       = "DistinguishMissingStaticObjects"
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.frontend_admin.arn]

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

# IPv6 is enabled on the distribution; cutover must cover both address families.
resource "aws_route53_record" "root_ipv6" {
  count   = var.dns_cutover_enabled ? 1 : 0
  zone_id = data.aws_route53_zone.primary.zone_id
  name    = var.domain_name
  type    = "AAAA"

  lifecycle {
    prevent_destroy = true
  }

  alias {
    name                   = aws_cloudfront_distribution.main.domain_name
    zone_id                = aws_cloudfront_distribution.main.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "www" {
  for_each = var.dns_cutover_enabled && var.serve_www ? toset(["A", "AAAA"]) : toset([])
  zone_id  = data.aws_route53_zone.primary.zone_id
  name     = "www.${var.domain_name}"
  type     = each.value

  lifecycle {
    prevent_destroy = true
  }

  alias {
    name                   = aws_cloudfront_distribution.main.domain_name
    zone_id                = aws_cloudfront_distribution.main.hosted_zone_id
    evaluate_target_health = false
  }
}
