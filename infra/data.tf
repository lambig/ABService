data "aws_caller_identity" "current" {}

# --- RDS (PostgreSQL) ---

resource "random_password" "db" {
  length  = 32
  special = false
}

resource "aws_db_instance" "main" {
  identifier     = "${var.project_name}-db"
  engine         = "postgres"
  engine_version = var.db_engine_version

  instance_class    = var.db_instance_class
  allocated_storage = var.db_allocated_storage_gb
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  multi_az               = var.db_multi_az
  publicly_accessible    = false

  backup_retention_period   = var.db_backup_retention_days
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.project_name}-db-final"

  tags = {
    Name = "${var.project_name}-db"
  }
}

# アプリケーションはEC2上からSSM Parameter Store経由でDB接続情報を取得する（Secrets Managerはコスト面で不採用）。
resource "aws_ssm_parameter" "db_host" {
  name  = "/${var.project_name}/${var.environment}/db/host"
  type  = "String"
  value = aws_db_instance.main.address
}

resource "aws_ssm_parameter" "db_port" {
  name  = "/${var.project_name}/${var.environment}/db/port"
  type  = "String"
  value = tostring(aws_db_instance.main.port)
}

resource "aws_ssm_parameter" "db_name" {
  name  = "/${var.project_name}/${var.environment}/db/name"
  type  = "String"
  value = var.db_name
}

resource "aws_ssm_parameter" "db_username" {
  name  = "/${var.project_name}/${var.environment}/db/username"
  type  = "String"
  value = var.db_username
}

resource "aws_ssm_parameter" "db_password" {
  name  = "/${var.project_name}/${var.environment}/db/password"
  type  = "SecureString"
  value = random_password.db.result
}

# --- アプリケーション認証（#116） ---

# 管理操作（Command系・管理向けQuery）を保護する固定APIキー。値はTerraformが生成し、コード・tfvarsには置かない。
resource "random_password" "admin_api_key" {
  length  = 48
  special = false
}

resource "aws_ssm_parameter" "admin_api_key" {
  name  = "/${var.project_name}/${var.environment}/app/admin-api-key"
  type  = "SecureString"
  value = random_password.admin_api_key.result
}

# --- S3 ---

resource "aws_s3_bucket" "frontend_public" {
  bucket = "${var.project_name}-frontend-public-${data.aws_caller_identity.current.account_id}"

  tags = {
    Name = "${var.project_name}-frontend-public"
  }
}

resource "aws_s3_bucket_public_access_block" "frontend_public" {
  bucket                  = aws_s3_bucket.frontend_public.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket" "frontend_admin" {
  bucket = "${var.project_name}-frontend-admin-${data.aws_caller_identity.current.account_id}"

  tags = {
    Name = "${var.project_name}-frontend-admin"
  }
}

resource "aws_s3_bucket_public_access_block" "frontend_admin" {
  bucket                  = aws_s3_bucket.frontend_admin.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# #136（アセットアップロード基盤）: アルバムカバー画像等のアップロード先。
resource "aws_s3_bucket" "assets" {
  bucket        = "${var.project_name}-assets-${data.aws_caller_identity.current.account_id}"
  force_destroy = var.asset_bucket_force_destroy

  tags = {
    Name = "${var.project_name}-assets"
  }
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

  versioning_configuration {
    status = "Enabled"
  }
}

# 受け入れ前（pending/）の実体は、確定時にbackendが配信対象へコピーして削除する。確定まで至らなかった
# アップロード（検査で拒否される前に離脱した等）はそのまま残るため、期限切れで自動的に消す。配信対象（assets/）は
# 確定した実体を保持し続けるため、対象は pending/ に限る。
resource "aws_s3_bucket_lifecycle_configuration" "assets" {
  bucket = aws_s3_bucket.assets.id

  rule {
    id     = "expire-pending-uploads"
    status = "Enabled"

    filter {
      prefix = "pending/"
    }

    expiration {
      days = 1
    }

    # バケットはversioning有効のため、削除しただけでは旧バージョンが残る。
    noncurrent_version_expiration {
      noncurrent_days = 1
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

# 管理画面はbackendが発行した署名付きURLへ直接PUTする（実体はbackendを経由しない）。
# PUT先はCloudFrontではなくS3のエンドポイントになるため、サイトのオリジンからのクロスオリジンPUTを許可する。
resource "aws_s3_bucket_cors_configuration" "assets" {
  bucket = aws_s3_bucket.assets.id

  cors_rule {
    allowed_headers = ["Content-Type"]
    allowed_methods = ["PUT"]
    allowed_origins = ["https://${var.domain_name}"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}
