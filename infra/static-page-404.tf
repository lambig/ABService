# Lambda@Edge needs a published version in us-east-1 and cannot use custom
# environment variables. Only the two generated 404 documents are readable.
data "archive_file" "static_page_404" {
  type        = "zip"
  output_path = "${path.module}/.terraform/static-page-404.zip"
  source {
    filename = "index.mjs"
    content  = file("${path.module}/functions/static-page-404.mjs")
  }
  source {
    filename = "origins.json"
    content = jsonencode({
      region = var.aws_region
      origins = {
        (aws_s3_bucket.frontend_public.bucket_regional_domain_name) = { bucket = aws_s3_bucket.frontend_public.id, key = "404.html" }
        (aws_s3_bucket.frontend_admin.bucket_regional_domain_name)  = { bucket = aws_s3_bucket.frontend_admin.id, key = "admin/404.html" }
      }
    })
  }
}

resource "aws_iam_role" "static_page_404" {
  name = "${var.project_name}-static-page-404"
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
        Resource = ["${aws_s3_bucket.frontend_public.arn}/404.html", "${aws_s3_bucket.frontend_admin.arn}/admin/404.html"]
      },
      {
        Effect   = "Allow"
        Action   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "arn:aws:logs:*:${data.aws_caller_identity.current.account_id}:*"
      }
    ]
  })
}

resource "aws_lambda_function" "static_page_404" {
  provider         = aws.us_east_1
  function_name    = "${var.project_name}-static-page-404"
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
