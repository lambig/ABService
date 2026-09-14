# Archives and release records must never be served by CloudFront.
resource "aws_s3_bucket" "frontend_releases" {
  bucket = "${var.project_name}-frontend-releases-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_public_access_block" "frontend_releases" {
  bucket                  = aws_s3_bucket.frontend_releases.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "frontend_releases" {
  bucket = aws_s3_bucket.frontend_releases.id
  versioning_configuration {
    status = "Enabled"
  }
}

data "aws_iam_policy_document" "frontend_deploy_assume_role" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github_actions.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:ref:refs/heads/main"]
    }
  }
}

resource "aws_iam_role" "frontend_deploy" {
  name               = "${var.project_name}-frontend-deploy"
  assume_role_policy = data.aws_iam_policy_document.frontend_deploy_assume_role.json
}

resource "aws_iam_role_policy" "frontend_deploy" {
  name = "${var.project_name}-frontend-deploy"
  role = aws_iam_role.frontend_deploy.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["s3:ListBucket"]
        Resource = [
          aws_s3_bucket.frontend_public.arn,
          aws_s3_bucket.frontend_admin.arn,
          aws_s3_bucket.frontend_releases.arn
        ]
      },
      {
        Effect = "Allow"
        Action = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
        Resource = [
          "${aws_s3_bucket.frontend_public.arn}/*",
          "${aws_s3_bucket.frontend_admin.arn}/admin/*",
          "${aws_s3_bucket.frontend_releases.arn}/*"
        ]
      },
      {
        Effect   = "Allow"
        Action   = ["cloudfront:CreateInvalidation", "cloudfront:GetInvalidation"]
        Resource = aws_cloudfront_distribution.main.arn
      }
    ]
  })
}

output "github_actions_frontend_deploy_role_arn" {
  value = aws_iam_role.frontend_deploy.arn
}

output "frontend_release_bucket_name" {
  value = aws_s3_bucket.frontend_releases.bucket
}

output "cloudfront_distribution_id" {
  value = aws_cloudfront_distribution.main.id
}
