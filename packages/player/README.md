# Player PoC

既存Manifestを消費する独立した再生画面。既存の公開・管理画面への組み込みは行わない。
`npm ci` → `npm run dev:player` で起動する。
`npm exec -w abservice-player -- playwright install --with-deps --only-shell chromium` と
`npm run test:player:browser` でproduction bundleのブラウザ検証を実行する。

## 境界の理由

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

## 検証の範囲

実ChromiumでFLACデコードと再生時刻の進行を検査する。停止・選曲・破棄での解放と、
中止を無視した旧取得の遅延完了・破損音源・取得失敗・play拒否も検証する。
headlessの時刻進行はスピーカーからの可聴出力や対象Androidでの音切れの証明ではない。
実機評価と後続の接続作業は#313 / #110を正とする。

再生許可の参照: [HTMLMediaElement.play](https://developer.mozilla.org/en-US/docs/Web/API/HTMLMediaElement/play)。
