# 監視用の短期資格情報ファイル更新

Linux/Python 3.9以降向けの任意導入コンポーネント。IAM Roles Anywhereの `credential-process` で取得した900秒のセッションを、CloudWatch agent向けの共有資格情報ファイルへ発行する。既存EC2 instance profile、Terraform、配布workflowからは呼び出さない。CA発行・証明書更新・CRL・IAM権限・CloudWatch設定・外部通知をこのコンポーネントが作成することはない。

## 契約

- `refresh.py` は1回実行。成功は終了0、失敗・排他競合は終了1。helperのstdout/stderrや例外本文をログへ出さず、固定の結果コードのみ出力する。
- 設定は `config.example.json` の5項目すべてを指定する。helperは絶対パスの検証済み実行ファイル。環境変数からAWS認証・設定・proxyを継承しない。proxyやHSM等が必要な構成は別途対応が必要。
- 証明書世代ディレクトリには `certificate.pem` と `private-key.pem` を置く。`current` symlinkは許容し、実行開始時に世代を一度だけ解決する。世代内の2ファイルは通常ファイルとし、発行処理中は削除・上書きしない。鍵は実行ユーザー所有の0600。鍵と証明書の一致・CA/subject/用途・期限を、世代を公開する前に別工程で検証する。
- 出力ディレクトリは事前作成した絶対パス、実行ユーザー所有0700。symlink経由は拒否する。親ディレクトリと設定・helper・スクリプトは管理者が管理し、別ユーザーに書き込み権限を与えない。
- `refresh.lock` のflockで、手動実行を含め同じ出力先の同時更新を拒否する。競合したプロセスはstatusを書き換えない。稼働中にlockファイルを消さない。
- 応答のVersion、3つの資格情報値、タイムゾーン付きExpiration、残存780秒以上を検証する。改行など共有ファイルへ別項目を注入する値や、要求した有効期間を大きく超える値も拒否する。
- `credentials` は `[monitor]` 1プロファイルだけ。0600の同一ディレクトリ内一時ファイルをflush/fsync後、renameして公開する。親ディレクトリもfsyncする。helper失敗・タイムアウト・不正応答・rename前の書込み失敗では前回の正常ファイルを保持する。
- rename後のディレクトリfsyncやstatus書込みが失敗した場合、資格情報自体は既に更新されている可能性がある。終了は失敗にし、旧値へ戻さない。資格情報とstatusは2ファイルのトランザクションではない。
- 強制終了で残った `.refresh-*` は次回の排他取得後に削除する。秘密値を含み得るため、出力先を専用ディレクトリにする。core dumpは同梱serviceで無効化する。

## timerと有効期間

同梱timerは起動後15秒から、前回のservice起動を基準に120秒間隔（AccuracySec=1s）で動く。helperは45秒で打切り、service全体は60秒で打切る。同一serviceの実行は重複しない。失敗後もtimerが次回起動を試みる。

CloudWatch agent v1.300073.0の共有ファイルproviderは取得後10分キャッシュする。900秒セッションでは、`900 > 発行間隔の最大値 + 600 + 遅延余裕` を保つ必要があるため、発行周期120秒、受入残存780秒を候補値にしている。timerは厳密な時刻保証ではなく、更新失敗や長い停止でこの余裕は失われる。agentの版や更新周期を変えるときは再検証する。

