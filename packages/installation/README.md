# Installation contracts (#292)

## 新規配布: schema v2

`projectManifestV2(unknown)` で作品情報と再生項目を分離する。
`albums[].tracks` はcanonicalな `trackId / trackNo / title` の説明情報で、音源を要求しない。
`playbackItems` は配布対象として明示的に選んだ再生項目のリスト。配列順を表示順とする。

- 共通項目は安定した `playbackItemId`、`kind`、`albumId`、表示用 `title`、`audioAssetId`、任意の `durationSeconds`。
- `kind: "album-crossfade"` は作品を参照し、trackIdを持たない。
- `kind: "track"` は同じ作品内のcanonicalなtrackIdを必須にする。
- クロスフェードのみ・曲音源のみ・併用を扱い、曲音源追加時も既存クロスフェードの項目IDを変えない。
- 画像や曲順・タイトル・外部URLからデモの区間や現在曲を推測しない。架空のTrackを追加しない。
- 重複ID、未知の作品・曲、別作品の曲、種別と参照の不整合、選択項目の音源対応漏れを拒否する。
- 音源はrequiredなaudio asset。v2のassetは正の安全な整数サイズと、sha256・小文字64桁hexのchecksumを必須にする。形式・実サイズ・digestの実測は登録・保存adapterの責務。
- artwork/presentationの参照先も検証する。必須表示素材はrequiredをtrueにして完全性判定へ含める。

projection入力は認可済み・ID付きAlbum/Track snapshot、playbackItems、albumArtworkBindingsとpackage共通情報。
Album選択順と再生項目順を保ち、収録曲はtrackNoで並べる。
canonicalデータの余分な項目やassetの取得URLはsnapshotに写さない。再生項目の形は種別ごとに厳密に検証する。
入力形式の不備はinvalid-projection、生成したManifestの整合性違反はinvalid-manifestとして返す。
認証・認可・DB取得・実音源の対応付けは #474 / #475 の責務。

## 旧schema・保存物との互換性

`parseManifest` はv1/v2を各schemaで検証し、版を変えず独立したreadonly snapshotを返す。
未知のschemaはunsupported-schema。旧アプリのv1 parserはv2を未対応として拒否するため、v2をv1と偽って配布しない。
v2のcompatibleAppVersion.minInclusiveは [1, 10, 0] 以降を必須にする。

schemaVersion・packageVersion・app互換範囲は独立。v2配布時は新しいpackageVersionを割り当てる。
読み取り時に保存済みManifest・ID・packageVersionを自動変更しない。
OPFSの既存の実体識別（asset ID・サイズ・checksum）を維持し、schema更新だけを理由に音源を複製しない。
app/package切替は #477 で新版の準備・互換性・完全性を確認してから行い、失敗時は旧版を維持する。
この純粋契約は保存物の削除や世代切替を行わない。

旧 `projectManifest` はv1専用の互換APIとして残す。以下の「契約 v1」と旧projectionの全Track音源必須条件は、この互換APIだけの仕様。
v1のopaqueなchecksumの解釈も維持する。新規配布にはprojectManifestV2を用いる。

## 利用側の選択境界

`getPlaybackItems(manifest)` で両版を共通のPlaybackItemリストへ写す。
v2は独立した項目ID、v1のみ既存select呼び出しを保つため従来のtrackIdを項目IDとして用いる。保存Manifestの変換ではない。
プレイヤーの `select(playbackItemId)` と `snapshot().playbackItemId` を選択・再取得の正とする。
`snapshot().trackId` は曲音源のcanonical ID、クロスフェードではnull。再生位置・長さはmedia実体から得る。
#476のUIも収録曲の説明一覧と再生可能な項目を分けて表示する。

readinessは収録曲情報だけを理由に音源を要求しない。選択した音源とrequired表示素材を検証する。
複数項目が同じ実体を参照してもassetsは1件とし、必要容量は一度だけ計上する。
unit検査は三つの配布形態、参照違反、サイズ/checksum、共有実体、optional素材、旧版互換を含む。
playerの合成デモはv2を使い、`?schema=1` で旧fixtureの再生を検証できる。
実データ配布、作品情報／PRとインスタレーション表現の完成、実Android受け入れは後続issueで管理する。

`projectManifest(unknown)`、`parseManifest(unknown)`、`assessReadiness(manifest, environment)` は I/O に依存しない。
Zodは外部入力の深い検証とreadonly snapshotの生成を担う。既存lock内の4.4.3を直接依存として宣言した。

## 契約 v1

- schemaVersion（構造の版）、packageVersion（配布物の識別）、compatibleAppVersion（実行アプリの範囲）は別物。
- アプリの安定版を `[major, minor, patch]` で表し、互換範囲は `[minInclusive, maxExclusive)`。
  prereleaseやSemVer範囲文字列はこのPoCでは扱わない。
- Album/TrackのID・タイトルをprojectionへ写し、音源やartworkを論理assetIdで参照する。
  Tune DBの項目は不要。
- trackの音源は必ずrequiredかつaudio media type。artwork/presentationはassetのrequired設定に従う。
- checksumはalgorithmとvalueの対。ハッシュ方式は未決のためopaqueな契約とし、計算はadapterの責務。
  inventoryにはローカルの実バイトから計算した観測値を渡す。manifestの期待値を転記してはいけない。
- asset ID重複・track ID重複・参照切れ・不正数値・空の互換範囲を拒否する。

## Projectionの入力境界

v1.0のAlbum/Trackには会場用音源のasset IDがないため、canonicalなID・タイトル・曲順と、配布用assetとの対応表を分ける。
公開APIはtrack IDを返さないため入力元にはせず、IDを持つ読み取りデータのsnapshotを使う。
URL・storage key・SoundCloudの埋め込み先から論理asset IDを推測しない。
対応表とassetのchecksumは呼び出し元が用意し、曲の対応漏れを成功扱いで除外しない。

アルバムの選択順は呼び出し元の意図として保ち、曲順はcanonicalなtrackNoに従う。
公開可否・配布権限の判定はこの純粋変換では行わず、入力を用意する境界で担う。

FLACは試聴端末向けの限定配布を前提とする。準備時の認証・配布対象の認可・取得URLの発行は
配布APIの責務であり、このManifestはアクセス許可を与えない。公開画像の配信経路に音源を載せない。
端末は準備済みの音源をオフラインで再生するため、期限付き取得URLや認証情報はManifestへ含めない。

複数のTrackに同じ音源実体を使う場合は、対応表から同じassetIdを参照する。
assetsにはその実体を1件だけ載せるため、readinessの必要容量も1回分となる。
曲名の一致だけでは再録・別録音・別マスターを同一視せず、音源の対応付けは呼び出し元が決定する。

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

このパッケージは論理契約に閉じ、OPFS・Service Worker・checksum計算を実装しない。保存とshellのadapterは `packages/offline-storage` / `packages/offline-shell`、接続した試聴操作は `packages/offline-player` を参照する。FLAC uploadとbackend projection APIは持たない。残作業と採用判断は #292 / #110 が管理する。
