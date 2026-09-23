# Independent, opt-in host root. No DNS/CDN or legacy EC2/RDS resources.
terraform {
  required_version = ">= 1.9.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.0" }
  }
  backend "s3" {}
}

provider "aws" {
  region              = var.region
  allowed_account_ids = [var.account_id]
  default_tags { tags = { ManagedBy = "Terraform", Component = var.name } }
}

resource "aws_lightsail_instance" "host" {
  name              = var.name
  availability_zone = var.availability_zone
  blueprint_id      = "amazon_linux_2023"
  bundle_id         = "small_3_0"
  ip_address_type   = "ipv4"
  lifecycle { prevent_destroy = true }
}

resource "aws_lightsail_instance_public_ports" "host" {
  instance_name = aws_lightsail_instance.host.name
  # Bootstrap access only. Application remains loopback-bound until CDN acceptance.
  port_info {
    protocol  = "tcp"
    from_port = 22
    to_port   = 22
    cidrs     = [var.operator_cidr]
  }
}

resource "aws_lightsail_static_ip" "host" { name = "${var.name}-ip" }
resource "aws_lightsail_static_ip_attachment" "host" {
  static_ip_name = aws_lightsail_static_ip.host.name
  instance_name  = aws_lightsail_instance.host.name
}

resource "aws_s3_bucket" "assets" {
  bucket = var.assets_bucket
  lifecycle { prevent_destroy = true }
}
resource "aws_s3_bucket_public_access_block" "assets" {
  bucket                  = aws_s3_bucket.assets.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
resource "aws_s3_bucket_versioning" "assets" {
  bucket = aws_s3_bucket.assets.id
  versioning_configuration { status = "Enabled" }
}
resource "aws_s3_bucket_server_side_encryption_configuration" "assets" {
  bucket = aws_s3_bucket.assets.id
  rule {
    apply_server_side_encryption_by_default { sse_algorithm = "AES256" }
  }
}
resource "aws_s3_bucket_policy" "assets" {
  bucket = aws_s3_bucket.assets.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect    = "Deny", Principal = "*", Action = "s3:*"
    Resource  = [aws_s3_bucket.assets.arn, "${aws_s3_bucket.assets.arn}/*"]
    Condition = { Bool = { "aws:SecureTransport" = "false" } }
  }] })
}
resource "aws_s3_bucket_lifecycle_configuration" "assets" {
  bucket     = aws_s3_bucket.assets.id
  depends_on = [aws_s3_bucket_versioning.assets]
  rule {
    id     = "pending-only"
    status = "Enabled"
    filter { prefix = "pending/" }
    expiration { days = 1 }
    noncurrent_version_expiration { noncurrent_days = 1 }
    abort_incomplete_multipart_upload { days_after_initiation = 1 }
  }
}

resource "aws_ecr_repository" "backend" {
  name                 = "${var.name}-backend"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration { scan_on_push = true }
  lifecycle { prevent_destroy = true }
}

# Only metadata enters Terraform state. The operator writes the CA/ledger through
# Secrets Manager after apply, verifies retrieval, and removes local private files.
resource "aws_secretsmanager_secret" "ca" {
  name                    = "${var.name}/operator-ca"
  description             = "Operator-only signing key, CA certificate and issuance/revocation ledger"
  recovery_window_in_days = 30
  lifecycle { prevent_destroy = true }
}

resource "aws_rolesanywhere_trust_anchor" "host" {
  name    = var.name
  enabled = true
  source {
    source_type = "CERTIFICATE_BUNDLE"
    source_data { x509_certificate_data = var.ca_certificate }
  }
}

locals {
  purposes = toset(["app", "deploy", "backup"])
}
resource "aws_iam_role" "workload" {
  for_each = local.purposes
  name     = "${var.name}-${each.key}"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect = "Allow", Principal = { Service = "rolesanywhere.amazonaws.com" }
    Action = ["sts:AssumeRole", "sts:TagSession", "sts:SetSourceIdentity"]
    Condition = {
      ArnEquals = { "aws:SourceArn" = aws_rolesanywhere_trust_anchor.host.arn }
      StringEquals = {
        "aws:SourceAccount"               = var.account_id
        "aws:PrincipalTag/x509Subject/CN" = "${var.name}-${each.key}"
      }
    }
  }] })
}
resource "aws_rolesanywhere_profile" "workload" {
  for_each         = local.purposes
  name             = "${var.name}-${each.key}"
  enabled          = true
  duration_seconds = 900
  role_arns        = [aws_iam_role.workload[each.key].arn]
}
resource "aws_iam_role_policy" "app" {
  role = aws_iam_role.workload["app"].id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["s3:GetObject", "s3:PutObject"],
    Resource = ["${aws_s3_bucket.assets.arn}/pending/*", "${aws_s3_bucket.assets.arn}/assets/*"] },
    { Effect = "Allow", Action = "s3:DeleteObject", Resource = "${aws_s3_bucket.assets.arn}/pending/*" }
  ] })
}
resource "aws_iam_role_policy" "deploy" {
  role = aws_iam_role.workload["deploy"].id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = "ssm:GetParameter",
    Resource = "arn:aws:ssm:${var.region}:${var.account_id}:parameter${var.parameter_prefix}/*" },
    { Effect = "Allow", Action = "ecr:GetAuthorizationToken", Resource = "*" },
    { Effect = "Allow", Action = ["ecr:BatchGetImage", "ecr:BatchCheckLayerAvailability", "ecr:GetDownloadUrlForLayer"],
    Resource = aws_ecr_repository.backend.arn }
  ] })
}
resource "aws_iam_role_policy_attachment" "backup" {
  role       = aws_iam_role.workload["backup"].name
  policy_arn = var.backup_writer_policy_arn
}
resource "aws_iam_role" "ssm" {
  name = "${var.name}-ssm"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect = "Allow", Principal = { Service = "ssm.amazonaws.com" }, Action = "sts:AssumeRole"
    Condition = {
      StringEquals = { "aws:SourceAccount" = var.account_id }
      ArnLike      = { "aws:SourceArn" = "arn:aws:ssm:${var.region}:${var.account_id}:*" }
    }
  }] })
}
resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.ssm.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

output "connection" {
  value = {
    name             = var.name, region = var.region, instance_ip = aws_lightsail_static_ip.host.ip_address
    assets_bucket    = aws_s3_bucket.assets.id, repository = aws_ecr_repository.backend.repository_url
    parameter_prefix = var.parameter_prefix, ca_secret = aws_secretsmanager_secret.ca.name
    anchor           = aws_rolesanywhere_trust_anchor.host.arn, ssm_role = aws_iam_role.ssm.name
    roles            = { for k, r in aws_iam_role.workload : k => r.arn }
    profiles         = { for k, p in aws_rolesanywhere_profile.workload : k => p.arn }
  }
}
