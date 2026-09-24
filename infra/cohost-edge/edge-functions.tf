resource "aws_wafv2_web_acl" "cloudfront" {
  provider    = aws.us_east_1
  name        = "${var.name}-cloudfront-waf"
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
      metric_name                = "${var.name}-common-rule-set"
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
      metric_name                = "${var.name}-known-bad-inputs"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.name}-cloudfront-waf"
    sampled_requests_enabled   = true
  }
}

# Lambda@Edge needs a published version in us-east-1 and cannot use custom
# environment variables. Only the two generated 404 documents are readable.
data "archive_file" "static_page_404" {
  type        = "zip"
  output_path = "${path.module}/.terraform/static-page-404.zip"
  source {
    filename = "index.mjs"
    content  = replace(file("${path.module}/../functions/secured-origin-response.mjs"), "../headers/build-security-headers.mjs", "./build-security-headers.mjs")
  }
  dynamic "source" {
    for_each = {
      "static-page-404.mjs"        = file("${path.module}/../functions/static-page-404.mjs")
      "build-security-headers.mjs" = file("${path.module}/../headers/build-security-headers.mjs")
      "security-config.json"       = jsonencode(local.edge_security_config)
    }
    content {
      filename = source.key
      content  = source.value
    }
  }
  source {
    filename = "origins.json"
    content = jsonencode({
      region = var.region
      origins = {
        (aws_s3_bucket.frontend["public"].bucket_regional_domain_name) = { bucket = aws_s3_bucket.frontend["public"].id, key = "404.html" }
        (aws_s3_bucket.frontend["admin"].bucket_regional_domain_name)  = { bucket = aws_s3_bucket.frontend["admin"].id, key = "admin/404.html" }
      }
    })
  }
}

resource "aws_iam_role" "static_page_404" {
  name = "${var.name}-static-page-404"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = ["lambda.amazonaws.com", "edgelambda.amazonaws.com"] }
    }]
  })
}

resource "aws_iam_role_policy" "static_page_404" {
  role = aws_iam_role.static_page_404.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject"]
        Resource = ["${aws_s3_bucket.frontend["public"].arn}/404.html", "${aws_s3_bucket.frontend["admin"].arn}/admin/404.html"]
      },
      {
        Effect   = "Allow"
        Action   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "arn:aws:logs:*:${var.account_id}:*"
      }
    ]
  })
}

resource "aws_lambda_function" "static_page_404" {
  provider         = aws.us_east_1
  function_name    = "${var.name}-static-page-404"
  role             = aws_iam_role.static_page_404.arn
  handler          = "index.handler"
  runtime          = "nodejs22.x"
  architectures    = ["x86_64"]
  filename         = data.archive_file.static_page_404.output_path
  source_code_hash = data.archive_file.static_page_404.output_base64sha256
  publish          = true
  timeout          = 10
  memory_size      = 128
  depends_on       = [aws_iam_role_policy.static_page_404]
}
