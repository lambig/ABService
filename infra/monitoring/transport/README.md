# 監視資格情報イベントのファイル配送（任意導入）

[publisher](../credentials/README.md) のJSONイベントを専用ファイルへ出力し、CloudWatch agentで収集するテンプレート。既存のEC2配布・Docker awslogs経路には接続しない。アプリログ収集、AWSリソース作成、metric filter・alarm・通知は含まない。実環境の値と導入順序は非公開の運用リポジトリで管理する。

## 導入契約

systemd、logrotate、Docker Compose v2を備えたLinuxホストと、既存の専用ユーザー `abservice-monitor` が必要。agentはdigest固定し、publisherと同じUID/GIDで実行する。秘密鍵・証明書ディレクトリはコンテナへ渡さず、共有資格情報ディレクトリのみ読み取り専用で渡す。単一ファイルではなくディレクトリをmountし、原子的な置換後も新しい資格情報を読めるようにする。

1. publisherの導入・手動更新を確認し、設定変更中はそのtimer/serviceを止める。
2. `tmpfiles.conf` を `/etc/tmpfiles.d/abservice-monitor.conf` へroot所有で設置し、`systemd-tmpfiles --create /etc/tmpfiles.d/abservice-monitor.conf` を実行する。出力ファイルはサービス起動前に必要。ディレクトリ0700・ファイル0600・所有者を確認する。
3. `publisher-file.conf` を `/etc/systemd/system/abservice-monitor-credentials.service.d/file.conf` として設置する。この選択でJSON出力先はjournalからファイルへ変わる。systemd自身の起動失敗等は引き続きjournalで確認する。
4. `credentials.logrotate` を `/etc/abservice/monitor-logrotate.conf` へroot所有で設置する。同梱logrotate service/timerを `/etc/systemd/system/` へ置く。二重実行を避けるため `/etc/logrotate.d/` には同じ設定を追加しない。
5. `systemctl daemon-reload` 後、publisherを一度正常終了させ、ログと共有資格情報を確認してから両timerを有効化する。
6. 下表のホストディレクトリを用意する。`agent.example.json` を設定ディレクトリの `agent.json` とし、region・log group・streamの3箇所の `REPLACE_*` を実値へ置換する。`common-config.toml` も同じ場所へ設置する。log groupは事前作成し、保持期間とIAMの対象を別途制限する。この設定から保持期間は変更しない。
7. 環境変数を用意し、`docker compose -f infra/monitoring/transport/compose.yml up -d` を実行する。translateの正常終了後にagentが起動する。agentとpublisher両方のログ、CloudWatchへの到着を確認する。

| 環境変数 | 内容・権限 |
| --- | --- |
| `MONITOR_UID` / `MONITOR_GID` | 専用ユーザーの数値ID |
| `MONITOR_CONFIG_DIR` | root管理、専用ユーザーが読める設定ディレクトリ。コンテナ内はread-only |
| `MONITOR_CREDENTIALS_DIR` | `/run/abservice-monitor`。publisher管理0700、read-only |
| `MONITOR_LOG_DIR` | `/var/log/abservice-monitor`。0700、read-only |
| `MONITOR_STATE_DIR` | 永続ディスク上の専用ディレクトリ。専用ユーザー所有0700、read-write。生成設定と読み取り位置を保存 |

bind元がない場合は起動を失敗させ、Dockerによるroot所有ディレクトリの自動作成を禁止する。agentのルートFSはread-only、capabilityなし、メモリ上限256MiB。agent自身の診断ログはDocker local driverで2MiB×3ファイルに制限する。これはホスト全体の容量制限ではなく、対象実機でのメモリ受入も別途必要。

agentの既定は `restart: "no"` とし、異常終了やDocker daemon再起動による自動起動を無効にする。停止後は資格情報の準備を確認して手動で起動する。ホスト起動時にはtmpfiles → publisher初回成功 → agentの順序を別途構成する。Composeのdepends_onはtranslatorだけを待ち、ホストのpublisher成功を待たない。`/run` は再起動で消えるため、restart policyだけを有効にしてもこの順序を保証できない。自動起動・障害時の再起動は、この順序と空runtimeからの復旧を保証する運用側の仕組みと合わせて導入し、実機で確認する。

## ローテーションと配送の限界

15分ごとにlogrotateを実行し、dailyまたは1MiB超でrename/createする。copytruncateを使わず、次回のoneshot実行が新ファイルを開く。archiveは圧縮せず最大7世代、maxageは7日。判定はtimer実行時、日数による削除はrotation時なので、サイズ・保持時間の厳密な上限ではない。書込み中のrotationでは旧ファイルへその実行の末尾が入ることがある。

agentは現行の `credentials.jsonl` だけを収集し、ファイル自動削除を無効にする。archiveをwildcardに含めず、更新時刻を変えた古いarchiveが通常収集の対象にならないようにする。稼働中のrename追従と、停止中に複数回rotationされたarchiveの全走査は同じではない。

