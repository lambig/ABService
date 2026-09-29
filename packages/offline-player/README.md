# Offline player integration PoC

配布元から試聴packageを準備し、保存済みのManifest・shell・音源を一つの試聴操作へ接続する。保存方式をプレイヤーへ漏らさない。準備は `packages/listening-preparation` の `prepare`、起動時の昇格・不要世代の削除は同じく `startup` を使い、取得は `packages/distribution-client` を通す。

## 準備と起動

- 起動時に `startup` で、このページが再生に使うactive packageを決める。他のタブが開いていなければ、準備済みのpendingを検証して昇格し、どこからも参照されない世代を削除する。曲目・選択中の表示・作品情報は、active packageから組んだ表示データ（`packages/listening-presentation`）だけから出す。Manifestを読むのはプレイヤーと保存領域だけで、画面はManifestへ戻らない。
- 準備の画面（token入力と保存の操作）は、準備済みでないときと、「配布物を準備する」で明示的に開いたときだけ表示する。通常の試聴では出さない。再認証を求められたときは準備の画面に留まり、準備済みのpackageでの試聴は続けられる。
- 選んだ作品の作品名・名義・artwork・説明を、`packages/listening-presentation` の表示データから表示する。artworkは検証済みの実体を描き、説明の画像とリンクは取りに行かない。欠けた項目の区画は出さない。見た目は仮で、画面全体の構成は #479 が受け持つ。
- 「アプリと音源を保存」は、入力欄の端末tokenで配布元からpackageを取得し、不足・破損した実体だけを取り直して、pendingとして準備する。新しい版はすべてのタブを閉じて開き直した後に切り替わる。activeと同じ版なら切り替えずにその場で修復する。
- tokenは入力欄と準備の呼び出しの間だけ持ち、保存もログ出力もしない。読み込み直すと入力し直しになる。
- 再生resolverはOPFSの実体検証済みBlobだけを返す。通信が戻っていても再生操作から自動ダウンロードしない。途中で失敗しても準備完了としない。
- readinessは実OPFS inventoryと、同じshell世代の全ファイル検証結果から判定する。判定はその時点の観測であり、以後の消去や破損を保証しない。選曲時にも音源を再検証し、失敗時は準備完了表示を取り下げる。
- 失敗の表示と `console.warn` の診断には、段階・assetId・mediaType・分類だけを出し、tokenと署名URLは出さない。

`/offline-player/` 専用scopeで先行shellのworkerを再利用する。音源はshellのallowlistに含めない。既存公開・管理画面への導入ではない。プレイヤーのUIは統合操作を試すデモとして独立させ、合成音fixture・基本スタイルは先行playerを参照する。本番の共通UI設計はこの段階では確定しない。

## 検証用の配布元

`distribution.ts` は、`vite preview` にだけ足す検証用の配布元。backendの試聴配布APIと同じ経路（package、音源の取得URLの解決、`/assets/{assetId}` の公開画像）と応答の形を、同梱の合成FLACと1画素のPNGで返す。本番の配布元ではなく、tokenと署名URLは形だけを真似る。

- token は `distribution.ts` の `fixtureToken`。
- 配布する版は2つ。v1は作品クロスフェードと曲音源の併用（音源2つとartworkを必須）。v2は作品クロスフェードのみで、v1と音源1つ・artworkを共有する。`POST /__distribution?version=v2` で切り替える。
- 通信の失敗・期限切れ・中断は、試験側がPlaywrightのrouteで差し込む。

## 実行

`npm ci` → `npm run build:offline-player` → `npm run preview -w abservice-offline-player`。
http://127.0.0.1:4179/offline-player/ を開き、`fixtureToken` を入力して保存する。準備後にすべてのタブを閉じ、同じURLを通信なしで開き直す。

