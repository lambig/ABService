# Offline asset storage PoC

会場準備では、内容で識別した保存先の存在とサイズから音源を再利用する。画像だけの追加・更新や起動のたびに、変更のない音源を全量読み直さない。

## 判断の理由と境界

解析結果の新規保存は音源SHA-256をキーにし、画像・作品情報だけの版更新でも同じ解析を利用する。取り込み時は対応する音源ID・長さ・内容識別と解析形式・時間長を照合し、保存済み音源の本体は読み直さない。音源の同サイズ破損は再生時のreadで検出する。従来のpackageVersion付き保存物は同じ版から読み続けられる。旧形式で保存した解析を別の版でも使う場合は、一度既存の解析ファイルを取り込み直すと内容キーへ移行し、以後の画像更新では再取り込みを要しない。

- OPFSは候補検証。ファイル操作をasset IDの境界へ閉じ込め、呼び出し側へ保存パスを渡さない。同一asset IDでもサイズ・checksumが異なる内容を別のキーへ保存し、失敗した更新で旧世代を壊さない。パスは識別情報のSHA-256で作り、asset ID内のスラッシュや日本語をパスとして解釈しない。
- checksumはPoCで`sha256`・小文字hexに限定する。未対応方式を黙って信頼しない。これは配布元の真正性・音声形式・デコード可否の検査ではない。信頼するManifestとのバイト一致を検査する。
- 書き込み前のBlobと、close後の保存実体を検証する。readで破損を検出した実体は削除し、次の通常準備でその実体だけを再取得する。中止や保存領域の障害では削除しない。
- inspectは存在・サイズと `contentIdentity` を返し、実体checksumを再計算しない。assessの `checksumsValid: null` は未検証を表す。同じサイズの保存後破損は準備時の検出対象にせず、readで検出する。
- createWritableの一時書き込みを利用する。close開始前の中止・書き込み失敗ではabortし、元ファイルを維持する。新規キーに空ファイルが残る場合も、完全性はサイズ・checksumで判定する。close開始をcommit境界とし、それ以後の中止はロールバックを保証しない。保存後検証を終えて成功を返す。
- 同一originのadapter操作をWeb Locksで直列化し、別タブからの書き込みとinventory観測の競合を防ぐ。ロック待ちも中止可能。adapter外のストレージ消去を防ぐ機構ではない。
- 容量見積りは予約ではない。追加1音源分を保守的に見積もり、実書き込みのQuotaExceededErrorも扱う。容量が不明な場合は未評価とし、十分・不足を捏造しない。
- packageCompleteは音源の準備状態。Service Workerによるshell保存を扱わないため、このadapterのassessはappShellAvailableをfalseとする。ブラウザデータ消去・自動退避に対する保証や、navigator.storage.persist()の許可取得も含まない。
- AssetStoreは渡されたManifestの世代だけを参照する。旧世代の実体を保持するため、新旧のManifestが同じ実体を共有できる。どの世代を使うかはpackage storeが決める。
- `collectAssets` は渡したManifestのどれからも参照されない実体を削除する。実体は内容（assetId・サイズ・checksum）で識別するため、複数の世代が共有する実体は1つとして残る。Manifestが1つでも不正なら何も削除しない。別タブで使用中の世代や保存中の実体を知らないため、呼び出し側が他のタブの利用を排他してから呼ぶ。

## 準備済みpackageの世代

`createPackageStore` は、配布されたManifestをpackageVersionごとに保存し、どの世代を使うかをactive/pendingの2つのslotで表す。activeはオフライン再生が読む世代、pendingは昇格を待つ候補。

- 保存先はasset実体と別のディレクトリ `abservice-packages-v1`。Manifestは `manifests/` の下に、packageVersionのSHA-256をファイル名にして置く。packageVersionにスラッシュ・ドット・日本語が含まれても、パスとしては解釈しない。
- 2つのslotは1つのpointerファイルにまとめる。createWritableのcloseがcommit境界なので、close前に失敗すれば両slotとも旧値のまま残る。
- `stage` は配布応答と同じstrict parseに通したManifestを書き、そのあとでpendingを付け替える。activeは変えない。Manifestを書いた直後に中断された場合に残るのは、どのslotからも参照されないファイルだけ。
- `read` は読むたびにstrict parseを通し、ファイル中のpackageVersionがslotの値と一致することを確かめる。JSONが壊れている・未知の項目がある・別の版が入っている場合はcorrupt、ファイルが無ければmissing。中身のあるpointerが不正な場合も、推測で直さずにcorruptを返す。0バイトのpointerだけは、初回作成がcommitされずに終わったもの（空のままのファイルが残る）として、両slotとも空のpointerと扱う。commitされるpointerは空の状態でも`{}`なので、0バイトになることはない。
- activeかpendingが参照している世代を、同じpackageVersionで別の内容に置き換えることはしない（conflict）。packageVersionは内容のdigestなので、同じ版で内容が違うのは配布側の異常として扱う。ただし保存済みのファイルが読めない場合は、同じ版の正しい内容で上書きして修復する。
- `promote` はpendingを読める状態か確かめてからactiveへ移す。昇格の時機（全タブ終了後の起動時）、asset・app互換・shellの再検証、利用中の世代の保護、不要世代の削除は呼び出し側の準備・起動処理が受け持つ。`discardPending` は参照だけを外し、ファイルは残す。
- `list` は保存済みの世代と、それを参照しているslotを返す。読めないファイルは数だけを返し、保存パスは外に出さない。
- `collect` はactive・pendingのどちらからも参照されないManifestファイルを削除する。残すかどうかはslotの版から求めたファイル名で決めるため、参照中のファイルは読めなくても残り、同じ版の `stage` で修復できる。pointerが不正なら何も削除しない。
- 操作は同一originの全タブで、ロック `abservice-packages-v1` の排他で直列化する。asset実体のロックと両方を取る場合は、package → assetの順に取る。

## 検証の入口

`npm ci`後、`npm exec -w abservice-offline-storage -- playwright install --with-deps --only-shell chromium`、`npm run test:offline-storage:browser`。

index.html/e2eはブラウザ試験専用のharnessで、公開画面への追加ではない。Viteでbundleした実装を実ChromiumのOPFSで動かす。短い自作440Hz合成音のFLACを用い、デコード検証はプレイヤー側へ委ねる。別世代用のバイト列は同FLACに1バイト付加した保存検証用fixture。

package storeの試験は、`packages/installation/fixtures/manifest-v3.example.json`（backendの統合試験と同じ見本）を配布応答として使う。

正常保存・再読み込み・通信遮断後の取得・実ファイル破損・別タブのロックは実APIで検証する。容量不足と中断のタイミングはnative write/closeやestimateへ故障注入し、復元する。実ディスクを満杯にした実測や、オフライン状態からのアプリ起動試験とは区別する。

## 参考

- [File System Standard](https://fs.spec.whatwg.org/) — 一時書き込みとclose/abortの意味論。
- [The origin private file system](https://web.dev/articles/origin-private-file-system) — origin単位の保存・容量制約・サイトデータ消去との関係。
