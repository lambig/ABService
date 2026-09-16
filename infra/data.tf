data "aws_caller_identity" "current" {}

# --- RDS (PostgreSQL) ---

# 資格情報の更新は keepers の値（tfvars の rotation 変数）を変えた apply で行う（#127）。戻す用の旧値は
# 別に保管しない（生成値は state に残る）ため「戻す」はもう一度更新すること。順序と断の扱いは
# infra/README.md「資格情報の更新」。
resource "random_password" "db" {
  length  = 32
  special = false

  keepers = {
    rotation = var.db_password_rotation
  }
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

  backup_retention_period = var.db_backup_retention_days

  # 消すときは最終スナップショットを残す。名は世代で一意にする（前の置換の最終スナップショットは残るので、同じ名では
  # 次の削除が止まる）。削除は state に入っている値で走るため、世代は置換より前の apply で入れておく
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.project_name}-db-final-${var.db_final_snapshot_generation}"

  # 常設の DB は消えない側に置く。作り直し（復元済みの内容で置き換える）は明示的に false にしてから。
  deletion_protection = var.db_deletion_protection

  # 復元済みの内容で作り直すときだけ入れる（infra/README.md「バックアップと復旧」）。作成のときにだけ効き、
  # その後に値を変えても消しても作り直しにはならない
  snapshot_identifier = var.db_main_snapshot_identifier

  lifecycle {
    ignore_changes = [snapshot_identifier]
  }

  tags = {
    Name = "${var.project_name}-db"
  }
}

# --- 復元（#130） ---
# 復元は常設の DB を上書きせず、別のインスタンスへ行う（現在の DB を消さない）。tfvars に復元点を入れると
# 現れ、消すと消える一時のインスタンス。内容を確かめたら db_active で接続先をこちらへ切り替え、稼働中の
# commit を再配布する。常設へ戻す手順は infra/README.md「バックアップと復旧」。
locals {
  db_restore_requested = var.db_restore_to_time != null || var.db_restore_snapshot_identifier != null
}

resource "aws_db_instance" "restored" {
  count = local.db_restore_requested ? 1 : 0

  identifier = "${var.project_name}-db-restored"

  instance_class    = var.db_instance_class
  storage_type      = "gp3"
  storage_encrypted = true

  # 復元直後のパスワードは復元点のもの。apply が生成値へ揃えるので、Parameter Store と食い違わない
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  multi_az               = false
  publicly_accessible    = false

  backup_retention_period = var.db_backup_retention_days
  skip_final_snapshot     = true
  deletion_protection     = false

  snapshot_identifier = var.db_restore_snapshot_identifier

  dynamic "restore_to_point_in_time" {
    for_each = var.db_restore_to_time == null ? [] : [var.db_restore_to_time]
    content {
      source_db_instance_identifier = aws_db_instance.main.identifier
      restore_time                  = restore_to_point_in_time.value
    }
  }

  lifecycle {
    # 復元点は作成のときにだけ効く。別の時点へやり直すときは一度消してから作る（接続先にしている最中に
    # 値を触って作り直しになるのを避ける）
    ignore_changes = [snapshot_identifier, restore_to_point_in_time]

    precondition {
      condition     = (var.db_restore_to_time == null) != (var.db_restore_snapshot_identifier == null)
      error_message = "Set exactly one of db_restore_to_time (point in time) or db_restore_snapshot_identifier (snapshot)."
    }
  }

  tags = {
    Name = "${var.project_name}-db-restored"
  }
}

# アプリケーションはEC2上からSSM Parameter Store経由でDB接続情報を取得する（Secrets Managerはコスト面で不採用）。
# backend の接続先。db_active で常設と復元済みのどちらかを選ぶ。切り替えは apply の直後に稼働中の commit を
# 再配布して反映する（deploy.sh が配布のたびに読む。#127 と同じ順序）。
resource "aws_ssm_parameter" "db_host" {
  name  = "/${var.project_name}/${var.environment}/db/host"
  type  = "String"
  value = var.db_active == "restored" ? one(aws_db_instance.restored[*].address) : aws_db_instance.main.address

  lifecycle {
    precondition {
      condition     = var.db_active == "main" || local.db_restore_requested
      error_message = "db_active = \"restored\" needs a restored instance: set db_restore_to_time or db_restore_snapshot_identifier."
    }
  }
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

  keepers = {
    rotation = var.admin_api_key_rotation
  }
}

resource "aws_ssm_parameter" "admin_api_key" {
  name  = "/${var.project_name}/${var.environment}/app/admin-api-key"
  type  = "SecureString"
  value = random_password.admin_api_key.result
}

# --- オリジンへの到達制限（#286） ---

# 自分のCloudFrontだけが付ける値。セキュリティグループが許すのはCloudFront共通の送信元範囲で、
# 他の配信も含まれるため、prefix listだけでは自分の配信に限定できない。
# CloudFrontのcustom headerとbackendの設定の両方へ同じ値を渡す。
resource "random_password" "origin_verify_token" {
  length  = 48
  special = false

  keepers = {
    rotation = var.origin_verify_token_rotation
  }
}

resource "aws_ssm_parameter" "origin_verify_token" {
  name  = "/${var.project_name}/${var.environment}/app/origin-verify-token"
  type  = "SecureString"
  value = random_password.origin_verify_token.result
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
    allowed_origins = var.serve_www ? ["https://${var.domain_name}", "https://www.${var.domain_name}"] : ["https://${var.domain_name}"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}

# バケット名はprodで必須（既定値へフォールバックさせない）。DB接続情報と同じ経路で渡す。
resource "aws_ssm_parameter" "assets_bucket" {
  name  = "/${var.project_name}/${var.environment}/assets/bucket"
  type  = "String"
  value = aws_s3_bucket.assets.bucket
}
