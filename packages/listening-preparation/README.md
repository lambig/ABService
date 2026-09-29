# Listening package preparation

試聴端末の準備トランザクション。配布中のpackageを取得し、不足・破損したrequired assetだけを取り直して、再起動待ちのpendingにする。取得は `packages/distribution-client`、Manifestの世代とasset実体の保存は `packages/offline-storage` に任せ、ここは両者をつなぐ順序と止め方だけを持つ。画面・Service Worker・起動時の昇格は持たない。

## 手順

1. 配布client で package を取得する（strictな検証は client が行う）。
2. そのManifestでasset storeを開き、保存済み実体の観測から readiness を判定する。
3. appが互換範囲外なら `incompatible`、不足・破損したrequired assetを取り直す空きが無ければ `capacity` で止める。どちらもpendingへ記録しない。
4. active と同じ版なら、切り替えは不要（`current`）。残っている別の版のpendingは外す。そのままにすると、次回起動で配布中の版より古いpendingへ切り替わる。違う版ならpendingとして保存する（`on-restart`）。activeが読めない場合も後者とし、それを修復とする。同じ版で内容が違う場合は、packageVersionが内容のdigestであることに反するため、package storeの `stage` と同じく `conflict`（stage `storage`）で止め、pendingも変えない。
5. 不足・破損したrequired assetを、Manifestの順に1つずつ取得して保存する。checksumの照合は保存する側が保存の前後に行う。
6. 取得を終えたら観測し直し、required assetが揃い、同じ世代のshellが完全なときだけ `prepared` を返す。

activeはここでは置き換えない。昇格・旧版の維持・不要世代の削除は、全タブ終了後の起動処理が受け持つ。

## 再試行と中断

- 準備は呼び出し側が実行し直すことで再試行する。このトランザクション自身は再試行しない。
- 保存済みの実体は内容（assetId・サイズ・checksum）で識別するため、通信断・中断・tokenや署名URLの期限切れで止まった後も、次の実行では取り直さない。止まったassetは先頭から取り直す。
- pendingは取得の前に保存する。途中で止まっても、それまでに保存した実体はpendingの世代から参照される。
- 取得は直列で、取得した実体は保存が済むまでしか持たない。並列に取ると、1つ分を超えるメモリが同時に要る。

## 容量

- 必要容量は、Manifestの一意なrequired assetのうち、不足・破損しているものの期待byte数の和（`assessReadiness` の `requiredDownloadBytes`）。検証済みの実体は0 byteとし、すべて揃っていれば空きが0でも準備できる。
- 空きは `navigator.storage.estimate()` の quota − usage。旧版・shell cacheはusageに含まれるため、候補の分を2倍にしない。
- 事前の判定は予約ではない。保存時の `QuotaExceededError` は、そのassetの `quota-exceeded` として返す。

## 失敗の分類

| stage | 起きる場面 | 呼び出し側の次の操作 |
|---|---|---|
| `package` | packageを取得できない（分類は配布clientのもの） | 分類に従う（`unauthorized` / `forbidden` ならtokenを入力し直す） |
| `incompatible` | 配布中のpackageがこのappの版に対応しない | appを更新する |
| `capacity` | 必要容量が空きを超える | 空きを確保する |
| `asset` | required assetの取得か保存に失敗した | 分類に従う。再試行では保存済みの実体を取り直さない |
| `storage` | 保存領域の観測・世代の保存に失敗した。activeと同じ版で内容が違う場合は `conflict` | 保存領域を確認する。`conflict` は配布側か保存物の異常として調べる |
| `shell` | assetは揃ったが、同じ世代のshellが完全でない | shellを保存し直す |
| `incomplete` | 取得後の観測で、まだ不足・破損がある | 再試行する |

診断に載るのは段階・assetId・mediaType・分類・HTTPステータス・byte数だけで、tokenと署名URLは型の上で載らない。

optional assetは取得しない。readinessはoptionalの欠落を準備完了の妨げとしない。

## 検証の入口

`npm run test:listening-preparation`。配布client・package store・asset storeを差し替えた単体試験で、配布応答と同じ見本（`packages/installation/fixtures/manifest-v3.example.json`、クロスフェードのみ）を使う。実OPFS・実Chromiumでの準備から通信なしの起動までは、試聴端末の受け入れ試験で扱う。
