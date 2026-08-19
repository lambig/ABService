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

## ロールバック（インフラ変更）

Terraform適用は`terraform plan`で差分を確認してから`apply`する運用を基本とする。誤適用時は直前のstateバージョン（S3バケットのバージョニングで保持）に戻すか、該当リソースのみ設定を戻して再度`apply`する。スタック全体の破棄は`terraform destroy`（RDSは`skip_final_snapshot = false`のため最終スナップショットが残る）。

## CI/CD（backendデプロイ、#128）との連携

`apply`後、以下のoutputをGitHubリポジトリのAction variables（Settings > Secrets and variables > Actions > Variables）に設定する。`.github/workflows/deploy.yml`がこれらを参照する。

| Terraform output | GitHub variable |
|---|---|
| `github_actions_deploy_role_arn` | `AWS_DEPLOY_ROLE_ARN` |
| `ecr_repository_url`のリポジトリ名部分 | `ECR_REPOSITORY` |
| `ec2_instance_id` | `EC2_INSTANCE_ID` |

デプロイはmainへのpushで自動実行される（ビルド→ECR push→SSM Run Command経由でEC2上の`/opt/abservice/deploy.sh`を実行しpull・再起動）。GitHub ActionsはOIDC連携で一時認証情報を取得するため、長期のAWSアクセスキーは発行・保存しない（`aws_iam_openid_connect_provider.github_actions`）。

`AWS_DEPLOY_ROLE_ARN`未設定の間は`deploy.yml`のjobがskipされ、mainへのpushでも何も実行されない。上表のAction variables設定後、次回のmainへのpushから自動的に有効化される。

## ロールバック（backendデプロイ）

ECRのライフサイクルポリシーにより直近10件のタグ付きイメージが保持される。障害時は`.github/workflows/deploy.yml`を`workflow_dispatch`で手動起動し、`image_tag`に直前の正常なタグ（gitのshort SHA）を指定して再デプロイする（再ビルドは行わず、ECRの既存イメージをそのままEC2へpull・再起動するだけなので数十秒で完了する）。ロールバック後、mainブランチの履歴は`git revert`で追随させる（force-push・履歴書き換えはしない）。

## 未着手・依存関係

- frontend-admin/frontend-publicの静的ビルド成果物をS3へアップロードする手順は#125の対象
