# Independent edge root: no compute, database, asset-bucket or DNS ownership.
terraform {
  required_version = ">= 1.9.0"
  required_providers {
    aws     = { source = "hashicorp/aws", version = "~> 5.0" }
    archive = { source = "hashicorp/archive", version = "~> 2.0" }
  }
  backend "s3" {}
}
provider "aws" {
  region              = var.region
  allowed_account_ids = [var.account_id]
  default_tags { tags = { ManagedBy = "Terraform", Component = var.name } }
}
provider "aws" {
  alias               = "us_east_1"
  region              = "us-east-1"
  allowed_account_ids = [var.account_id]
}

data "aws_s3_bucket" "assets" { bucket = var.assets_bucket }

resource "aws_s3_bucket" "frontend" {
  for_each = toset(["public", "admin", "releases"])
  bucket   = "${var.name}-${each.key}-${var.account_id}"
  lifecycle { prevent_destroy = true }
}
resource "aws_s3_bucket_public_access_block" "frontend" {
  for_each                = aws_s3_bucket.frontend
  bucket                  = each.value.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
resource "aws_s3_bucket_versioning" "frontend" {
  for_each = aws_s3_bucket.frontend
  bucket   = each.value.id
  versioning_configuration { status = "Enabled" }
}
resource "aws_s3_bucket_server_side_encryption_configuration" "frontend" {
  for_each = aws_s3_bucket.frontend
  bucket   = each.value.id
  rule {
    apply_server_side_encryption_by_default { sse_algorithm = "AES256" }
  }
}
resource "aws_s3_bucket_policy" "frontend" {
  for_each = aws_s3_bucket.frontend
  bucket   = each.value.id
  policy = jsonencode({ Version = "2012-10-17", Statement = concat([{
    Effect    = "Deny", Principal = "*", Action = "s3:*"
    Resource  = [each.value.arn, "${each.value.arn}/*"]
    Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }], each.key == "releases" ? [] : [
    { Effect = "Allow", Principal = { Service = "cloudfront.amazonaws.com" }, Action = "s3:GetObject",
    Resource = "${each.value.arn}/*", Condition = { StringEquals = { "AWS:SourceArn" = aws_cloudfront_distribution.main.arn } } },
    { Effect = "Allow", Principal = { Service = "cloudfront.amazonaws.com" }, Action = "s3:ListBucket",
    Resource = each.value.arn, Condition = { StringEquals = { "AWS:SourceArn" = aws_cloudfront_distribution.main.arn } } }
  ]) })
}

locals {
  managed_cache_disabled  = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad"
  managed_cache_optimized = "658327ea-f89d-4fab-a63d-7e88639e58f6"
  managed_origin_api      = "b689b0a8-53d0-40ab-baf2-68738e2966ac"
  origins = {
    public = aws_s3_bucket.frontend["public"].bucket_regional_domain_name
    admin  = aws_s3_bucket.frontend["admin"].bucket_regional_domain_name
    assets = data.aws_s3_bucket.assets.bucket_regional_domain_name
    api    = var.backend_domain
  }
  security_headers = jsondecode(templatefile("${path.module}/../headers/security.json", {
    image_sources = "'self' data:"
    api_sources   = "'self'"
    upload_origin = "https://${data.aws_s3_bucket.assets.bucket_regional_domain_name}"
  }))
  header_noindex = { public = !var.public_indexing_enabled, admin = true, api = true, assets = false }
  edge_security_config = {
    security = local.security_headers, noindex = local.header_noindex
    origins  = { for kind, domain in local.origins : domain => kind }
  }
  # A list preserves behavior precedence. Admin deliberately includes /admin.
  ordered_behaviors = [{ kind = "admin", path = "/admin*" }, { kind = "assets", path = "/assets/*" }, { kind = "api", path = "/api/*" }]
}
resource "aws_cloudfront_function" "resolve_static_uri" {
  name    = "${var.name}-resolve-static-uri"
  runtime = "cloudfront-js-2.0"
  publish = true
  code    = file("${path.module}/../functions/resolve-static-uri.js")
}
resource "aws_cloudfront_function" "security_response" {
  for_each = toset(["public", "admin", "api", "assets"])
  name     = "${var.name}-${each.key}-security-response"
  runtime  = "cloudfront-js-2.0"
  publish  = true
  code = templatefile("${path.module}/../functions/security-response.js.tftpl", {
    renderer = replace(file("${path.module}/../headers/build-security-headers.mjs"), "export function", "function")
    config   = jsonencode(local.security_headers)
    kind     = jsonencode(each.key)
    noindex  = jsonencode(local.header_noindex[each.key])
  })
}
resource "aws_cloudfront_origin_access_control" "s3" {
  name                              = "${var.name}-s3-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}
resource "aws_cloudfront_distribution" "main" {
  enabled             = var.enabled
  is_ipv6_enabled     = true
  default_root_object = "index.html"
  aliases             = var.aliases
  price_class         = "PriceClass_All"
  web_acl_id          = aws_wafv2_web_acl.cloudfront.arn
  lifecycle {
    precondition {
      condition     = !var.enabled || var.free_plan_verified
      error_message = "Read back the FREE/ACTIVE subscription before enabling delivery."
    }
    precondition {
      condition     = length(var.aliases) == 0 || var.viewer_certificate_arn != null
      error_message = "Custom aliases require an issued us-east-1 certificate."
    }
    precondition {
      condition     = !var.enabled || var.backend_protocol == "https-only"
      error_message = "Enabled delivery must encrypt origin verification and API credentials in transit."
    }
  }
  dynamic "origin" {
    for_each = { for k, v in local.origins : k => v if k != "api" }
    content {
      origin_id                = origin.key
      domain_name              = origin.value
      origin_access_control_id = aws_cloudfront_origin_access_control.s3.id
    }
  }
  origin {
    origin_id   = "api"
    domain_name = var.backend_domain
    custom_origin_config {
      http_port              = var.backend_port
      https_port             = var.backend_https_port
      origin_protocol_policy = var.backend_protocol
      origin_ssl_protocols   = ["TLSv1.2"]
    }
    custom_header {
      name  = "X-Origin-Verify"
      value = var.origin_verify_token
    }
  }
  default_cache_behavior {
    target_origin_id       = "public"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true
    cache_policy_id        = local.managed_cache_disabled
    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.resolve_static_uri.arn
    }
    function_association {
      event_type   = "viewer-response"
      function_arn = aws_cloudfront_function.security_response["public"].arn
    }
    lambda_function_association {
      event_type   = "origin-response"
      lambda_arn   = aws_lambda_function.static_page_404.qualified_arn
      include_body = false
    }
  }
  dynamic "ordered_cache_behavior" {
    for_each = local.ordered_behaviors
    content {
      path_pattern             = ordered_cache_behavior.value.path
      target_origin_id         = ordered_cache_behavior.value.kind
      viewer_protocol_policy   = "redirect-to-https"
      allowed_methods          = ordered_cache_behavior.value.kind == "api" ? ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"] : ["GET", "HEAD"]
      cached_methods           = ["GET", "HEAD"]
      compress                 = true
      cache_policy_id          = ordered_cache_behavior.value.kind == "assets" ? local.managed_cache_optimized : local.managed_cache_disabled
      origin_request_policy_id = ordered_cache_behavior.value.kind == "api" ? local.managed_origin_api : null
      dynamic "function_association" {
        for_each = ordered_cache_behavior.value.kind == "admin" ? [true] : []
        content {
          event_type   = "viewer-request"
          function_arn = aws_cloudfront_function.resolve_static_uri.arn
        }
      }
      function_association {
        event_type   = "viewer-response"
        function_arn = aws_cloudfront_function.security_response[ordered_cache_behavior.value.kind].arn
      }
      lambda_function_association {
        event_type   = "origin-response"
        lambda_arn   = aws_lambda_function.static_page_404.qualified_arn
        include_body = false
      }
    }
  }
  restrictions {
    geo_restriction { restriction_type = "none" }
  }
  viewer_certificate {
    cloudfront_default_certificate = var.viewer_certificate_arn == null
    acm_certificate_arn            = var.viewer_certificate_arn
    ssl_support_method             = var.viewer_certificate_arn == null ? null : "sni-only"
    minimum_protocol_version       = var.viewer_certificate_arn == null ? "TLSv1" : "TLSv1.2_2021"
  }
}

# Provider v5 does not expose PricingPlanManager. Manage only the subscription
# through CloudFormation; distribution/WAF remain owned by this Terraform root.
resource "aws_cloudformation_stack" "free_plan" {
  provider = aws.us_east_1
  name     = "${var.name}-free-plan"
  template_body = jsonencode({
    AWSTemplateFormatVersion = "2010-09-09"
    Resources = { Subscription = {
      Type = "AWS::PricingPlanManager::Subscription"
      Properties = {
        PlanFamily   = "CloudFront", PlanTier = "FREE", UsageLevel = "DEFAULT"
        ResourceArns = [aws_cloudfront_distribution.main.arn, aws_wafv2_web_acl.cloudfront.arn]
      }
    } }
    Outputs = { SubscriptionArn = { Value = { "Fn::GetAtt" = ["Subscription", "Arn"] } } }
  })
}
output "connection" {
  value = {
    distribution_id  = aws_cloudfront_distribution.main.id
    distribution_arn = aws_cloudfront_distribution.main.arn
    domain           = aws_cloudfront_distribution.main.domain_name
    web_acl_arn      = aws_wafv2_web_acl.cloudfront.arn
    subscription_arn = aws_cloudformation_stack.free_plan.outputs["SubscriptionArn"]
    buckets          = { for k, b in aws_s3_bucket.frontend : k => b.id }
    assets_bucket    = data.aws_s3_bucket.assets.id
  }
}
