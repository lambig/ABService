# infra（Terraform）

ABService v1.0 の AWS インフラ定義。EC2（backend常時起動）+ CloudFront（WAFアタッチ）+ RDS（PostgreSQL）+ S3（frontend配信2バケット・アセット1バケット）+ ECR（backendコンテナイメージ配布先）を単一のTerraform構成として管理する。構成の確定事項はこのファイルが正で、判断の理由は [../docs/DECISIONS.md](../docs/DECISIONS.md)、境界と経路は [../docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md) を参照。

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

## DNS の準備と切り替え

最初は `dns_cutover_enabled = false` で plan/apply する。証明書の DNS 検証レコードは作るが、apex / www の A・AAAA は管理しない。`serve_www` は証明書を準備する前に運用側で決める（既定は apex のみ。www のリダイレクトを意味しない）。

配信の受け入れ後、DNS の控えを取ってから `dns_cutover_enabled = true` の plan を確認する。A と AAAA は両方が対象。既存の同名・同型レコードがある場合、上書き許可で押し切らず、そのレコードだけを `terraform import` で採用して差分を見る。www が CNAME の場合は ALIAS と共存できないため、控えと切り戻し手順を準備して別途切り替える。

```sh
terraform import 'aws_route53_record.root[0]' '<zone-id>_<domain>_A'
terraform import 'aws_route53_record.root_ipv6[0]' '<zone-id>_<domain>_AAAA'
# www を採用する場合のみ（既存レコードが A/AAAA の場合）
terraform import 'aws_route53_record.www["A"]' '<zone-id>_www.<domain>_A'
terraform import 'aws_route53_record.www["AAAA"]' '<zone-id>_www.<domain>_AAAA'
terraform plan -out=cutover.tfplan
terraform apply cutover.tfplan
```

**旧構成を適用済みの場合**は、最初から `dns_cutover_enabled = true` を実値ファイルに入れる。`moved` が既存の root を `[0]` へ移す。false のままなら `prevent_destroy` が削除を拒否するので、稼働中の A レコードを黙って消すことはない。

**既に検索公開済みの環境では `public_indexing_enabled = true` も明示する。** 新しい既定falseのまま適用すると、DNSを維持していても公開ページへ `X-Robots-Tag: noindex, nofollow` が付く。既存環境の移行では、DNSと検索公開の両方を現状に合わせて実値ファイルへ設定し、planで公開側のレスポンスヘッダーポリシーを意図せず変更しないことを確認する。まだ検索公開していない環境はfalseを維持する。管理画面とAPIのnoindexは、どちらの場合も維持する。

**切り戻しで false にするだけではいけない。** `prevent_destroy` が削除を拒否する。運用側の控えから A/AAAA（採用時は www も）を戻し、Terraform にも復旧後の管理方針を反映する。管理から外す場合は対象の DNS レコードだけを `terraform state rm` し、false に戻した plan に DNS 操作が無いことを確かめる。state 全体は巻き戻さない。

公開サイトの検索対象化は `public_indexing_enabled` で切替と分ける。既定falseでは `X-Robots-Tag: noindex, nofollow` を付ける。受け入れ後にtrueにする。管理画面とAPIのnoindexは解除しない。noindexはアクセス制御ではない。

実ドメイン・既存レコード・切替日時・復旧先は運用リポジトリが持つ。

## ロールバック（インフラ変更）

変更前の構成へ戻して plan し、実リソースに適用する。**state の旧バージョンを復元しても実リソースは戻らない**ため、通常の切り戻し手段にはしない。state の復元は state 自体を壊した場合の復旧で、現物との照合が必要。DB の migration と内容の復旧は 復旧方針（#130） を参照。

## CI/CD（backendデプロイ、#128）との連携

`apply`後、以下のoutputをGitHubリポジトリのAction variables（Settings > Secrets and variables > Actions > Variables）に設定する。`.github/workflows/deploy.yml`がこれらを参照する。

| Terraform output | GitHub variable |
|---|---|
| `github_actions_deploy_role_arn` | `AWS_DEPLOY_ROLE_ARN` |
| `ecr_repository_url`のリポジトリ名部分 | `ECR_REPOSITORY` |
| `ec2_instance_id` | `EC2_INSTANCE_ID` |

デプロイは**mainへのpushに対するCIが成功したときだけ**自動実行される（ビルド→ECR push→SSM Run Command経由で、そのSHAの`infra/host/deploy.sh`と`docker-compose.prod.yml`をEC2の`/opt/abservice`へ配ってから実行しpull・再起動）。イメージを動かす手順も検査済みのcommitに揃うため、稼働中のホストが古い手順のまま残ることがない（`user_data`が置くのはDockerの準備とインスタンス固有の値`/opt/abservice/deploy.env`まで）。対象はそのCIが検査したcommitのSHAに固定されるため、CI完了後にmainが進んでいても、検査していないcommitが出ることはない。GitHub ActionsはOIDC連携で一時認証情報を取得するため、長期のAWSアクセスキーは発行・保存しない（`aws_iam_openid_connect_provider.github_actions`）。

