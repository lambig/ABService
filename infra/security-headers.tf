locals {
  security_headers = jsondecode(templatefile("${path.module}/headers/security.json", {
    image_sources = "'self' data:"
    api_sources   = "'self'"
    # The backend presigner uses regional virtual-hosted S3 URLs in prod.
    upload_origin = "https://${aws_s3_bucket.assets.bucket_regional_domain_name}"
  }))
  header_noindex = {
    public = !var.public_indexing_enabled
    admin  = true
    api    = true
    assets = false
  }
}

moved {
  from = aws_cloudfront_response_headers_policy.noindex
  to   = aws_cloudfront_response_headers_policy.security["admin"]
}

resource "aws_cloudfront_response_headers_policy" "security" {
  for_each = var.cloudfront_free_compatible ? toset([]) : toset(["public", "admin", "api", "assets"])
  # Retain the existing admin policy's identity during adoption.
  name = each.key == "admin" ? "${var.project_name}-noindex" : "${var.project_name}-${each.key}-security"

  security_headers_config {
    content_security_policy {
      content_security_policy = local.security_headers.policies[each.key]
      override                = true
    }
    content_type_options {
      override = true
    }
    frame_options {
      frame_option = contains(["api", "assets"], each.key) ? "DENY" : local.security_headers.frameOptions
      override     = true
    }
    referrer_policy {
      referrer_policy = local.security_headers.referrerPolicy
      override        = true
    }
    strict_transport_security {
      access_control_max_age_sec = local.security_headers.hstsMaxAge
      include_subdomains         = false
      preload                    = false
      override                   = true
    }
  }

  dynamic "custom_headers_config" {
    for_each = local.header_noindex[each.key] ? [true] : []
    content {
      items {
        header   = "X-Robots-Tag"
        value    = "noindex, nofollow"
        override = true
      }
    }
  }
}
