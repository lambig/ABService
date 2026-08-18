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
