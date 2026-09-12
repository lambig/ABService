# Offline shell PoC

試聴機の音源保存と独立して、通信なしの起動とshell更新の世代整合を検証する。既存公開/管理画面へService Workerを導入しないため、専用scopeと専用の検証画面を持つ。

## 判断の理由

- HTML/JS/CSSとworker・build処理から世代を作る。install時にファイルごとのSHA-256を確認し、全取得・保存が完了するまで候補workerを有効にしない。失敗した候補cacheだけを削除し、旧active workerを保つ。hashは配布元の真正性を保証するものではない。
- 世代を含むURLで各ファイルを取得する。HTMLだけ新版でJSは旧版、といった混在を避けるため、取得時のredirectや内容不一致を拒否する。配布側は旧版のファイルも保持する前提。
- skipWaitingとclients.claimを使わない。稼働中の試聴機のページを強制的に新版へ移さず、旧版の全タブを閉じた後に切り替える。候補がwaitingの間は旧版のoffline起動を維持する。
- fetchは専用scopeの入口と列挙したshellだけを扱う。API・音源・未知URL・query付きURLをshellへ置換せず、runtime cacheもしない。レスポンスは利用時にもhash確認し、欠落・破損時に別世代のネットワーク応答で穴埋めしない。
- 旧世代cacheの自動GCを行わない。新しい候補のinstallとcache削除を競合させず、更新失敗からの保護を先に検証する。長期運用の容量管理は別判断。
- 保存後の消去・破損は503で説明する。データ消去後の自動修復や同一世代の再installはこのPoCの対象外。ブラウザの保存領域やService Worker登録を利用者が変更しない通常の更新ライフサイクルを前提とする。

## 実行・検証の境界

`npm ci`、`npm run build:offline-shell`、`npm run preview -w abservice-offline-shell`。検証用サーバーの入口は http://127.0.0.1:4178/installation-poc/ 。HTTPS相当のsecure contextとしてlocalhostを用いる。

`npm exec -w abservice-offline-shell -- playwright install --with-deps --only-shell chromium`の後、`npm run test:offline-shell:browser`で実Service Worker/Cache Storageを検証する。

buildは同じ実装からv1/v2の配布fixtureを生成する。e2e/serverはlocalhost限定の試験サーバーで、配布切替・HTTP失敗・200の破損応答・redirectを注入するための制御口を持つ。サーバーとfixture切替口は本番配信用ではない。NodeでTypeScriptを実行できるため追加bundlerを持たず、ブラウザ向けコードは型検査とは別にtranspileする。

試験は同じbrowser contextでページをすべて閉じ、通信を遮断して新しいページを開く。Service Workerが返した応答と、保存したHTML/JS/CSSの動作を確認する。ブラウザプロセス/OS再起動、Android実機、サイトデータ消去・自動退避への耐性、PWAインストールや音源再生の試験ではない。

音源・OPFS・InstallationManifest/readinessの統合は含めない。画面の保存確認はshellについてのみで、会場投入可能やofflineReady全体の主張ではない。

## 参考

[Service Workers specification](https://www.w3.org/TR/service-workers/)のinstall/activateとworker更新のライフサイクルを利用する。
