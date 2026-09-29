# Player PoC

既存Manifestを消費する独立した再生画面。既存の公開・管理画面への組み込みは行わない。
`npm ci` → `npm run dev:player` で起動する。
`npm exec -w abservice-player -- playwright install --with-deps --only-shell chromium` と
`npm run test:player:browser` でproduction bundleのブラウザ検証を実行する。

## 境界の理由

`PlayerSnapshot` は phase で形が決まる。シーク中は `phase: "seeking"` になり、その状態だけがシーク後の戻り先 `resumeTo`（再生中からのシークは `playing`、それ以外は `paused`）を持つ。media の `seeked` で戻り先へ移る。シーク中に再生すると戻り先が `playing` に変わり、一時停止するとシークを抜けて `paused` になる。表示と操作の可否には、シーク中を戻り先として扱う `settledPhase` を使う。

再生・シーク・FLACデコードはブラウザのmedia実装へ委ねる。PCM全体を独自デコードしたり、
AudioWorkletやWASMを再生操作の前提にしたりしない。音響解析・描画は別の接続対象として扱う。

論理asset IDと音源Blobの解決を分離することで、保存方式を選曲・再生操作へ漏らさない。
デモのresolverは同梱fixtureをHTTP取得してBlobへ変換する検証用adapterであり、
OPFSや永続オフライン保存の実装ではない。Manifestのchecksumはfixtureの識別用で、
このプレイヤーは整合性検査やoffline readinessを代行しない。

選曲は旧再生を終了し、読み込み後に明示的な再生操作を受け付ける。
ブラウザの再生許可に依存するため、playの完了前に成功表示をしない。
停止は選択曲を保持して資源を解放し、読み直しは音源の再取得として扱う。
中止要求だけではresolverの遅延完了を防げないため、現在のsessionとの一致も確認する。

## Fixture

音源は自作の短い合成正弦波。既存の楽曲・録音を含まない。
`first.flac`は440Hz・8秒、`second.flac`は660Hz・5秒、16kHz mono。
ffmpegの`sine` source（振幅既定値）→`volume=0.15`→FLAC compression level 12で生成。
デモの曲名は検証用であり、実在の収録曲を表すものではない。

## 書体

日本語の字形を持つ Klee One（SIL OFL 1.1）を同梱し、公開サイトと同じ字面にする（DECISIONS 25）。
実体は `@fontsource/klee-one` の subset 別ファイル（japanese / latin / latin-ext の 400 と 600）を使い、
ビルド時に hash 付きの asset として出る。実行時に外部の書体配信へ取りに行かない。

`@font-face` の宣言は `src/style.css` が持ち、fontsource の subset 別 CSS は使わない。その CSS は
unicode-range を持たず、同じ family と weight の宣言が並ぶと最後の 1 つしか効かないため。日本語の面は
範囲を限定せず、ラテンの面を後に置いて範囲を限定する。どの面にも無い字は、次の family（OS の標準）へ
字ごとに落ちる。

ブラウザ検査（`e2e/typeface.ts`）は、面が読み込まれて日本語とラテンの見本が同梱書体の字形で描かれることを
幅の比較で見る。`document.fonts.check` は面が無いときも真を返すため、それでは字形の有無を判定できない。
書体の実体を同一オリジン以外から取りに行かないことも見る。

## 検証の範囲

実ChromiumでFLACデコードと再生時刻の進行を検査する。停止・選曲・破棄での解放と、
中止を無視した旧取得の遅延完了・破損音源・取得失敗・play拒否も検証する。
headlessの時刻進行はスピーカーからの可聴出力や対象Androidでの音切れの証明ではない。
実機評価と後続の接続作業は#110を正とする。#313はこの最小プレイヤーの操作・resolver境界の受け入れを扱う。

再生許可の参照: [HTMLMediaElement.play](https://developer.mozilla.org/en-US/docs/Web/API/HTMLMediaElement/play)。
