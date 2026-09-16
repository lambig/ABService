variable "project_name" {
  description = "リソース名の接頭辞として使うプロジェクト識別子"
  type        = string
  default     = "abservice"
}

variable "environment" {
  description = "環境識別子（タグ付け用）"
  type        = string
  default     = "production"
}

variable "aws_region" {
  description = "主リージョン（EC2/RDS/S3/VPC）"
  type        = string
  default     = "ap-northeast-1"
}

variable "vpc_cidr" {
  description = "VPCのCIDRブロック"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "EC2を配置するパブリックサブネットのCIDR（AZごとに1つ）"
  type        = list(string)
  default     = ["10.0.0.0/24", "10.0.1.0/24"]
}

variable "private_subnet_cidrs" {
  description = "RDSを配置するプライベートサブネットのCIDR（AZごとに1つ、RDSサブネットグループの要件で最低2AZ必要）"
  type        = list(string)
  default     = ["10.0.10.0/24", "10.0.11.0/24"]
}

variable "domain_name" {
  description = "サービスの公開ドメイン名。リポジトリにはコミットしない値のため terraform.tfvars（gitignore対象）でのみ指定する"
  type        = string
  sensitive   = true
}

variable "ec2_instance_type" {
  description = "backendを常時起動するEC2インスタンスタイプ（Graviton/ARM64）"
  type        = string
  default     = "t4g.small"
}

variable "ec2_root_volume_size_gb" {
  description = "EC2ルートボリュームサイズ（GB）"
  type        = number
  default     = 20
}

variable "db_engine_version" {
  description = "RDS PostgreSQLのエンジンバージョン（ローカルdocker-composeのpostgres:15系に合わせる）"
  type        = string
  default     = "15"
}

variable "db_instance_class" {
  description = "RDSインスタンスクラス"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage_gb" {
  description = "RDS割り当てストレージ（GB）"
  type        = number
  default     = 20
}

variable "db_backup_retention_days" {
  description = "RDS自動バックアップの保持日数"
  type        = number
  default     = 7
}

variable "db_multi_az" {
  description = "RDSをMulti-AZ構成にするか（EC2側も単一インスタンス常時起動の方針のため既定はfalse。可用性要件が上がった場合にtrueへ変更する）"
  type        = bool
  default     = false
}

variable "db_name" {
  description = "アプリケーションが接続するデータベース名"
  type        = string
  default     = "abservice"
}

variable "db_username" {
  description = "アプリケーション用DBユーザー名"
  type        = string
  default     = "abservice"
}

variable "cloudfront_price_class" {
  description = "CloudFrontの配信クラス（コストと配信エッジ範囲のトレードオフ）"
  type        = string
  default     = "PriceClass_200"
}

variable "github_repository" {
  description = "GitHub Actions OIDC連携の信頼範囲を絞るためのリポジトリ識別子（owner/repo）"
  type        = string
  default     = "lambig/ABService"
}

variable "backend_app_port" {
  description = "backendコンテナが公開するアプリケーションポート（CloudFrontオリジンのポートと一致させる）"
  type        = number
  default     = 8080
}

variable "asset_bucket_force_destroy" {
  description = "terraform destroy時にアセットバケットの中身ごと削除を許可するか（誤削除防止のため既定はfalse）"
  type        = bool
  default     = false
}

variable "dns_cutover_enabled" {
  description = "証明書・配信の準備とは別に、A/AAAA ALIAS を管理する。初回の準備は false。採用後に false に戻して切り戻さない"
  type        = bool
  default     = false
}

variable "serve_www" {
  description = "www も証明書・配信・CORS の対象にする。実環境の採否は運用リポジトリで決める"
  type        = bool
  default     = false
}

variable "public_indexing_enabled" {
  description = "公開サイトを検索対象にする。DNS切替と受け入れが終わるまで false"
  type        = bool
  default     = false
}

# --- 監視と通知（#168） ---

variable "alarm_email" {
  description = "アラームの通知先メールアドレス。運用側の値のため terraform.tfvars（gitignore対象）でのみ指定する。トピックはリージョンごと（主リージョンと us-east-1）にあり、購読の確認メールは2通届く。両方を踏むまで通知は届かない"
  type        = string
  sensitive   = true
}

