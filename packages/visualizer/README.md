# Visualizer PoC (#291)

`AudioFeatures → mapFeatures → PresentationFrame → WebGPU Renderer` の独立デモ。
音声の再生・取得・解析は実装せず、`abservice-audio-dsp` の `AudioFeatures` を入力として受け取る。
既存の公開／管理画面へは組み込まない。

## 実行

```sh
npm ci
npm run dev -w abservice-visualizer
```

表示されたlocalhost URLを開き「先頭から開始」。HTTPSまたはlocalhostのWebGPU対応環境が必要。
停止で描画予約・GPU資源を解放し、開始で時刻0から再初期化する。
WebGPUが利用できない場合・初期化失敗・device lostは画面に理由を表示する。代替rendererの自動選択は行わない。
リサイズ・画面回転・fullscreenではcanvas寸法を毎フレーム照合する。DPR上限は2、寸法はdeviceの上限以下。

## 境界と値域

- `src/index.ts`: DOM非依存の純粋mapper、immutableな描画値、時刻で再現可能なfake source。
- `src/renderer.ts`: 描画値のみを受け取り、uniform buffer・一枚のatlas・fullscreen triangleで描画する。
- `src/scene.wgsl`: 背景1層、抽象artwork1枚、文字1層、軽量な点状エフェクト。
- `src/demo.ts`: 開始／停止、device lost後の再試行、直近120フレームの観測。

| 特徴量             | 描画値                             | 範囲                |
| ------------------ | ---------------------------------- | ------------------- |
| rms                | scene scale / background intensity | 1–1.035 / 0.15–0.55 |
| lowEnergy          | artwork scale / displacement       | 1–1.08 / 0–0.025    |
| midEnergy          | typography displacement / opacity  | 0–0.025 / 0.65–1    |
| highEnergy         | effect intensity                   | 0–0.35              |
| onset              | impulse（artworkの短い拡大）       | 0–1                 |
| spectralCentroidHz | background texture scale           | 1–2                 |

特徴量はclampし、非有限値は中立値へ変換する。時定数0.12秒の指数平滑化を使う。
onsetは強度のピークを取り込み、時定数0.16秒で減衰する。設定は`MappingOptions`で変更可能。
`previous`には`restingFrame`またはmapperが返したframeを渡す。時刻の巻き戻りでは平滑化をresetする。
AudioFeaturesの帯域・onset意味論は#290で検討中であり、このfake sourceはDSPの代替実装ではない。

## 検証と未完了条件

ローカルで単体13テスト・型検査・lint・Vite buildを実行。
CIにも型検査・lint・単体・buildを追加した。
この作業環境にはブラウザがなく、Playwright Chromiumの取得がtimeoutしたため、
**WGSLの実GPUコンパイル・デモ描画・スクリーンショットは未確認**。Draft PRとして扱う。

対象端末の実測は未実施。WebGPUを第一候補として維持できるとの判断はまだ行わない。
以下をレビュー時の実機検証で記録する。

| 検証                             | 記録する内容                                                                          |
| -------------------------------- | ------------------------------------------------------------------------------------- |
| 開始・停止・再開始               | デモ表示、停止中の負荷、時刻reset、連打で重複loopが残らないこと                       |
| 通常・全画面・縦横回転           | artworkと文字の視認性、canvas寸法、復帰後の描画                                       |
| 30分連続                         | 端末／OS／ブラウザ／GPU、解像度、fps、frame interval、CPU/GPU負荷、メモリ開始／終了値 |
| WebGPU unavailable / adapter拒否 | エラー表示、開始で再試行できること                                                    |
| device lost                      | 描画停止、資源解放、開始で再初期化できること                                          |

画面上のfpsはrAF間隔由来、CPU submitはrender呼び出しの経過時間でありGPU実行時間ではない。
GPU負荷・メモリは対象端末のプロファイラで別途観測する。

WebGPU APIの参照: [W3C WebGPU](https://www.w3.org/TR/webgpu/)。