根拠: [agentのprovider設定](https://github.com/aws/amazon-cloudwatch-agent/blob/v1.300073.0/cfg/aws/credentials.go)、[キャッシュの期限処理](https://github.com/aws/amazon-cloudwatch-agent/blob/v1.300073.0/cfg/aws/refreshable_shared_credentials_provider.go)、[AWS helper仕様](https://docs.aws.amazon.com/rolesanywhere/latest/userguide/credential-helper.html)。

## 導入する場合

1. 専用OSユーザー/グループ `abservice-monitor` を作る。rootで更新処理を常用しない。固定・検証済み版のhelperとPythonを用意する。
2. `refresh.py` を `/opt/abservice/monitor-credentials/refresh.py` へ、実値を埋めた設定を `/etc/abservice/monitor-credentials.json` へ管理者所有で配置する。設定と証明書の親は専用グループだけに読取り/通過を許可し、leaf秘密鍵は専用ユーザー所有0600。CA秘密鍵はホストへ配らない。実値・保管場所・担当者は非公開の運用リポジトリで管理する。
3. service/timerを `/etc/systemd/system/` に0644で配置し、`systemd-analyze verify`、`systemctl daemon-reload` を実行する。serviceが `/run/abservice-monitor` を0700で作る。`RuntimeDirectoryPreserve=yes` によりoneshot終了や停止では正常ファイルを消さず、再起動で `/run` が消えた場合は作り直す。
4. `systemctl start abservice-monitor-credentials.service` の成功と、秘密値を開かず `status.json` の結果/期限を確認する。初回発行に失敗した場合、agentを起動しない。
5. agentの `common-config.toml` に下記を明示する。コンテナへ渡す場合は資格情報ファイル単体でなく親ディレクトリを読み取り専用mountし、同じ数値UIDで0600を読めるようにする。leafのディレクトリをagentへmountしない。同一ホストの同一UIDだけでは鍵へのアクセス分離にならないため、コンテナ境界とmountを別途受け入れる。ホスト常駐agentを別UIDで動かす方式はこの雛形には含まない。
6. `systemctl enable --now abservice-monitor-credentials.timer`。ホスト再起動時も初回発行成功後にagentを起動する依存関係をagent側へ設定し、実ログの配送と資格情報更新を確認する。agentのunit/Composeへの配線はこの変更に含まない。

```toml
[credentials]
shared_credential_profile = "monitor"
shared_credential_file = "/run/abservice-monitor/credentials"
```

停止はtimerを先に止めてからserviceを止める。意図的に資格情報ファイルを削除する場合も両方停止後に行う。プロセス停止やファイル削除で既発行AWSセッションの権限は失効しない。緊急失効とagentのキャッシュからの回復は別の運用手順が必要。

## 状態と通知

`status.json` は `version`、`attempted_at`、`result`、`stage`、`code`、`last_success_at`、`credential_expires_at` を含む。失敗時も前回成功時刻/期限を引き継ぐ。秘密値・ARN・証明書情報を含めない。形式不明の旧statusからは時刻を引き継がない。

設定・取得・検証・公開の失敗はstatusへ書く。runtime/lockを利用できない場合、排他競合、status自体の書込み失敗、強制終了では新しいstatusがない場合がある。終了状態と最終成功時刻の古さを併せて監視する。statusだけで「いま配送できる」と判断しない。

この変更は通知を送らない。ホストの資格情報が壊れても検知できる外部からのログ鮮度監視、通知の受信確認、証明書/CRL期限の監視を導入条件とする。残存時間の余裕不足、欠落/重複、保持容量、停止・再起動後の配送、実機メモリ、ホスト喪失からの復旧も別途検証する。

## ローカル検査

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s infra/monitoring/credentials -p 'test_*.py' -v
systemd-analyze verify infra/monitoring/credentials/abservice-monitor-credentials.{service,timer}
```

専用のLinux検証環境では次のsystemd実行試験も行う。root権限で一意名のunitを `/run/systemd/system` へ置き、実行自体はテスト用に既存の非特権 `nobody` を使う。本番のユーザー設計には使わない。timer間隔のみ3秒へ短縮し、成功→失敗→次回回復、oneshot後の保持、空のruntimeからの再起動を確認する。finallyで自分のunitと `/run/ab-monitor-test-*` 専用領域を削除する。全ホストの再起動や本番周期の長時間試験ではない。

```sh
sudo python3 infra/monitoring/credentials/test_systemd.py
```

両試験とも偽のhelperと明示的に無効な資格情報を使用し、AWSへ接続しない。Roles Anywhereでの実発行、agentへの受渡し、採用OS実機の受入は別途必要。
