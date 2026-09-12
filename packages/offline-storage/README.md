# Offline asset storage PoC

会場準備の「音源がローカルに存在する」を、期待値の写しではなく実ファイルの観測から判定するためのadapter。既存のManifest/readiness契約を使い、プレイヤーやbackendの実装とは独立させる。

## 判断の理由と境界

- OPFSは候補検証。ファイル操作をasset IDの境界へ閉じ込め、呼び出し側へ保存パスを渡さない。同一asset IDでもサイズ・checksumが異なる内容を別のキーへ保存し、失敗した更新で旧世代を壊さない。パスは識別情報のSHA-256で作り、asset ID内のスラッシュや日本語をパスとして解釈しない。
- checksumはPoCで`sha256`・小文字hexに限定する。未対応方式を黙って信頼しない。これは配布元の真正性・音声形式・デコード可否の検査ではない。信頼するManifestとのバイト一致を検査する。
- Web Cryptoのdigestは全バッファを要するため、inventoryの検証は直列に行う。1音源分のメモリが必要で、大容量FLAC・Androidでのピークメモリと速度の採用評価は別途必要。
- 書き込み前のBlobと、close後の保存実体を検証する。read/inspectでも実体を毎回再検証する。readは破損を返さず、inspectは実測checksumを返して既存readinessにmissing/corruptを分類させる。
- createWritableの一時書き込みを利用する。close開始前の中止・書き込み失敗ではabortし、元ファイルを維持する。新規キーに空ファイルが残る場合も、完全性はサイズ・checksumで判定する。close開始をcommit境界とし、それ以後の中止はロールバックを保証しない。保存後検証を終えて成功を返す。
- 同一originのadapter操作をWeb Locksで直列化し、別タブからの書き込みとinventory観測の競合を防ぐ。ロック待ちも中止可能。adapter外のストレージ消去を防ぐ機構ではない。
- 容量見積りは予約ではない。追加1音源分を保守的に見積もり、実書き込みのQuotaExceededErrorも扱う。容量が不明な場合は未評価とし、十分・不足を捏造しない。
- packageCompleteは音源の準備状態。Service Workerによるshell保存を扱わないため、このadapterのassessはappShellAvailableをfalseとする。ブラウザデータ消去・自動退避に対する保証や、navigator.storage.persist()の許可取得も含まない。
- 全packageの原子的世代切替・不要世代の削除は扱わない。Manifestで指定した世代を参照し、旧世代を保持することで先行検証を可能にする。

## 検証の入口

`npm ci`後、`npm exec -w abservice-offline-storage -- playwright install --with-deps --only-shell chromium`、`npm run test:offline-storage:browser`。

index.html/e2eはブラウザ試験専用のharnessで、公開画面への追加ではない。Viteでbundleした実装を実ChromiumのOPFSで動かす。短い自作440Hz合成音のFLACを用い、デコード検証はプレイヤー側へ委ねる。別世代用のバイト列は同FLACに1バイト付加した保存検証用fixture。

正常保存・再読み込み・通信遮断後の取得・実ファイル破損・別タブのロックは実APIで検証する。容量不足と中断のタイミングはnative write/closeやestimateへ故障注入し、復元する。実ディスクを満杯にした実測や、オフライン状態からのアプリ起動試験とは区別する。

## 参考

- [File System Standard](https://fs.spec.whatwg.org/) — 一時書き込みとclose/abortの意味論。
- [The origin private file system](https://web.dev/articles/origin-private-file-system) — origin単位の保存・容量制約・サイトデータ消去との関係。