デプロイの成否は、`deploy.sh`がcompose の healthcheck（readinessを引く）を待って決める。起動に失敗するか期限内にhealthyへ至らなければ、コンテナのログを出したうえで非0で終わり、SSMの実行もActionsも失敗する。

イメージはarm64のランナーでビルドし、push前に架構がarm64であることを確かめる（EC2は`data.aws_ami.al2023_arm64`のためamd64のイメージは動かせない）。EC2のbootstrap（`user_data`）が担うのはDockerとdocker composeプラグイン（版を固定し、配布されているsha256と突き合わせる）の導入と、`/opt/abservice/deploy.env`の配置まで。

`AWS_DEPLOY_ROLE_ARN`未設定の間は`deploy.yml`のjobがskipされ、CIが成功しても何も実行されない。上表のAction variables設定後、次回のCI成功から自動的に有効化される。

## 管理者APIキー（#116）

管理操作（Command系API・管理向けQuery API）は `Authorization: Bearer <APIキー>` を要求する。キーはTerraformが生成し、Parameter Store の `/<project>/<environment>/app/admin-api-key`（SecureString）に保存される。`deploy.sh` がこれを取得して backend コンテナへ `ADMIN_API_KEY` として渡すため、デプロイ側の追加設定は不要。

管理画面や手動操作で値が必要な場合は Parameter Store から取得する（値はリポジトリに置かない）。

```bash
aws ssm get-parameter --name "/<project>/<environment>/app/admin-api-key" \
  --with-decryption --query 'Parameter.Value' --output text --region ap-northeast-1
```

ローテーションは Parameter Store の値を更新し、backend を再デプロイ（再起動）して反映する。

## オリジンへの到達制限（#286）

EC2のセキュリティグループが許すのは`com.amazonaws.global.cloudfront.origin-facing`の範囲で、これは**CloudFront全体**の送信元であり、他人のdistributionも含む。prefix listだけでは自分の配信に限定できず、別のdistributionが同じEC2を指せばWAFと`/api/*`の振り分けを経ずにbackendへ届く。

Terraformが生成した値（`random_password.origin_verify_token`）をCloudFrontの`custom_header`（`X-Origin-Verify`）とParameter Storeの`/<project>/<environment>/app/origin-verify-token`（SecureString）の両方へ渡し、backendが`OriginVerificationFilter`で照合する。一致しない要求は本文なしの403で拒む。

**コンテナ自身からの`/q/*`は検査しない。** compose のhealthcheckがloopback経由で引くため。この緩和は、同じコンテナの中のプロセスが管理エンドポイントへ到達できることを意味する。外から`/q/*`へ届く経路は、CloudFrontが`/api/*`しか流さないことと、この検査の両方で塞ぐ。

値のrotationはCloudFrontとbackendの両方を同時に切り替えられないため、切り替えの瞬間に断が生じる。手順は#127で扱う。

## DB接続情報（#117）

RDSの接続先とパスワードはTerraformが Parameter Store へ保存する（`/<project>/<environment>/db/host` `.../port` `.../name` `.../username`、パスワードのみ SecureString の `.../password`）。`deploy.sh` がこれらを取得して backend コンテナへ `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` として渡す。backend は JDBC（Flywayが使う）とreactiveの接続URLをこのホスト・ポート・DB名から組み立てるため、用途ごとのURLを個別に渡すことはしない（両者が別のデータベースを指し得る形を残さない）。

backend の prod プロファイルはこれらに既定値を持たない。注入が漏れた状態では、`docker-compose.prod.yml` が `${VAR:?}` でコンテナを作る前に止める（空の値をそのまま渡すと、接続URLの式に空が埋まったまま起動してしまう。#330）。

## スキーマ移行（Flyway）

schemaの正はマイグレーションであり、起動時に適用される（`quarkus.flyway.migrate-at-start`）。**暗黙のbaselineは無効**（`baseline-on-migrate=false`）なので、Flywayの履歴テーブルを持たない非空のDBへ接続すると起動に失敗する。これは「本来適用すべきマイグレーションが飛ばされる」状態を検出するための設計で、失敗は想定どおりの挙動。

履歴を持たない既存DBを取り込む必要が生じた場合のみ、**一回限りの明示的な操作**としてbaselineする。

1. 対象DBの現在のschemaが、どのマイグレーション版まで適用済みの状態と等価かを確定させる
2. その版を `baselineVersion` として Flyway CLI で `baseline` を実行し、履歴テーブルを作る
3. アプリを起動し、以降のマイグレーションが順に適用されることを確認する

アプリの設定を一時的に `baseline-on-migrate=true` へ変えてこれを済ませることはしない（次回以降も暗黙baselineが効いてしまい、検出したい欠落を見逃す）。

## アセット配信（#136）