- agentが稼働したままの一時的な配送障害では再試行を行うが、無制限の耐久キューではない。障害中にrotationすると旧ファイルの送信処理が停止し、未送信batchが破棄される場合もある。agentが動いていても欠落しないとは限らない。
- 永続stateは読み取り位置を保存する。CloudWatchが受理したこととstate更新の原子性やexactly-onceを保証しない。プロセス強制終了・長時間障害・ディスク喪失では欠落や重複があり得る。
- agent停止中にrotationされたarchiveは、再起動だけでは配送されないことがある。配送障害中のrotationも含め、外部から最終成功時刻の古さを監視し、ホストの状態と保存済みarchiveを確認する。archiveが保持期限を超える前に保全し、必要な再送は通常収集と分けて対象・重複を照合する。遅延した成功イベントの時刻を書き換えない。

欠落・遅延があっても監視を正常扱いしないalarm設計の検証は別途必要。M<Nへの変更だけでは、現在のlog timestampで古い成功を再送した場合の誤警報を解消する保証はない。このテンプレートの導入だけで通知経路の受入完了とはしない。

## 成功時刻と再送経路

`timestamp_format` はpublisherのJSONにある `"last_success_at": "` に続くUTC時刻を秒まで読み取る。配送先のlog event timestampは `last_success_epoch * 1000` に一致させる。小数秒を指標と同じ整数秒へ揃え、後ろにある小数秒や `+00:00` を解析対象に含めない。`timezone: "UTC"` と `trim_timestamp: false` により、JSON本文を変更せず、資格情報期限が先に並んでも成功時刻を選ぶ。

これは同梱publisherの出力契約専用であり、任意のJSON形式・ローカル時刻用ではない。キー後の空白、キー名、UTC以外のoffsetなどを変更する場合は設定と実agent試験を合わせて更新する。[AWSの設定仕様](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-Agent-Configuration-File-Details.html)では、形式を省略すると現在時刻を使う。時刻を持たない失敗イベントは成功時刻として使わず、成功用metric filterから除外する。失敗イベントのlog timestampを正確な発生時刻として扱わない。

archiveの再送は次のように分離する。

1. 保全したarchiveから必要なイベントを選び、通常ログディレクトリとは別の0700ディレクトリに `replay.jsonl`（0600、専用ユーザー所有）として置く。通常の `credentials.jsonl` へ追記・差替えしない。
2. `replay.agent.example.json` のregionと再送専用log group/streamを設定し、別の設定ディレクトリへ `agent.json` として置く。`common-config.toml` も配置する。再送専用groupは通常groupと必ず分け、鮮度監視用metric filterを付けない。streamだけの分離では、groupに付くmetric filterを回避できない。
3. 同じComposeを別project名・別 `MONITOR_CONFIG_DIR` / `MONITOR_LOG_DIR` / `MONITOR_STATE_DIR` で手動起動する。通常agentの読み取り位置や生成設定を共有しない。共有資格情報の準備を先に確認する。
4. 送受信ID、本文と元の成功時刻、配送先group、欠落・重複・AWSの受理可能な経過時間を照合して終了する。過去ログの再送だけで通常監視のALARMを解除しない。通常publisherが新たに成功し、通常経路へ届くことを復旧条件にする。

再送用のgroup作成・IAM制限・metric filter不在確認・ログ選別は運用側の手順であり、このテンプレートは自動で保証しない。今回のローカル試験はagentの送信内容を検査するもので、実AWSのアラーム再検証は別途必要。

## ローカル実行試験

専用の使い捨てLinux環境で実行する。root権限で一意名のunit・ディレクトリ・Docker networkを作成する。非特権のテスト実行には既存 `nobody` を使うが、本番には専用ユーザーを使う。

```sh
sudo python3 infra/monitoring/transport/test_transport.py
```

固定imageがなければDockerが取得する。実行コンテナは外部へ接続できないinternal networkに限定し、ホスト上の模擬Logs APIへ送る。資格情報・証明書・ARNは明示的に無効なfixtureで、AWSリソースを作らない。確認対象は次の通り。

- systemdのJSONファイル出力・所有者/権限、read-only mount、稼働中のrename rotation
- 実publisherの成功時刻と配送envelopeの一致、古い成功・小数秒あり/なし・期限が先に並んだJSON、本文の保持
- 通常ログよりmtimeが新しいarchiveの収集除外、別ファイルから再送専用groupへの配送
- 配送先503から未rotationのイベントが回復すること、障害中のrotationによる欠落の観測とローカル保持、回復後の新ファイルから成功/失敗イベントが到着すること
- stateを保持した再起動で、未rotationの新イベントを収集
- 停止中の複数rotationから再起動した場合の欠落/重複を記録し、未配送イベントのローカル保持を確認
- 7世代の保持と一時リソースの削除

この試験は実AWSの認証・IAM・CloudWatch障害・実機再起動・長期容量を検証しない。HTTP模擬APIはローカル試験専用で、運用設定へ持ち込まない。
