# infra（Terraform）

ABService v1.0 の AWS インフラ定義。EC2（backend常時起動）+ CloudFront（WAFアタッチ）+ RDS（PostgreSQL）+ S3（frontend配信2バケット・アセット1バケット）+ ECR（backendコンテナイメージ配布先）を単一のTerraform構成として管理する。構成方針・確定事項は GitHub issue #126（および #132 トラッキング）を参照。

## 前提

- ドメインのRoute53ホストゾーンが作成済みであること（レジストラ側のネームサーバー委譲を含む。#129）
- 実際のドメイン名はこのリポジトリのどこにもハードコードしない。`terraform.tfvars` と `backend.hcl` はいずれも `.gitignore` 対象

## 状態管理の初期セットアップ（初回のみ・手動）

Terraformのstate自体を管理するS3バケットとロック用DynamoDBテーブルは、鶏卵問題を避けるためTerraform化せず手動で一度だけ作成する。

```bash
aws s3api create-bucket --bucket <your-terraform-state-bucket> --region ap-northeast-1 \
  --create-bucket-configuration LocationConstraint=ap-northeast-1
aws s3api put-bucket-versioning --bucket <your-terraform-state-bucket> \
  --versioning-configuration Status=Enabled
aws dynamodb create-table --table-name <your-terraform-lock-table> \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST
```

## セットアップ

```bash
cp terraform.tfvars.example terraform.tfvars   # domain_name等を実際の値に置き換える
cp backend.hcl.example backend.hcl             # state保存先バケット名等を実際の値に置き換える

terraform init -backend-config=backend.hcl
terraform plan
terraform apply
```

## ロールバック

Terraform適用は`terraform plan`で差分を確認してから`apply`する運用を基本とする。誤適用時は直前のstateバージョン（S3バケットのバージョニングで保持）に戻すか、該当リソースのみ設定を戻して再度`apply`する。スタック全体の破棄は`terraform destroy`（RDSは`skip_final_snapshot = false`のため最終スナップショットが残る）。

## 未着手・依存関係

- backendコンテナイメージ自体（Dockerfile、#121）は整備済み。ただしEC2の`user_data`はDockerランタイムの準備までに留めており、ECRからのpull・起動は含まない。CI/CD（#128）でイメージをECRへpushしたうえで、EC2上でpull・起動する手順を追加する
- デプロイ自動化（CI/CD、#128）は本構成の対象外
- frontend-admin/frontend-publicの静的ビルド成果物をS3へアップロードする手順は#125の対象
