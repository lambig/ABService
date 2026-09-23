# 同居DBの保存先とホスト外の鮮度監視

独立したTerraform root。既存の `infra/` rootを適用せず、[cohost保存処理](../host/cohost/BACKUP.md)の保存先・保存用IAM policy・Lambdaによる監視・SNSメール通知だけを作る。ホスト、DB、CA、Roles Anywhere role/profile、アセットbucket、DNSは作らない。

非公開の運用repoに `aws_region`、`account_id`、`bucket_name`、`assets_bucket_name`、`alarm_email` を用意し、**他のrootとは異なるstate key**で実行する。

```sh
terraform -chdir=infra/cohost-backup init -backend-config=/secure/backup-backend.hcl
terraform -chdir=infra/cohost-backup plan -var-file=/secure/backup.tfvars -out=/secure/backup.plan
terraform -chdir=infra/cohost-backup apply /secure/backup.plan
```

保存先はSSE-S3、versioning、Public Access Block、TLS必須。`runs/`の14日expire・noncurrent後1日、未完了multipartの1日掃除を設定する。S3処理は非同期のため厳密な保持時間の上限ではない。bucketは `prevent_destroy` と `force_destroy=false` で誤削除を防ぐ。意図した廃止も保存データの移行・保持確認を先に行う。

`writer_policy_arn`をbackup専用workload roleに接続する。信頼条件・証明書の用意は運用側で行い、他の広いpolicyをそのroleへ付けない。policyはbackup prefixへの新規PUTと前提設定/asset version一覧の読取りだけを許可する。元asset bucketのversion保持・非公開設定は保存コマンドでも検査する。

## 有効化

1. SNS購読確認メールを承認する。以前の試験用topicの購読とは別。以後このtopicを他の運用通知にも再利用できる。
2. 初回正常配布後、独立したbackup認証で手動保存、ホスト外からの取得を成功させる。運用側のsystemd service/timerを設置する。
3. Lambdaを手動invokeし、`healthy=true`とCloudWatch指標を確認する。Lambda APIのHTTP 200だけでは成功判定しない。
4. `monitoring_enabled=true`でplan/applyする。初期値falseは準備用であり、実更新を蓄積する前に有効化する。保存用timerを有効化する変数ではない。
5. 代表的な異常と復帰、通知の受信を確認する。メール未受信を正常の証拠にしない。

## 判定と限界

15分ごとにS3をページングして最新の完了manifestを取得し、形式・時刻・ファイル参照と指定versionの存在/サイズを検査する。dump開始時刻が既定18時間より古い、完了記録がない、S3を読めない場合は `Unhealthy=1`。新しくuploadされた古いsnapshotは正常化しない。ログは理由コードと時刻だけで、manifest全体やSDKエラーを転載しない。

CloudWatchは15分×3期間中2期間の異常をSNSへ通知し、OK復帰も通知する。Lambda/EventBridge/IAM障害など指標自体が出ない状態は `breaching` とする。評価用の追加取得期間や配送遅延があるため、停止後30分などの厳密な検知SLAではない。24時間の損失許容に対して早めに警告し、運用者が再試行・必要時の更新停止を行う。

監視はdumpをダウンロードしない。GetObjectVersion権限はHeadObjectにも必要なため付与するが、コードはmetadataだけ読む。checksum照合・DB形式互換性・画像参照の確認は取得/復元検証で行う。アプリ/ホストの稼働監視や必要ログの退避を兼ねない。

料金対象は保存容量/API、毎月約2,880回のLambda実行（30日換算）、1 custom metric、1 alarm、少量の14日保持ログとSNS。無料枠を恒久費用として差し引かず運用側の全体見積もりへ含める。

検証: `python3 -m unittest discover -s infra/cohost-backup`、`terraform -chdir=infra/cohost-backup init -backend=false`、`validate`、`test`。mockでのplanはAWSの実配送・購読受信の証拠とは区別する。

参考: [Lambda Python runtime](https://docs.aws.amazon.com/lambda/latest/dg/lambda-python.html)、[CloudWatchの欠損評価](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/alarms-and-missing-data.html)。
