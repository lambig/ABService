# 同居DBのホスト外保存

`backup.py` は `deploy.py` の保護されたstateと同じ排他lockを使い、PostgreSQLの整合した論理dumpをS3へ保存する。稼働DBを変更する復元コマンドは持たない。

## 保存するものと前提

- `db.dump`: `pg_dump -Fc --no-owner --no-acl`。DB内の整合したsnapshotを取得し、`pg_restore -l`で目次を検査する。
- `assets.json`: dump後に取得した `assets/` の全object versionとdelete markerの一覧。bucket / key / VersionId / ETag / size /時刻を残す。
- `manifest.json`: 上記2ファイルのSHA-256・VersionId・サイズ、保守的なsnapshot時刻、最後の正常配布のGit commit/image digest、PostgreSQL image、DB名と所有者、Flyway履歴の所在、直近の配布試行。**両ファイルの保存後にだけ**作る完了記録。Flyway履歴そのものはdump内の同じsnapshotに含め、別時点のDB照会結果を重ねない。

アプリの確定画像は追記のみで削除されず、S3側で全世代を保持する前提。画像はすでにホスト外にあるため、このコマンドは実体を複製せずversionを対応付ける。古いversionやdelete markerも記録するので、通常のversion付き削除・置換の後も実体を取得できる。versionの永久削除、versioning停止、アカウント全体の喪失を防ぐ別拠点コピーではない。管理者による公開画像の書換えを通常運用にしない。

元のアセットバケットと別のバックアップバケットは、ともにversioningと4種類のPublic Access Blockを有効にする。公開画像のversionを期限切れ/低頻度のアーカイブへ移すlifecycleは拒否する。アセット側は既存の `pending/` 掃除など、明示したlifecycle設定を持つこと。タグ等の複合filterは安全側に拒否する場合がある。

dump/目録をSHA-256付き・SSE-S3暗号化・条件付き新規PUTで保存する。1ファイル5GBを上限とし、超過時は失敗して前の復元点を残す。大きいDB向けmultipart転送やDB以外のvolumeコピーは対象外。保存世代のlifecycle、頻度、鮮度通知、必要容量と費用は運用側で決める。

## 準備と実行

Python 3.9以上、AWS CLI v2、Docker/Compose、PostgreSQL 15が必要。専用AWS認証を指定し、SSM Agentやアプリの資格情報は使わない。`current.json`がある初回正常配布後に有効にする。アプリが停止・配布が失敗していても、DBが読めれば保存を続ける。manifestの正常配布版と直近試行・Flyway履歴を併せて読む。

設定は秘密を含まない次の3項目。実値は非公開の運用リポジトリで管理する。

```json
{"region":"us-east-1","bucket":"example-backup-bucket","prefix":"cohost"}
```

```sh
python3 infra/host/cohost/backup.py --config /etc/example/backup.json \
  create --state-dir /var/lib/example/cohost
```

保存ロールの権限は、両bucketの `s3:GetBucketVersioning` / `s3:GetBucketPublicAccessBlock`、アセット側の `s3:GetLifecycleConfiguration` / `s3:ListBucketVersions`、バックアップprefixへの `s3:PutObject`。アセットやバックアップの削除、backup内容の読取権限は不要。鍵/トークン、Compose全体、DBパスワード、ロールのパスワードを保存物・stdoutへ転載しない。

`backup-status.json`は直近試行と最後に完了したmanifestの所在を持つ。途中失敗で前の成功を置き換えず、未完了のdumpだけがS3へ残っても完了扱いにしない。stdoutには時刻付きの成功/失敗を出し、詳細エラーは秘密を含み得るため転載しない。OSから強制終了された場合はstatusがrunningのまま残り得るので、outcomeだけでなく最後のsnapshot時刻を確認する。ローカルの一時dumpは保護されたstate配下に作る。

## ホストを失った場合の取得と復元

運用者など**ホストとは独立した認証**で以下を行う。読取側はbackup prefixの `s3:ListBucket` / `s3:GetObject` / `s3:GetObjectVersion`、asset versionの `s3:GetObjectVersion`を持つ。

```sh
python3 infra/host/cohost/backup.py --config /etc/example/backup.json \
  freshness --max-age-hours <operator-threshold>
python3 infra/host/cohost/backup.py --config /etc/example/backup.json \
  fetch --manifest-key <chosen-manifest-key> --manifest-version <chosen-version-id> \
  --destination /secure/new-recovery-directory --assets
```

`freshness`はS3の最新完了manifestを読み、**upload時刻ではなくdump開始前のsnapshot時刻**で判定する。古い/存在しない/読めない場合は非ゼロ終了。通知や書込み停止は行わない。別ホストや運用者からも実行し、保存周期を超える失敗を放置しない。

`fetch`は既存ディレクトリへの上書きを拒否し、dumpと目録のSHA-256を照合する。`--assets`を付けると保存した全versionを取得し、size/ETagを照合する。ローカル画像名は `sha256(key + NUL + VersionId)`。元のkey/versionはassets.jsonに残り、S3キーを直接ローカルパスにしない。途中失敗した取得先は未完了として扱い、別の新規ディレクトリで再試行する。

復元は新しい空のDBで行う。manifestのDB所有者を非superuserとして再作成し、パスワードは保管元から安全に再取得するか再発行する。その所有者のDBへ `pg_restore --no-owner --no-acl --role=<owner> --exit-on-error` で復元し、Flyway履歴と代表データ・参照画像を確認してからアプリを接続する。DBと画像の形式が読めれば、追加の照合や復元コードは障害に応じて用意できる。

この取得確認は、画像のアプリ内表示、全件参照整合性、全体の復旧時間を保証しない。DBの形式互換性とアプリのmigration互換性を、imageを戻す操作とは分けて確認する。

仕様参考: [PostgreSQL pg_dump](https://www.postgresql.org/docs/15/app-pgdump.html)、[S3 PutObject](https://docs.aws.amazon.com/cli/latest/reference/s3api/put-object.html)。
