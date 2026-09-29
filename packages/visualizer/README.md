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
AudioFeaturesの帯域・onset意味論は `packages/audio-dsp` の実装のJSDocと単体テストを正とする。このfake sourceは描画検証用であり、DSPの代替実装ではない。

## presentation state の契約（#479 A）

`src/state.ts` は試聴体験の状態の語彙と遷移だけを持つ純粋な契約。見せ方（layout・mapping・effect・動きの強さ・時間展開）は固定しない。

- 状態: `idle` / `selected` / `playing` / `paused` / `seeking` / `ended` / `error`。停止後・終了後の戻り先は `selected`。
- `transition(state, event)` が次の状態と、描画値を静止値へ戻すか（`resetFrame`）を返す。選び直し・seek・停止・失敗・clearでは戻し、旧い作品・旧い再生位置の平滑化とimpulseを残さない。一時停止では戻さず、直前の構図を保つ。
- その状態で起きえない出来事（例: `idle` での `play`、`seeking` 以外での `seeked`）では状態を変えない。順序の乱れた通知から状態を作らない。
- rendererへの入力は `PresentationInput`（状態・`PresentationFrame`・作品の表示内容）だけ。rendererはAudioFeatures・Manifest・playerを参照しない。表示内容は検証済みの表示データ（`packages/listening-presentation`）の作品 `ListeningAlbum` をそのまま渡し、どの事実をどう見せるかは renderer の側が決める。`listening-presentation` へは型だけで依存する。
- `PresentationInput` は状態ごとに形を分ける。`idle` は表示内容を持たず、`error` は任意、それ以外は必ず持つ。成り立たない組み合わせは型の段階で作れない。
- player の状態からこの出来事への写しは #476 C後半、rendererが表示内容を描くことは #479 B が受け持つ。

## 描画セッションの監督と縮退（#291 A）

`src/session.ts` の `superviseRenderer` は、1つの描画セッションのrendererの寿命を監督する。rendererを作る関数は呼び出し側が渡す（GPUなしで単体試験できる）。

- device lostでは今のrendererを破棄し、作り直しを**1回だけ**試す。描画中の例外もlostと同じに扱う。
- 作り直しが失敗するか、作り直したrendererもまたlostしたら `degraded` へ移り、無限に再試行しない。WebGPUが使えない・初期化に失敗した場合も `degraded`。
- `degraded` で止めるのは描画だけ。再生・作品情報・操作は呼び出し側で保つ。
- `dispose` の後は、遅れて完了したrendererも破棄し、lostも無視する。
- 回転・fullscreen復帰は、rendererが毎フレームcanvas寸法を照合して追う（作り直さない）。

`offline-player` は再生のたびにこの監督を作り、一時停止・停止・選曲・pagehideで破棄する。GPU資源を持つのは再生中だけ。

- 一時停止では、GPU資源を放す直前に今の構図を静止画へ写してcanvasの背景に敷き、直前の構図を保つ（#479 paused）。描画値と縮退の判定は一時停止をまたいで残す。
- 再開で新しい監督を作り、描き始めるか縮退した時点で静止画を外す。一度縮退した描画は、停止・選曲するまで作り直さない。

## ブラウザ検証

```sh
npm exec -w abservice-visualizer -- playwright install --with-deps chromium
npm run test:visualizer:browser
```

production bundleを独立起動し、ChromiumのソフトウェアWebGPUで描画とライフサイクルを検証する。
文字層はatlasの512×128の領域を同じ縦横比で描画する。ブラウザテストでは文字描画を正方形のマーカーに置き換え、横画面・縦画面の実描画ピクセルから縦横の倍率が等しいことを検証する。
GPUなしのCIでもWGSLとGPU APIの実行経路を検査するための構成であり、会場端末の性能評価には使わない。
画像と失敗時traceはCI artifactに保存する。対象端末の評価条件と残作業は#291を正とする。

画面上のfpsはrAF間隔由来、CPU submitはrender呼び出しの経過時間でありGPU実行時間ではない。
GPU負荷・メモリは対象端末のプロファイラで別途観測する。

WebGPU APIの参照: [W3C WebGPU](https://www.w3.org/TR/webgpu/)。