variable "log_retention_days" {
  description = "backend のログ（CloudWatch Logs）の保持日数"
  type        = number
  default     = 30
}

variable "cpu_utilization_alarm_percent" {
  description = "EC2 の CPU 使用率（平均・5分×3回）がこれを超えたら通知する"
  type        = number
  default     = 80
}

variable "disk_used_alarm_percent" {
  description = "EC2 ルートボリュームの使用率がこれを超えたら通知する（旧世代イメージの滞留を拾う。#336）"
  type        = number
  default     = 80
}

variable "memory_used_alarm_percent" {
  description = "EC2 のメモリ使用率（平均・5分×3回）がこれを超えたら通知する"
  type        = number
  default     = 90
}

variable "rds_free_storage_alarm_bytes" {
  description = "RDS の空きストレージがこれを下回ったら通知する（既定 2GiB）"
  type        = number
  default     = 2147483648
}

variable "rds_connections_alarm_count" {
  description = "RDS の接続数がこれを超えたら通知する（db.t4g.micro の上限は 100 前後）"
  type        = number
  default     = 80
}

variable "error_log_alarm_count" {
  description = "backend の ERROR ログが5分にこの件数以上出たら通知する"
  type        = number
  default     = 3
}

variable "cloudfront_5xx_alarm_percent" {
  description = "配信の 5xx 率（5分平均）がこれを超えたら通知する"
  type        = number
  default     = 5
}

# --- 資格情報の更新（#127） ---
# 値を変えた apply で対応する random_password が再生成される。日付など、更新のたびに違う文字列を入れる。
# 戻す用の旧値は別に保管しない（生成値は state に残る）ため、「戻す」はもう一度更新すること。順序と断の扱いは infra/README.md。

variable "admin_api_key_rotation" {
  description = "管理APIキーの更新の契機。変えると再生成され、Parameter Store が新しい値になる。反映には backend の再配布が要る（全セッションが失効する）"
  type        = string
  default     = "initial"
}

variable "db_password_rotation" {
  description = "DB パスワードの更新の契機。変えると再生成され、RDS の master password は即時に、Parameter Store も新しい値になる。apply の直後に backend を再配布する"
  type        = string
  default     = "initial"
}

variable "origin_verify_token_rotation" {
  description = "オリジン識別値の更新の契機。変えると再生成され、CloudFront の custom header と Parameter Store が新しい値になる。backend の再配布まで /api/* に断が生じる"
  type        = string
  default     = "initial"
}

# --- バックアップと復旧（#130） ---
# 復元は常設の DB を上書きせず別インスタンスへ行い、db_active で接続先を切り替える。順序は infra/README.md。

variable "db_deletion_protection" {
  description = "常設の DB の削除保護。作り直し（復元済みの内容で置き換える）のときだけ false にする"
  type        = bool
  default     = true
}

variable "db_restore_to_time" {
  description = "復元済みインスタンスを作る復元点（RFC3339 の UTC、例 2026-09-16T00:00:00Z）。db_restore_snapshot_identifier とは同時に指定しない。消すとインスタンスも消える"
  type        = string
  default     = null
}

variable "db_restore_snapshot_identifier" {
  description = "復元済みインスタンスを作るスナップショット。db_restore_to_time とは同時に指定しない。消すとインスタンスも消える"
  type        = string
  default     = null
}

variable "db_active" {
  description = "backend の接続先。main は常設、restored は復元済みインスタンス（復元点の指定が要る）。変えた直後に稼働中の commit を再配布する"
  type        = string
  default     = "main"

  validation {
    condition     = contains(["main", "restored"], var.db_active)
    error_message = "db_active must be \"main\" or \"restored\"."
  }
}

variable "db_main_snapshot_identifier" {
  description = "常設の DB を復元済みの内容で作り直すときのスナップショット。作成のときにだけ効き、以後は変えても消しても作り直しにならない"
  type        = string
  default     = null
}

variable "db_final_snapshot_generation" {
  description = "常設の DB を消すときに残す最終スナップショットの名の世代（<project>-db-final-<世代>）。前の置換の最終スナップショットは残るので、置換のたびに今回だけの値へ変え、置換より前の apply で state に入れておく（同じ名では作れず削除が止まる）"
  type        = string
  default     = "initial"
}
