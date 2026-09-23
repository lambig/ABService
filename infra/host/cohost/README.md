# 単一ホストのアプリ＋PostgreSQL（opt-in）

`deploy.py` は Linux 上で digest 固定の backend と PostgreSQL 15 を起動する。
既存の EC2/RDS Terraform、`deploy.yml`、`docker-compose.prod.yml` は変更しない。
この経路はまだ既存 Actions/SSM 配布へ接続していない。検証済みソースをホストへ配置し、
管理者または SSM の明示コマンドとして実行する。コードとイメージは同じ検査済み SHA を使う。
本番配備、Free 配信、バックアップ完了を意味しない。

## ホストの準備

- Linux、Python 3、Docker Engine/Compose v2（検証環境は 27.3 / 2.29）。ビルドは別環境で行う。
- アプリと PostgreSQL **15** の `repository@sha256:...` を用意する。ホストとイメージの架構は配布時に照合する。
  イメージの取得元と検査済み commit の対応は配布側で確認する。このスクリプトは CI 成功の照会やビルドをしない。
- ホストの AWS CLI は専用の短期資格情報で Parameter Store を読めるようにする。
  必要な prefix の GetParameter、SecureString の復号、ECR pull だけを許可する。
  ECR は事前に `aws ecr get-login-password` を `docker login --password-stdin` へ渡してログインする。
  SSM Agent の資格情報をアプリへ転用しない。
- アプリ用 `auth_dir` に `[default]` の `config` と、Roles Anywhere の
  `credential_process` が使う署名helper・証明書・秘密鍵を配置する。
  config 内のパスはコンテナ内の `/run/abservice-auth/...` を使う。
  helper は取得元・版・checksum・CPU を確認し、コンテナの UID/GID 1000 が実行できるようにする。
  秘密鍵は UID 1000 に限定した読取り権限で置き、CA 秘密鍵は渡さない。
  ディレクトリ全体を read-only mount するため、ファイルの atomic replace による更新も反映される。
  [Java SDK の process credentials](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-process.html)
  を再利用し、固定の AWS access key は Compose に入れない。証明書の更新・失効・期限管理は運用側で行う。

Parameter Store から prefix 配下の次の値を取得する。DB password、admin-password、API key、origin token は SecureString にする。

| suffix | 用途 |
| --- | --- |
| `db/name`, `db/username`, `db/password` | アプリDBと非superuserの所有者。Flywayも同じ所有者を使用 |
| `db/admin-password` | PostgreSQL管理者。アプリとは異なるパスワード |
| `app/admin-api-key`, `app/origin-verify-token` | 既存の管理認証・origin検証 |
| `assets/bucket` | S3アセットバケット |

実環境の値・権限・投入データは非公開の運用リポジトリで管理する。
次は書式のみであり、digest/prefix等をそのまま利用できる例ではない。

```json
{
  "name": "example-cohost",
  "region": "us-east-1",
  "parameter_prefix": "/example/environment",
  "postgres_image": "postgres@sha256:<verified-postgres-15-digest>",
  "auth_dir": "/etc/example/app-auth",
  "bind_address": "127.0.0.1",
  "port": 8080
}
```

DBは内部ネットワークだけに接続し、5432を公開しない。backendのbind先は明示する。
外部からCloudFront originとして接続させる場合は、ホスト/クラウドのfirewallで送信元を限定し、
自配信のorigin tokenを必ず設定する。`/q/*` をインターネットへ公開する設定にしない。

## 初回と更新

rootまたは専用のDocker操作ユーザーで、保護されたstateディレクトリを使う。
秘密を含む生成Compose、`initialization.json`、`current.json`、`previous.json` は mode 0600、親は0700となる。
Docker操作権限のある利用者はコンテナ環境から秘密を読めるため、その権限も制限する。

```sh
python3 infra/host/cohost/deploy.py \
  --config /etc/example/cohost.json --state-dir /var/lib/example/cohost \
  --source <verified-full-commit-sha> --image <repository@sha256:digest> --initialize
```

通常更新は `--initialize` を外し、同じname/stateで実行する。
初回だけ専用ラベル付きvolumeを作り、既存volumeへの初期化は拒否する。
作成前にstateの絶対パス・固有ID・DB設定を`initialization.json`へ保存し、volumeにも同じIDを付ける。
この記録が無い別state-dirや、別パスへコピーしたstateで既存DBを採用することは拒否する。
stateの移設・紛失からの復旧は別の明示操作とし、`--initialize`で既存volumeを取り込まない。
通常配布ではvolumeを作り直さず、存在しなければ失敗する。
[external volume](https://docs.docker.com/reference/compose-file/volumes/)なのでComposeの削除操作でもDBは残る。
volumeはバックアップではなく、ホスト喪失で失われる。実データ更新前に別途ホスト外保存を有効にする。

配布は排他ロックを取り、値取得・image pullを終えてから起動する。
DB TCP readinessとアプリreadiness（migration/DB接続を含む）を待ち、稼働image IDを照合して成功にする。
OS再起動後はDocker起動と`unless-stopped`で復帰する。起動順序が変わってもアプリが再試行するため、
実ホストの再起動試験は別途必要。手動でstopしたサービスは明示的に再開する。

アプリ更新でDB接続値・DBイメージを変更することは拒否する。DBパスワード更新やPostgreSQL更新は、
保存を確保してDB内の状態も変える別操作として扱う。環境変数だけの変更では既存DBの認証は変わらない。
非rootのアプリDB所有者はアプリDB/schema内のmigrationは可能だが、role/DB作成やsuperuser権限は持たない。

## 失敗と手動復帰

`attempt.json` が直近の試行、`current.json` が最後にhealthyを確認した配布。
失敗した場合、currentは稼働中の版を保証しない。アプリのmigrationは既にDBを変えている可能性がある。
`previous.json` は異なるimageへ成功したときの直前版を保持する。imageのpruneは行わない。
復帰時は保存したsource/imageとDBスキーマの互換性を確認して、対応する検査済みスクリプトで再配布する。
自動rollbackやDBの巻き戻し・削除は行わない。

初回が途中で失敗した場合、volumeは残す。statusとDB初期化状態を確認し、`--initialize`なしで再試行する。
初回にまだhealthyになっていなくても、同じstate・固有ID・DB設定でのみ再試行できる。
volume作成前に失敗し、対象volumeが存在しない場合に限り、同じstateで`--initialize`を再実行できる。
initスクリプト途中の失敗では空volumeを再作成する判断が必要な場合があるが、データを確認せず削除しない。
出力には秘密を含むAWS/Dockerエラーやアプリログを転載しない。詳細はホスト上でのみ確認する。

ログは容量制限付きのDocker local driverを使う。必要ログのホスト外保存、バックアップ鮮度通知、
SSM/IAMの最終配線、実ホストの2GB性能と再起動、CDN/TLSの受け入れは運用側に残る。

## 検証

```sh
python3 -m unittest discover -s infra/host/cohost -p 'test_*.py'
python3 infra/host/cohost/check_runtime.py --image <locally-built-app-image>
```

後者はローカルの専用registryへテストイメージを登録し、実際のdigest配布経路を通す。
AWSは呼ばず、明示した無効なfixture値を使用する。初回起動・非superuser・データ更新・再起動・
異なるimageへの再配布・失敗時の記録・手動復帰・volume保持・stateの取り違え拒否を検査する。
署名付きアップロードURLを発行し、fixtureのaccess key/session tokenが署名に使われることも確認する。
URLへのアクセスや出力は行わない。実AWSや実機性能の保証ではない。