画像アセットは管理画面が backend から署名付きURLを受け取り、S3（`aws_s3_bucket.assets`）へ直接 PUT する。実体は backend／CloudFront を経由しないため、サイズ上限はアプリ側の検証（`abservice.assets.max-bytes`）だけで決まる。

- 署名付きURLの宛先は受け入れ前の接頭辞（`pending/`）で、配信対象（`assets/`）へは backend の確定処理がバケット内でコピーして移す。クライアントが配信キーへ書き込む経路は無い
- 配信は CloudFront の `/assets/*` ビヘイビア経由（OAC で S3 を読み取り、バケットは非公開のまま）。オブジェクトキーの接頭辞を `assets/` に揃えているため `origin_path` は使わない。`pending/` は配信パスの外にあるため CloudFront から到達しない
- 確定後のアセットはキーが一意（UUIDv7）で内容が変わらないため長期キャッシュ設定（`default_ttl` 1日 / `max_ttl` 1年）
- 確定に至らなかった `pending/` の実体はライフサイクル（`aws_s3_bucket_lifecycle_configuration.assets`）で1日後に期限切れにする。バケットは versioning 有効なので旧バージョンと未完了マルチパートも同時に掃除する
- クロスオリジンの PUT を許可するため、バケットに CORS（`allowed_methods = ["PUT"]`、オリジンはサイトのドメイン）を設定している
- backend の実行ロールには assets バケットへの `GetObject` / `PutObject` / `DeleteObject` / `ListBucket` を付与済み（署名付きURLの発行と確定時のコピーに追加権限は不要）
- バケット名はTerraformが Parameter Store の `/<project>/<environment>/assets/bucket` へ保存し、`deploy.sh` が backend コンテナへ `ASSETS_BUCKET` として渡す。prod プロファイルは既定値を持たないため、渡し漏れは起動失敗になる

## 静的サイト配信

公開サイトと管理画面は別々のS3バケットへ置き、CloudFront が経路で振り分ける（`default_cache_behavior` が `frontend_public`、`path_pattern = "/admin*"` の `ordered_cache_behavior` が `frontend_admin`）。

- **振り分けは経路を書き換えない。** ビューアが要求した経路がそのままオリジンのオブジェクトキーになるため、管理画面の成果物はバケットの `admin/` 接頭辞配下へ置く（`/admin/index.html` → キー `admin/index.html`）。`origin_path` では解けない（要求の前に足すため `/admin/admin/...` を引くことになる）
- 管理画面の Astro は同じ理由で `base: '/admin'` を宣言している（`frontend-admin/astro.config.mjs`）。宣言がないと資産の参照が `/_astro/...` になり、既定の振り分けで公開サイトのバケットへ流れる
- 公開サイトは `base` を持たず、バケット直下へ置く
- 管理画面のパターンは `/admin*`。`/admin/*` は末尾スラッシュのない `/admin` に一致せず、入口が公開サイトのバケットへ流れる

### 経路からオブジェクトキーへの解決

OAC 経由の S3 は REST エンドポイントで、ディレクトリ索引を持たない。`default_root_object` も配信直下にしか効かないため、`/albums/` や `/admin` はその綴りのままキーとして引かれて 403/404 になる。Astro の既定（`build.format: 'directory'`）は各ページを `index.html` として出すため、CloudFront Functions（viewer request、`aws_cloudfront_function.resolve_static_uri`）が綴りを合わせる。

- 最後の区間に `.` を含む経路（資産・`404.html`）は素通し、それ以外は末尾へ `index.html` を補う
- 結び付けるのは静的サイトの2つの振り分けだけ。`/api/*` へ結ぶと拡張子を持たない API の経路まで書き換わる
- **E2E の配信（`e2e/scripts/serve-app.mjs`）は同じファイルを読み、`handler` へ要求を通して解決する。** 写して並べると片方が黙って古くなり、検査が本番と違う解決で緑になる

## ロールバック（backendデプロイ）

ECRのライフサイクルポリシーにより直近10件のタグ付きイメージが保持される。障害時は`.github/workflows/deploy.yml`を`workflow_dispatch`で手動起動し、`commit_sha`に直前の正常なcommitのfull SHAを指定して再デプロイする（再ビルドは行わず、ECRの既存イメージをそのままEC2へpull・再起動するだけなので数十秒で完了する）。ロールバック後、mainブランチの履歴は`git revert`で追随させる（force-push・履歴書き換えはしない）。

手動起動は`commit_sha`を必須とし、既存イメージの再デプロイだけを行う。イメージのタグもEC2へ配る`deploy.sh`・`docker-compose.prod.yml`も、そのcommitから決まる（戻すのはイメージだけで手順は現在のまま、という組み合わせを作らない）。新しいcommitを本番へ出す経路はmainへのpush（＋CI成功）だけで、手動起動から検査していないcommitをビルドして出すことはできない。

## frontend 配布・内容更新

コードの配布、公開内容だけの再ビルド、保存済み成果物からの切り戻しは [release/README.md](release/README.md)。実値の設定と実環境の受け入れ結果は運用リポジトリが持つ。