実機での調整と観測は、URL の hash で行う（例: `/offline-player/#probe&scale=0.5&fps=30`）。`probe` で CPU（1 回の描画までの main thread 全体と、そのうちの renderer）と GPU を分けた観測、起動時の計測点を画面に重ね、`dpr`・`scale`・`effects`・`fps` で描画の予算を変える（範囲は `packages/visualizer` の README）。hash だけを変えたときは読み込み直す。

`probe` では音声側も重ねる（#290 の計測契約）。

- sampleRate と render quantum の長さ、特徴量の通知の件数と間隔（AudioContext の時刻）
- Worklet の平均負荷率（通知の区間ごとの値の分布）
- `playbackStats` の音切れと遅延。停止・選び直しで接続を捨てる直前の値を残す
- 「DSP ベンチマーク」ボタン: 選んでいる音源（未選択なら最初の音源）を main thread で decode し、チャンネルの配列をコピーせずに worker へ移して、先頭 30 秒について同じ DSP の quantum ごとの処理時間を測る。decode の間は main thread が塞がるため、押したときだけ走る。再生前は sampleRate を 48000 と仮定する

`npm exec -w abservice-offline-player -- playwright install --with-deps --only-shell chromium` の後、`npm run test:offline-player:browser`。

ブラウザ試験は同じbrowser contextでの全ページ終了と新ページ起動を扱う。

- 準備から通信なしの起動・選曲・再生までと、再生時に通信しないこと
- 別タブが旧版を使っている間は昇格せず、全タブを閉じた後に昇格し、旧版だけが使っていた実体を削除すること（実Web Locks）
- 未完了の新版へ切り替えず旧版で起動し、再準備で続きから取り直すこと
- 起動時の実体の欠落・破損の検出と、欠けた実体だけの取り直し
- 取得中のtoken期限切れ・署名URLの期限切れ・取得途中の中断の後、検証済みの実体を取り直さないこと。診断にtokenと署名URLを出さないこと
- 容量不足・app非互換・形の正しくないtokenでは取得を始めないこと
- tokenをlocalStorage・sessionStorage・URLに残さないこと。準備済みの端末では準備の画面を出さず、再認証を求められたら準備の画面に留まること
- 通信なしで作品情報とartworkを表示し、説明の画像とリンクを取りに行かないこと
- shell・書体の欠落を準備完了としないこと

ブラウザプロセス/OS再起動、Android実機の可聴出力・音切れ・長時間メモリ・消去耐性、実backendと実S3からの取得（本番CORSを含む）の評価とは区別する。

Vite previewと同梱合成音は検証専用。永続化許可、失われたshellの修復、本番UIの確定は本PoCの範囲に含めない。

## shellのビルド

Viteが `dist/assets` に出したファイルは、入れ子のディレクトリも含めてshellへ収録する。HTML・CSS・JavaScriptだけを世代付き参照へ書き換え、フォントや画像は元のバイト列を保持する。文字列へ変換してからハッシュを取ると、異なるバイナリが同じ文字列へ崩れて同じ世代になるため、世代は元バイト列のハッシュから決め、shellの検査値は実際の配布バイト列から作る。

ビルド検査は一時ディレクトリで実際の `build.ts` を実行する。バイナリ保持・入れ子のCSS参照・配布ハッシュ・作成順によらない世代・バイナリ変更による世代更新を確認する。検査用のWOFF2名のバイト列は破損を検出するための合成データで、書体そのものではない。

## 書体

日本語の書体は公開サイトと同じ Klee One を同梱する（宣言と選定の理由は `packages/player/src/style.css` と player の README）。実体は `dist/assets` に出るため shell の収録対象になり、欠落・破損は shell 未完了として準備完了を取り下げる。Manifest の表示素材（presentation asset）ではない——書体はアプリの一部で、配布 package ごとに変わらない。

ブラウザ検査は、保存後の通信なし起動で面が届き、日本語とラテンの見本が同梱書体の字形で描かれることと、書体だけを欠いた shell を準備完了とせず、通信なしの起動では OS の書体へ落ちることを確認する。
