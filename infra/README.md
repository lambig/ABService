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

公開サイトの `public/robots.txt` はルートの `/robots.txt` として配布し、`/admin` と `/api` をクロール対象から除外する。これは検索公開後も公開ページだけを対象にするための案内であり、秘匿や認証の境界ではない。`X-Robots-Tag`、管理API認証、CloudFrontの振り分けを置き換えず、`robots.txt` 自体は誰でも取得できる前提にする。

実ドメイン・既存レコード・切替日時・復旧先は運用リポジトリが持つ。

## ロールバック（インフラ変更）

変更前の構成へ戻して plan し、実リソースに適用する。**state の旧バージョンを復元しても実リソースは戻らない**ため、通常の切り戻し手段にはしない。state の復元は state 自体を壊した場合の復旧で、現物との照合が必要。DB の migration と内容の復旧は [バックアップと復旧](#バックアップと復旧130) を参照。

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

## 監視と通知（#168）

異常の発見・原因追跡・復旧確認に要る最小限だけを持つ。定義は `monitoring.tf`、判断は [DECISIONS](../docs/DECISIONS.md)「監視はサービスの標準指標と公開 URL の合成監視で組む」。

| 見るもの | 経路 | 通知 |
| --- | --- | --- |
| backend のログ（prod は JSON） | Docker の `awslogs` ドライバ（`docker-compose.logs.yml`。`deploy.sh` が prod の compose に重ねる）→ CloudWatch Logs `/<project>/<environment>/backend`。保持は `log_retention_days` | `ERROR` の件数（metric filter）が5分に `error_log_alarm_count` 以上 |
| ホストのディスク・メモリ | CloudWatch agent（`user_data` が入れ、設定は `monitoring/cloudwatch-agent.json` を Parameter Store 経由で読む） | 使用率が閾値超え |
| EC2 / RDS | 標準指標 | ステータスチェック失敗、CPU、RDS の空きストレージと接続数 |
| 配信 | CloudFront の標準指標（us-east-1） | 5xx 率 |
| 公開 URL | Route53 ヘルスチェック（HTTPS で `/api/v1/albums` を引く。切替前は配信のドメイン名、切替後は正規ドメイン） | 失敗 |

通知先は SNS のトピックで、宛先は `alarm_email`（`terraform.tfvars`。リポジトリに書かない）。アラームのアクションはアラームと同じリージョンに要るため、トピックは主リージョンと us-east-1（CloudFront・Route53 ヘルスチェックのアラーム）の2つになり、宛先は同じメール。**購読の確認メールはトピックごとに1通届き、両方を踏むまで通知は届かない。** 復旧（OK）も同じ宛先へ通知する。閾値は `variables.tf` の既定を持ち、`tfvars` で上書きできる。

`/q/*`（readiness）は配信が `/api/*` しか流さないため外から引かず、公開 URL の合成監視で代える。ビルド・配布の失敗は GitHub Actions が通知し、公開世代の遅れは `node infra/release/frontend.mjs status` で見る（[release/README.md](release/README.md)）。

`user_data` の変更（agent の導入）は既存インスタンスには効かない（初回起動時にしか走らない）。`awslogs` ドライバは `mode: non-blocking` で、ログ配送のバックプレッシャー（宛先が遅い・一時的に届かない）ではアプリの書き出しを止めず、バッファが溢れたら捨てる。ログの欠落よりアプリの停止を避ける側に倒している。ドライバの初期化失敗（ロググループが無い・権限が無い・接続できない）はこの範囲外で、コンテナの起動失敗になる。ロググループと権限は IaC が先に作る。

実 AWS では、購読の確認、各アラームを故障注入で1度は鳴らして届くこと、復旧通知が出ることを確認し、結果を運用リポジトリへ記録する（#168 の受け入れ）。ダッシュボードと JVM 内部の指標（`/q/metrics`）の CloudWatch への送出は持たない。

## 管理者APIキー（#116）

管理操作（Command系API・管理向けQuery API）は `Authorization: Bearer <APIキー>` を要求する。キーはTerraformが生成し、Parameter Store の `/<project>/<environment>/app/admin-api-key`（SecureString）に保存される。`deploy.sh` がこれを取得して backend コンテナへ `ADMIN_API_KEY` として渡すため、デプロイ側の追加設定は不要。

管理画面や手動操作で値が必要な場合は Parameter Store から取得する（値はリポジトリに置かない）。

```bash
aws ssm get-parameter --name "/<project>/<environment>/app/admin-api-key" \
  --with-decryption --query 'Parameter.Value' --output text --region ap-northeast-1
```

更新は下の「資格情報の更新」に従う（Parameter Store を直接書き換えても、次の apply で Terraform の値へ戻る）。

## 初期データの投入（#373）

公開時に載せる文言・作品・記事・カバー画像は、SQL ではなく**管理API経由**で入れる。仕組みは
[packages/seed-loader](../packages/seed-loader/README.md)（投入ディレクトリの形・dry-run・中断からの再開）が持ち、
投入する内容と実行の手順は運用リポジトリ（#375）が持つ。投入先は配信のオリジン（`/api/*`）で、鍵は上の Parameter Store から取る。

```bash
SEED_API_BASE_URL=https://<配信のドメイン> ADMIN_API_KEY=<鍵> \
  npm run load -w abservice-seed-loader -- --dir <運用リポジトリの seed/> --dry-run
```

`--dry-run` は書き込みを送らずに計画だけを出す。外すと同じ計画どおりに送る。空の DB に対する通しのリハーサルを
本番より前に1回行う。

## オリジンへの到達制限（#286）

EC2のセキュリティグループが許すのは`com.amazonaws.global.cloudfront.origin-facing`の範囲で、これは**CloudFront全体**の送信元であり、他人のdistributionも含む。prefix listだけでは自分の配信に限定できず、別のdistributionが同じEC2を指せばWAFと`/api/*`の振り分けを経ずにbackendへ届く。

Terraformが生成した値（`random_password.origin_verify_token`）をCloudFrontの`custom_header`（`X-Origin-Verify`）とParameter Storeの`/<project>/<environment>/app/origin-verify-token`（SecureString）の両方へ渡し、backendが`OriginVerificationFilter`で照合する。一致しない要求は本文なしの403で拒む。

**コンテナ自身からの`/q/*`は検査しない。** compose のhealthcheckがloopback経由で引くため。この緩和は、同じコンテナの中のプロセスが管理エンドポイントへ到達できることを意味する。外から`/q/*`へ届く経路は、CloudFrontが`/api/*`しか流さないことと、この検査の両方で塞ぐ。

値の更新はCloudFrontとbackendの両方を同時に切り替えられないため、切り替えの瞬間に断が生じる。順序は下の「資格情報の更新」。

## 資格情報の更新（#127）

管理APIキー・DBパスワード・オリジン識別値は、どれも Terraform の `random_password` が生成元、Parameter Store（識別値は CloudFront の custom header も）が供給先、稼働中の backend コンテナが消費者。`deploy.sh` は配布のたびに Parameter Store から読むため、**Parameter Store を更新しただけでは稼働プロセスは変わらず、Parameter Store を手で書き換えても次の apply で Terraform の値へ戻る。** 3点を揃える手順を1つに固定する。

**契機は tfvars の rotation 変数。** `admin_api_key_rotation` / `db_password_rotation` / `origin_verify_token_rotation` の値（日付など、更新のたびに違う文字列）を変えて apply すると、対応する `random_password` だけが再生成され、供給先が新しい値になる。値は運用リポジトリの tfvars が持つので、いつ何を更新したかがそちらの履歴に残る。`terraform apply -replace` を手で打つ経路は使わない。

更新は 1 つずつ、次の順で行う。

1. tfvars の該当する rotation 変数を変え、plan で **その `random_password` と供給先（Parameter Store。識別値は CloudFront、DBパスワードは RDS も）だけ**が変わることを確かめて apply する
2. **直後に** backend を再配布する。`.github/workflows/deploy.yml` を `workflow_dispatch` で起動し、`commit_sha` に**いま稼働している backend の commit** の full SHA を渡す（ビルドせず、その commit のイメージを新しい値で起動し直す）。稼働中の commit は、**Deploy の run（attempt）のうち job「Build, push, and deploy backend」が success の最新のもの**の Summary「Backend image」の `sha-<full SHA>` から読む。見るのは job の結果であって workflow 全体の結論ではない——backend の job は frontend の job より先に動くため、frontend だけ失敗して run 全体が failure でも backend はその SHA で動いている。逆に Summary は SSM 配布の前に書かれるため、backend の job が failure の run の Summary は使わない。frontend の配布記録（`current.json` の codeSha）も使わない——frontend だけの切り戻しで backend と別の commit を指すことがあり、それを渡すと backend までその commit へ戻る
3. 反映を確かめる（下表）。結果は運用リポジトリの証跡へ記録する

| 資格情報 | apply が変えるもの | 断 | 確かめること |
| --- | --- | --- | --- |
| 管理APIキー | Parameter Store | 再配布までは旧キーが有効。再配布で backend が再起動し**全セッションが失効**する（DECISIONS 22） | 旧キーで 401、新キーで管理画面に入れる。ローダ・SSG など機械側は Parameter Store から新しい値を取り直す |
| DBパスワード | RDS の master password（`apply_immediately` に関わらず**即時**）と Parameter Store | apply の瞬間から再配布までの間、backend の既存接続は生きるが新規接続は認証に失敗しうる。readiness が落ちうるため利用の少ない時間に行い、apply の直後に再配布する | 再配布後の readiness が 200。ログに認証失敗が続いていない |
| オリジン識別値 | CloudFront の custom header と Parameter Store | CloudFront の反映（数分）と再配布のどちらが先でも、一致しない間は `/api/*` が 403 になる（DECISIONS 35）。停止を許容する時間帯に行う | 公開 API が 2xx に戻る。識別値なし・誤った識別値の要求が 403 のまま |

**戻し方**: 戻す用の旧値を別に保管しないため「戻す」は**もう一度更新する**こと（生成値は Terraform の state には残る。state の保護は従来どおりで、そこから旧値を取り出して戻す手順は持たない）。再配布が失敗して古いプロセスが残った場合、管理APIキーとオリジン識別値は旧値のまま動き続けるので、再配布をやり直す。DBパスワードは RDS 側が先に変わっているため、backend が新規接続できないまま止まりうる。再配布を直して実行するか、もう一度 rotation を変えて apply と再配布を揃える。

3つを同時に変えない。1つ変えるごとに再配布して確かめる。実環境で更新して確かめる工程は運用リポジトリの手順が持つ。

## バックアップと復旧（#130）

復旧は「全部を同じ commit へ戻す」操作ではない。戻す単位が 6 つあり、それぞれの記録の所在が違う。

| 単位 | 記録と保持 | 戻し方 |
| --- | --- | --- |
| backend のコード（イメージ） | ECR に `sha-<full SHA>` で直近 10 件 | Deploy の `workflow_dispatch`（[ロールバック（backendデプロイ）](#ロールバックbackendデプロイ)） |
| DB のスキーマ（Flyway の版） | DB の `flyway_schema_history` | 前進のみ。下記 |
| public / admin の成果物 | release バケットに世代ごと（[release/README.md](release/README.md)） | Deploy frontend の `rollback` |
| 公開データの世代 | DB（不透明な UUID。大小比較しない） | `rebuild-public` で成果物を現在の DB に揃える |
| DB の内容 | RDS の自動バックアップ（`db_backup_retention_days` 日。任意時点への復元を含む）と削除時の最終スナップショット | 下記の順序 |
| 画像 | アセットバケット（versioning 有効）。確定後の `assets/` は**追記のみ**で、backend が消せるのは `pending/` だけ（`AssetPendingDelete`） | 戻すものが無い。過去の時点へ戻した DB が参照する画像は必ず在る |

**DB は常設のインスタンスを上書きせず、別のインスタンスへ復元して接続先を切り替える。** 常設は `deletion_protection` で守り（`db_deletion_protection`、既定 true）、`terraform destroy` や置換は明示的に false にしてからでなければ通らない。

1. **書き込みを止める。** 障害の発生時刻・最後に正常だった時刻を控え、いまの DB のスナップショットを取る（復元元を決めるため、いまの DB は消さない）
2. **復元点を tfvars に入れて apply する。** `db_restore_to_time`（RFC3339 の UTC。自動バックアップの窓の中）か `db_restore_snapshot_identifier` のどちらか一方。`aws_db_instance.restored` が常設と同じサブネットグループ・SG で現れ、パスワードは apply が生成値へ揃える。plan にこのインスタンスと Parameter Store 以外の変更が出たら apply しない。backend の接続先はまだ常設のまま
3. **内容を確かめる。** EC2 から復元済みインスタンスへ接続し（Session Manager。`psql` はコンテナで動かす）、`flyway_schema_history` の最終版が稼働中の backend の migration に収まること、作品・記事・サイト文言の件数と、参照している画像キーがバケットに在ることを見る
4. **接続先を切り替える。** `db_active = "restored"` で apply し、**直後に**稼働中の backend の commit を再配布する（[資格情報の更新](#資格情報の更新127) の手順 2 と同じ。deploy.sh が配布のたびに `db/host` を読む）。backend の起動時に Flyway が不足分の migration を適用するので、復元点が古いぶんはここで前進する
5. **公開面を揃える。** 復元で撤回済みの内容が「公開」に戻っていないかを**管理画面で確認してから** `rebuild-public` を実行する。公開データの世代は不透明な値なので、この確認は機構で代替されない。復元より前の成果物への `rollback` は世代が一致しないため拒否される
6. **常設へ戻す。** 復元済みインスタンスのスナップショットを取り、`db_deletion_protection = false` で apply したうえで、`db_main_snapshot_identifier` にそのスナップショットを入れて常設を置換する（`terraform apply -replace=aws_db_instance.main`）。常設が出来たら `db_active = "main"` で apply して再配布し、復元点の変数を消して復元済みインスタンスを消し、保護を true に戻す。`db_main_snapshot_identifier` は作成のときにだけ効き、以後は変えても消しても置換にならない

**Flyway は前進のみ。** backend を過去の commit へ戻せるのは、その commit が持つ migration が DB の `flyway_schema_history` に収まる範囲だけ。DB の方が先へ進んでいる（戻したい commit に無い版が適用済み）なら、旧イメージへ戻すだけでは復旧にならず、互換性のある forward fix か、この節の DB の復元を選ぶ。`baseline-on-migrate` は使わない（[スキーマ移行（Flyway）](#スキーマ移行flyway)）。

**RPO / RTO の見立て。** 任意時点への復元はトランザクションログを 5 分ごとに取るため、失うのは最大でその程度。所要は復元（`db.t4g.micro` で十数分）+ apply + 再配布 + 照合。目標値と実測は運用リポジトリが持つ。クロスリージョンの複製は持たない（1 リージョンの喪失は受け入れる）。

検査は `infra/tests/recovery.tftest.hcl`（既定で常設 1 台が保護され、復元点で 2 台目が同じ網の位置に現れ、`db_active` で接続先が動き、復元点のない切り替えと 2 つの復元点の同時指定は plan で止まる。backend の削除権限が `pending/` に限られる）。実環境で復元して確かめる工程は運用リポジトリの手順が持つ。

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

### 静的ページの404（#125）

公開・管理の2つのbehaviorだけに `static_page_404`（Lambda@Edge、origin-response）を結び付ける。書換え後のHTMLキーが404なら、そのオリジンのビルド済み404本文をHTTP 404で返す。公開は `404.html`、管理は `admin/404.html`。HEADは同じステータス・表現ヘッダーで本文を返さない。404応答は `Cache-Control: no-store` とし、未存在URLへの応答が新しいページの配布後も残ることを避ける。

- S3はListBucket権限がないと未存在キーも403になる。静的2バケットのOACに限り、当該distributionのSourceArn条件で `s3:ListBucket` を許可する。ルート要求はindex.htmlに書き換え、query stringはオリジンへ渡さないため、閲覧者向けの一覧取得経路は作らない。アセット・releaseバケットの権限は広げない。
- 403・5xxを未存在とみなさない。API・アセットのbehaviorには接続せず、distribution共通のcustom error responseも置かない。欠落したJS/CSS/画像はHTMLに置換しない。直接の `/404.html`・`/admin/404.html` は存在する静的ファイルとして200で取得できる。
- LambdaはviewerのHost・query・要求パスをS3へ渡さず、Terraformがパッケージに含めたオリジンと固定キーの対応だけを使う。実行ロールはその2オブジェクトのGetObjectと当該アカウントの各リージョンへのログ出力のみ。404本文を取得できない・形式不正・512 KiB超の場合は、内部情報を含めない503を返す。
- 関数はus-east-1の番号付きversionを使用する。Node.js 22ランタイム同梱のAWS SDK v3を使い、依存パッケージを別途インストールせずTerraformのarchive providerでzipを生成する。SDKのminor版はランタイム更新に従うため、ランタイム更新時も受け入れを再確認する。環境変数やレイヤーには依存しない。
- 未存在ページのオリジン応答時にはLambda実行とS3 GetObjectが増える。実環境での遅延・費用・権限・リージョン複製・キャッシュの受け入れはABAffairsに記録する。通常のActions配布はTerraformをapplyしないため、IaCの適用とフロント成果物の配布は別工程になる。

既存環境へ導入するときは、先に両フロントの404成果物を配布し、その後にこのTerraform構成を適用する。関数の関連付け・OACポリシー変更の伝播が完了してから、公開/管理の未存在URL、非公開化後の旧URL、GET/HEAD、APIのProblem Details、欠落したアセットを確認する。初回構築でバケットが空の間や関数伝播中の応答を受け入れ完了としない。Terraform実行主体にはLambda作成・公開・関連付け、IAM PassRoleおよびLambda@Edgeのサービスリンクロール作成に必要な権限が必要。関数を廃止するときは先に関連付けを外し、複製削除を待ってから削除する。

ローカル/CIでは `node --test infra/functions/*.test.mjs infra/release/*.test.mjs` と `terraform test`、E2Eの `static-404.spec.ts` を実行する。E2Eも本番の応答変換関数を使い、S3取得だけをローカルファイルへ置き換える。AWS実環境の伝播や実行権限まで実証するテストではない。

仕様の参照元: [S3 GetObjectの403/404](https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetObject.html)、[Lambda@Edgeの制約](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/lambda-at-edge-function-restrictions.html)、[エッジ関数の組合せとヘッダー](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/edge-function-restrictions-all.html)、[エラー応答のキャッシュ](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/custom-error-pages-expiration.html)。

### 配信のセキュリティヘッダー（#240）

`security-headers.tf` がpublic/admin/API/assetsの全behaviorへレスポンスヘッダーポリシーを関連付ける。既存のnoindexポリシーは `moved` でadminへ引き継ぐ。公開の `public_indexing_enabled` はX-Robots-Tagだけを切り替え、検索公開後もセキュリティヘッダーを維持する。admin/APIは常にnoindex、assetsには新たな検索制限を加えない。ステータス・本文・Content-Type・キャッシュ・CORSはこのポリシーで置き換えない。

| ヘッダー | 設定 |
| --- | --- |
| Content-Security-Policy | report-onlyではなく強制。許可値の正は `headers/security.json` |
| Strict-Transport-Security | max-age=31536000。未確認のサブドメインへ広げず、includeSubDomains/preloadは付けない |
| X-Content-Type-Options | nosniff |
| Referrer-Policy | strict-origin-when-cross-origin |
| X-Frame-Options / frame-ancestors | public/adminはSAMEORIGIN / self（管理の記事プレビューを許可）、API/assetsはDENY / none |

CSPはdefault-srcをnoneにし、公開・管理のスクリプト/CSSをselfに、書体CSSをfonts.googleapis.com、書体実体をfonts.gstatic.com、画像をself/dataに限定する。SoundCloudは元URLのsoundcloud.com等ではなく、共有URL生成器が出す `https://w.soundcloud.com` を両画面のframe-srcに許可する。管理画面だけは同一オリジンのプレビューフレームと、管理APIへの通信・当該アセットバケットのregional endpointへの署名付きPUTを許可する。S3全体へのワイルドカードは使わない。API/assetsの文書内リソースはdefault-src noneで許可しない。

**現段階ではscript/styleのunsafe-inlineを許可する。** 公開の書体待ちスクリプト、Astroの島の初期化、生成CSS・UIのstyle属性が必要とするためで、厳格なスクリプトCSPによるXSS防止が完了したという意味ではない。unsafe-eval、HTMLのイベント属性（script-src-attr）、object、base変更は許可しない。記事HTMLのサニタイズも引き続き必要。unsafe-inlineの除去には生成物と結び付けたhash等の別設計が必要になる。公開プレビュー自身のmeta CSP（script-src none）はHTTPヘッダーとの積で働くため緩まない。SoundCloudの子文書内の取得先を親のmedia-srcへ列挙しない。実音源の可聴性や外部サービス側の将来変更は別の実環境受け入れで確認する。

E2E配信も同じJSONから強制CSPを返す。ローカル/CIはサイト・API・MinIOが別オリジンのため、`stack.siteBaseUrl`、`stack.backendBaseUrl`、`E2E_UPLOAD_ORIGIN`（既定 `http://localhost:9000`）だけを検査環境用に追加する。最後の値はbackendがブラウザへ払い出す署名URLのoriginと一致させる。配信サーバー自身が使う `E2E_ASSET_ORIGIN` と混同しない。これらのローカル許可値は本番Terraformに入れない。

検査は `node --test infra/headers/*.test.mjs`、`terraform test` と実スタックE2E。E2Eでは公開表示・書体待ち・管理ログイン・画像PUT・記事プレビュー・404を確認し、任意の外部スクリプト/通信/フレーム、eval、イベント属性の拒否をブラウザのsecuritypolicyviolationで確認する。SoundCloudの許可検査にはネットワーク境界のテスト文書を使い、実音源の再生成功とは扱わない。

実AWSへはまだ適用していない。#129の準備配布後、全behaviorの成功/エラー/キャッシュ応答、同一オリジンAPI、署名付きS3 PUT/CORS、書体・画像・実SoundCloud・記事プレビューを確認し、結果をABAffairsへ記録する。設定を無条件に緩める前に違反したdirectiveと取得先を特定する。運用の適用・切り戻し時は、ブラウザへ既に記憶されたHSTSがポリシーを外すだけでは消えない点も考慮する。

参照: [CloudFrontレスポンスヘッダーポリシー](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/understanding-response-headers-policies.html)、[CSPのディレクティブと複数ポリシー](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy)。

## ロールバック（backendデプロイ）

イメージのタグは commit ごとに `sha-<full SHA>` の1つだけで、移動するタグ（`latest`）は発行しない。リポジトリは IMMUTABLE で、**同じ commit のイメージは二度は作らない**——その commit のタグが既に在れば、通常の配布でもビルドと push を飛ばして在るものを配る（同じ commit の再ビルドは同じ実体にならないため）。ECRのライフサイクルポリシーはこの接頭辞のタグ付きイメージを直近10件保持し、発行する接頭辞と保持する接頭辞が揃っていることは `scripts/check-deploy-image-tag.sh` が CI で突き合わせる（ずれると、規則の対象外のイメージが数に入らず溜まり続ける）。ホストへ渡す参照はタグではなく **digest**（`<repo>@sha256:…`）。タグと digest は Actions のログと Summary に、稼働中のコンテナの image ID と digest は `deploy.sh` の出力（`running image:`）に出る。

障害時は`.github/workflows/deploy.yml`を`workflow_dispatch`で手動起動し、`commit_sha`に直前の正常なcommitのfull SHAを指定して再デプロイする（再ビルドは行わず、ECRの既存イメージをそのままEC2へpull・再起動するだけなので数十秒で完了する）。戻るのは**その commit に最初に配った実体**で、戻せるのは保持されている直近10件の commit まで。期限切れで消えた commit を指定すると、ビルドせずに止まる。ロールバック後、mainブランチの履歴は`git revert`で追随させる（force-push・履歴書き換えはしない）。

手動起動は`commit_sha`を必須とし、既存イメージの再デプロイだけを行う。イメージのタグもEC2へ配る`deploy.sh`・`docker-compose.prod.yml`も、そのcommitから決まる（戻すのはイメージだけで手順は現在のまま、という組み合わせを作らない）。新しいcommitを本番へ出す経路はmainへのpush（＋CI成功）だけで、手動起動から検査していないcommitをビルドして出すことはできない。

## frontend 配布・内容更新

コードの配布、公開内容だけの再ビルド、保存済み成果物からの切り戻しは [release/README.md](release/README.md)。実値の設定と実環境の受け入れ結果は運用リポジトリが持つ。
