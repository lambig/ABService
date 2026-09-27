# Opt-in storage for verified audio. No CDN/OAC, browser CORS, public ACL or expiry.
variable "private_audio_bucket" {
  description = "Dedicated private audio bucket, distinct from published assets; null leaves audio infrastructure absent."
  type        = string
  default     = null
  validation {
    condition = var.private_audio_bucket == null ? true : (
      can(regex("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$", var.private_audio_bucket)) &&
      var.private_audio_bucket != var.assets_bucket
    )
    error_message = "Use a distinct DNS-safe bucket name (letters, digits and hyphens), or null."
  }
}

locals {
  private_audio = var.private_audio_bucket == null ? {} : { enabled = var.private_audio_bucket }
}

resource "aws_s3_bucket" "private_audio" {
  for_each = local.private_audio
  bucket   = each.value
  lifecycle { prevent_destroy = true }
}
resource "aws_s3_bucket_public_access_block" "private_audio" {
  for_each                = local.private_audio
  bucket                  = aws_s3_bucket.private_audio[each.key].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
resource "aws_s3_bucket_ownership_controls" "private_audio" {
  for_each = local.private_audio
  bucket   = aws_s3_bucket.private_audio[each.key].id
  rule { object_ownership = "BucketOwnerEnforced" }
}
resource "aws_s3_bucket_versioning" "private_audio" {
  for_each = local.private_audio
  bucket   = aws_s3_bucket.private_audio[each.key].id
  versioning_configuration { status = "Enabled" }
}
resource "aws_s3_bucket_server_side_encryption_configuration" "private_audio" {
  for_each = local.private_audio
  bucket   = aws_s3_bucket.private_audio[each.key].id
  rule {
    apply_server_side_encryption_by_default { sse_algorithm = "AES256" }
  }
}
resource "aws_s3_bucket_policy" "private_audio" {
  for_each = local.private_audio
  bucket   = aws_s3_bucket.private_audio[each.key].id
  policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect    = "Deny", Principal = "*", Action = "s3:*"
    Resource  = ["arn:aws:s3:::${each.value}", "arn:aws:s3:::${each.value}/*"]
    Condition = { Bool = { "aws:SecureTransport" = "false" } }
  }] })
}
resource "aws_iam_role_policy" "private_audio" {
  for_each = local.private_audio
  name     = "private-audio"
  role     = aws_iam_role.workload["app"].id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    # HeadObject needs ListBucket to distinguish a missing key from denied access.
    # The bucket is audio-only; no version listing, ACL changes or deletion.
    { Effect = "Allow", Action = "s3:ListBucket", Resource = "arn:aws:s3:::${each.value}" },
    { Effect = "Allow", Action = ["s3:GetObject", "s3:PutObject"],
    Resource = "arn:aws:s3:::${each.value}/audio/verified/*" }
  ] })
}
output "private_audio_bucket" {
  value = var.private_audio_bucket
}
