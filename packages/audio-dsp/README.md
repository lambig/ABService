# Audio DSP PoC (#290)

P0（契約）と P1 の RMS 参照実装。Web Audio / AudioWorklet / WASM / DOM に依存しない。
本パッケージはブラウザで音を再生するプレイヤーではなく、その内部で利用する数値処理の境界である。

## 実装承認

Issue #290 の独立 PoC 着手承認と、2026-09-06 の「AbServiceのプレイヤーについて着手可能な部分を作業してください」に基づく。
既存の v1.0 アプリ・DB・アセット配信は本スライスの変更対象に含めない。

## 契約

- `AudioFeatures`: v0 の完全な特徴量。`audioFeatures()` が値域を検査してコピー・凍結する。
- `RmsFeatures`: 時刻と RMS のみ。周波数解析未実装の P1 ではこの型を返し、未計測値をゼロで埋めない。
- `DspPort<T>`: 同期 `analyze(PcmBlock)`。PCM は呼び出し中だけ借用し、保持・変更しない。
- `DspResult<T>`: `features` と `invalid-input` の判別 union。不正値を描画に渡さない。
- v0 の `rms`・帯域 energy・onset は [0, 1]、centroid は非負有限の Hz。
  帯域 energy / onset の正規化・窓・平滑化の意味論は P2 で決定する。

## RMS の意味論

| 項目       | 規則                                                                   |
| ---------- | ---------------------------------------------------------------------- |
| 入力       | 同じ正のフレーム数を持つ mono / stereo の planar Float32 PCM           |
| サンプル値 | NaN / Infinity はブロックを拒否。有限値はサンプル単位で [-1, 1] に制限 |
| RMS        | `sqrt(mean(channel mean(clipped sample²)))`                            |
| stereo     | 波形の加算 downmix はせず、各チャンネルのパワーを等重みで平均          |
| 左右逆相   | 同相と同じ RMS。位相相殺による無音化を防ぐ                             |
| 片 ch      | 同じ mono 信号に対し RMS は 1/sqrt(2)                                  |
| 窓・平滑化 | 入力ブロック全体の矩形窓、平滑化なし。128 frames 固定にしない          |
| 時刻       | 呼び出し元が与えるブロック開始の AudioContext 時刻（秒）を保持         |
| sampleRate | 正の有限値。RMS の計算には使用せず、P2 の周波数解析に備える            |
| 空入力     | `invalid-input`。無音はゼロで埋めた有効長の PCM として渡す             |

時刻の単調性はセッションを知る AudioWorklet adapter の責務とする。stateless な DSP は非負有限値のみ検証する。
過去の snapshot は後続ブロックや借用バッファの再利用で変わらない。

本実装は意味論の参照用で、入力検証と RMS 計算で PCM を2回読む。rendering thread の性能保証はまだない。
実測前に規約を緩和してループや可変バッファへ最適化しない。

## 検証

```sh
npm run test:audio-dsp
npm run typecheck:audio-dsp
npm run lint:audio-dsp
```

合成信号（無音・sine・impulse・左右逆相・片 ch）と不正値、凍結した snapshot、DSP 差し替えを検証する。
型検査は `lib: [ES2022]` で実施し、描画側の利用例にも Web Audio 型を必要としない。
ルートの unit/lint と frontend CI の型検査に組み込んでいる。

## 後続

1. **P1 残り**: AudioWorklet adapter と再生 graph、セッション時刻の単調性、通知周期の分離。
   未接続入力は通知を抑止するか有効な無音ブロックとして扱う。PCM の main thread 転送は行わない。
2. **P2**: FFT / window / hop / 帯域境界 / onset の意味論を合成 fixture とともに固定。
   完全な `AudioFeatures` を返す DSP を実装する。
3. **P3**: 同じ fixture で JS/WASM を比較し、許容誤差・初期化・処理時間・割り当てを計測。
4. **端末評価**: Android の長時間再生、通知頻度、deadline miss・音切れ。

FLAC 登録・OPFS・Service Worker・WebGPU・Album/Track API 結線は #290 の非目標のまま。
