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

デプロイは**mainへのpushに対するCIが成功したときだけ**自動実行される（ビルド→ECR push→SSM Run Command経由でEC2上の`/opt/abservice/deploy.sh`を実行しpull・再起動）。対象はそのCIが検査したcommitのSHAに固定されるため、CI完了後にmainが進んでいても、検査していないcommitが出ることはない。GitHub ActionsはOIDC連携で一時認証情報を取得するため、長期のAWSアクセスキーは発行・保存しない（`aws_iam_openid_connect_provider.github_actions`）。

`AWS_DEPLOY_ROLE_ARN`未設定の間は`deploy.yml`のjobがskipされ、CIが成功しても何も実行されない。上表のAction variables設定後、次回のCI成功から自動的に有効化される。

## 管理者APIキー（#116）

管理操作（Command系API・管理向けQuery API）は `Authorization: Bearer <APIキー>` を要求する。キーはTerraformが生成し、Parameter Store の `/<project>/<environment>/app/admin-api-key`（SecureString）に保存される。`deploy.sh` がこれを取得して backend コンテナへ `ADMIN_API_KEY` として渡すため、デプロイ側の追加設定は不要。

管理画面や手動操作で値が必要な場合は Parameter Store から取得する（値はリポジトリに置かない）。

```bash
aws ssm get-parameter --name "/<project>/<environment>/app/admin-api-key" \
  --with-decryption --query 'Parameter.Value' --output text --region ap-northeast-1
```

ローテーションは Parameter Store の値を更新し、backend を再デプロイ（再起動）して反映する。

## DB接続情報（#117）

RDSの接続先とパスワードはTerraformが Parameter Store へ保存する（`/<project>/<environment>/db/host` `.../port` `.../name` `.../username`、パスワードのみ SecureString の `.../password`）。`deploy.sh` がこれらを取得して backend コンテナへ `DB_URL` / `DB_REACTIVE_URL` / `DB_USERNAME` / `DB_PASSWORD` として渡す。

backend の prod プロファイルはこれらに既定値を持たないため、注入が漏れた状態ではローカル向けの値にフォールバックせず起動に失敗する。

## アセット配信（#136）

画像アセットは管理画面が backend から署名付きURLを受け取り、S3（`aws_s3_bucket.assets`）へ直接 PUT する。実体は backend／CloudFront を経由しないため、サイズ上限はアプリ側の検証（`abservice.assets.max-bytes`）だけで決まる。

- 配信は CloudFront の `/assets/*` ビヘイビア経由（OAC で S3 を読み取り、バケットは非公開のまま）。オブジェクトキーの接頭辞を `assets/` に揃えているため `origin_path` は使わない
- 確定後のアセットはキーが一意（UUIDv7）で内容が変わらないため長期キャッシュ設定（`default_ttl` 1日 / `max_ttl` 1年）
- クロスオリジンの PUT を許可するため、バケットに CORS（`allowed_methods = ["PUT"]`、オリジンはサイトのドメイン）を設定している
- backend の実行ロールには assets バケットへの `GetObject` / `PutObject` / `DeleteObject` / `ListBucket` を付与済み（署名付きURLの発行に追加権限は不要）

## ロールバック（backendデプロイ）

ECRのライフサイクルポリシーにより直近10件のタグ付きイメージが保持される。障害時は`.github/workflows/deploy.yml`を`workflow_dispatch`で手動起動し、`image_tag`に直前の正常なタグ（gitのshort SHA）を指定して再デプロイする（再ビルドは行わず、ECRの既存イメージをそのままEC2へpull・再起動するだけなので数十秒で完了する）。ロールバック後、mainブランチの履歴は`git revert`で追随させる（force-push・履歴書き換えはしない）。

手動起動は`image_tag`を必須とし、既存イメージの再デプロイだけを行う。新しいcommitを本番へ出す経路はmainへのpush（＋CI成功）だけで、手動起動から検査していないcommitをビルドして出すことはできない。

## 未着手・依存関係

- frontend-admin/frontend-publicの静的ビルド成果物をS3へアップロードする手順は#125の対象
