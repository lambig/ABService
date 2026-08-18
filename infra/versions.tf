terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  backend "s3" {
    # bucket/key/region/dynamodb_table はリポジトリにコミットしない backend.hcl から注入する。
    # 初回セットアップ手順は README.md を参照。
    # 実行例: terraform init -backend-config=backend.hcl
  }
}
