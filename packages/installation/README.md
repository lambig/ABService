# Installation contract PoC (#292)

`parseManifest(unknown)` と `assessReadiness(manifest, environment)` は I/O に依存しない。
Zodは外部入力の深い検証とreadonly snapshotの生成を担う。既存lock内の4.4.3を直接依存として宣言した。

## 契約 v1

- schemaVersion（構造の版）、packageVersion（配布物の識別）、compatibleAppVersion（実行アプリの範囲）は別物。
- アプリの安定版を `[major, minor, patch]` で表し、互換範囲は `[minInclusive, maxExclusive)`。
  prereleaseやSemVer範囲文字列はこのPoCでは扱わない。
- Album/TrackのID・タイトルをprojectionへ写し、音源やartworkを論理assetIdで参照する。
  Tune DBの項目は不要。projection生成APIは後続。
- trackの音源は必ずrequiredかつaudio media type。artwork/presentationはassetのrequired設定に従う。
- checksumはalgorithmとvalueの対。ハッシュ方式は未決のためopaqueな契約とし、計算はadapterの責務。
  inventoryにはローカルの実バイトから計算した観測値を渡す。manifestの期待値を転記してはいけない。
- asset ID重複・track ID重複・参照切れ・不正数値・空の互換範囲を拒否する。

## Readiness

`unsupported-schema` / `invalid-manifest` / `invalid-environment` は未評価として別の枝にする。
`assessed` は互換性・app shell・容量・required実体の有無・チェックサム一致を別項目で返す。
`checksumsValid` は存在するrequired実体のサイズ・checksum一致であり、欠落は `requiredAssetsPresent` が表す。
`packageComplete` は両方が真の場合のみ。optionalの欠落・破損はreadyを妨げない。

容量は不足・破損required assetを**追加でダウンロードする容量**。全requiredが検証済みなら空き0でもよい。
破損ファイルは削除済みと仮定せず、その置換分の追加容量を要求する。
app shellがローカルに存在し、アプリ互換性・package complete・容量がそろったときだけofflineReadyになる。
観測値はその時点のsnapshotであり、以後のevictionや書き換えを保証しない。

## 検証と後続

`npm run test:installation` / `npm run typecheck:installation` / `npm run lint:installation`。
ルートunit/lintとGitHub Actionsの型検査に組み込んでいる。

OPFS、Service Worker、FLAC upload、checksum計算、backend projection APIは後続。
このPoCは保存方式・配信経路を確定しない。ユーザーの「順に実行してください」に基づく独立実装。
