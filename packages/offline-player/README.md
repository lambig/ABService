# Offline player integration PoC

保存方式をプレイヤーへ漏らさずに、保存済みManifest・shell・音源を一つの試聴操作へ接続する。Manifestはshellのビルド世代に含めるため、起動時のオンライン取得を前提にしない。任意の配布package選択・動的Manifest更新は別の判断とする。

再生resolverはOPFSの実体検証済みBlobだけを返す。通信が戻っていても再生操作から自動ダウンロードしない。保存操作で欠落・破損した音源だけを取得し、途中で失敗しても準備完了としない。保存済みの正常音源は利用できる。全packageの原子的更新は主張しない。

readinessは実OPFS inventoryと、同じshell世代の全ファイル検証結果から判定する。判定はその時点の観測であり、以後の消去や破損を保証しない。選曲時にも音源を再検証し、失敗時は準備完了表示を取り下げる。shellの世代更新は全タブを閉じて切り替える先行PoCの方針に従う。

`/offline-player/` 専用scopeで先行shellのworkerを再利用する。音源はshellのallowlistに含めない。既存公開・管理画面への導入ではない。プレイヤーのUIは統合操作を試すデモとして独立させ、合成音fixture・基本スタイルは先行playerを参照する。本番の共通UI設計はこの段階では確定しない。

## 実行

`npm ci` → `npm run build:offline-player` → `npm run preview -w abservice-offline-player`。
http://127.0.0.1:4179/offline-player/ を開いて保存する。準備完了後にタブを閉じ、同じURLを通信なしで開き直す。

`npm exec -w abservice-offline-player -- playwright install --with-deps --only-shell chromium` の後、`npm run test:offline-player:browser`。
ブラウザ試験は同じbrowser contextでの全ページ終了と新ページ起動を扱う。ブラウザプロセス/OS再起動、Android実機の可聴出力・音切れ・長時間メモリ・消去耐性の評価とは区別する。

Vite previewと同梱合成音は検証専用。本番配布時の旧shell世代保持、永続化許可、容量管理、失われたshellの修復、音響解析/Visualizerの接続は本PoCの範囲に含めない。
