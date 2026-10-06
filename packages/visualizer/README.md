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

- 状態: `idle` / `selected` / `playing` / `paused` / `seeking` / `ended` / `error`。停止後・終了後の戻り先は `selected`。選択が残る停止は、再生前（`selected`）やエラー（`error`）からでも `selected` へ戻り、描画値を静止値へ戻す。
- `transition(state, event)` が次の状態と、描画値を静止値へ戻すか（`resetFrame`）を返す。選び直し・seek・停止・失敗・clearでは戻し、旧い作品・旧い再生位置の平滑化とimpulseを残さない。一時停止では戻さず、直前の構図を保つ。
- その状態で起きえない出来事（例: `idle` での `play`、`seeking` 以外での `seeked`）では状態を変えない。順序の乱れた通知から状態を作らない。
- rendererへの入力は `PresentationInput`（状態・`PresentationFrame`・作品の表示内容）だけ。rendererはAudioFeatures・Manifest・playerを参照しない。表示内容は検証済みの表示データ（`packages/listening-presentation`）の作品 `ListeningAlbum` をそのまま渡し、どの事実をどう見せるかは renderer の側が決める。`listening-presentation` へは型だけで依存する。
- `PresentationInput` は状態ごとに形を分ける。`idle` は表示内容を持たず、`error` は任意、それ以外は必ず持つ。成り立たない組み合わせは型の段階で作れない。
- `followPlayback(state, previous, next)` は再生の観測（`PlaybackObservation`）の前後から出来事を作り（`playbackEvents`）、状態を進める。新しい読み込みは select、停止して選択が残れば stop・残らなければ clear、`seeking` に入れば seek、出れば出た先の再生の有無を持つ seeked。位置の更新だけの観測からは出来事を作らない。
- visualizer は player に依存しない。`PlaybackObservation` は `abservice-player` の `PlayerSnapshot` が構造的に満たす形だけを受け取る（`listening-presentation` に置くと、その型に依存する visualizer との間で依存が循環するため）。
- rendererが表示内容を描くことは #479 B が受け持つ。

現行の描画APIは `render(PresentationFrame)`。代表負荷モードでは初期化時の `RendererOptions.content` に検証済み `ListeningAlbum` を渡し、artwork・作品名・名義のatlasを生成する。`PresentationInput` は完成形の状態契約であり、この測定用接続で全描画APIの移行や #479 B の完成を扱わない。素材のdecode・書体待ちの間に破棄された場合はGPUへの転送を中止し、画像とdeviceを解放する。

## 描画セッションの監督と縮退（#291 A）

`src/session.ts` の `superviseRenderer` は、1つの描画セッションのrendererの寿命を監督する。rendererを作る関数は呼び出し側が渡す（GPUなしで単体試験できる）。

- device lostでは今のrendererを破棄し、作り直しを**1回だけ**試す。描画中の例外もlostと同じに扱う。
- 作り直しが失敗するか、作り直したrendererもまたlostしたら `degraded` へ移り、無限に再試行しない。WebGPUが使えない・初期化に失敗した場合も `degraded`。
- `degraded` で止めるのは描画だけ。再生・作品情報・操作は呼び出し側で保つ。
- `dispose` の後は、遅れて完了したrendererも破棄し、lostも無視する。
- 回転・fullscreen復帰は、rendererが毎フレームcanvas寸法を照合して追う（作り直さない）。

`offline-player` は再生のたびにこの監督を作り、一時停止・停止・選曲・pagehideで破棄する。GPU資源を持つのは再生中だけ。

`planSession(step, frame)` は状態の変化から、描画の寿命（`draw` / `keep` / `still` / `release`）と次の描画値を決める純粋な関数。GPU を放すことと描画値を戻すことは分け、描画値は `resetFrame` のときだけ静止値へ戻す（例: 最後まで再生して `ended` へ移るときは GPU を放すが描画値は保つ）。ページを閉じるときの破棄だけは描画値まで捨てる。

- 一時停止では、GPU資源を放す直前に今の構図を静止画へ写してcanvasの背景に敷き、直前の構図を保つ（#479 paused）。描画値と縮退の判定は一時停止をまたいで残す。
- 再開で新しい監督を作り、描き始めるか縮退した時点で静止画を外す。一度縮退した描画は、停止・選曲するまで作り直さない。

## 負荷ノブと観測（#291 B）

`src/budget.ts` の `RendererBudget` は、#479 の表現要素を削らずに下限の端末で予算へ戻すための調整だけを持つ。

| ノブ | hash の名前 | 範囲 | 既定 |
|---|---|---|---|
| DPR の上限 | `dpr` | 0.5–3 | 2 |
| 内部の描画解像度の倍率（低く描いて拡大） | `scale` | 0.25–1 | 1 |
| effect の強さの倍率 | `effects` | 0–1 | 1 |
| 描画の上限 fps（速い rAF は見送る） | `fps` | 10–120 | 60 |

既定値は現行の見え方を変えない。`offline-player` は URL の hash（例: `#probe&scale=0.5&fps=30`）から読む。query ではなく hash を使うのは、Service Worker が query 付きのナビゲーションを扱わず、オフラインで開けなくなるため。

`src/probe.ts` はフレームごとの観測を直近 600 フレームだけ持ち、分布（p50 / p95 / max）を返す。次の三つを分けて記録する。

- cycle CPU: 1 回の描画までに main thread が使った時間の合計。特徴量から描画値への変換（`mapFeatures`）・presentation state・作品情報などの DOM 更新・renderer を含む。worker・GPU 寄せ・WASM Spike の要否はこれで判断する。呼び出し側が測って `cycle` で渡す。
- renderer CPU: renderer の中で描画値を書いて submit するまで（WebGPU の命令作成の CPU コスト）。
- GPU: submit から `onSubmittedWorkDone` まで（queue の待ちを含む上限値）。

CPU と GPU を分けるのは、どちらが律速かで効く対策が違うため（`docs/DECISIONS.md`「試聴端末は廉価な大判寄りのタブレットを下限とし、CPU と GPU の配分は一次性能ゲートの実測で決める」）。`offline-player` は `#probe` のとき、この要約と起動時の計測点（script の評価・曲目の準備・最初のフレーム）を画面に重ねて表示する。

層ごとの解像度（背景・effect を低く、文字・artwork を高く）は、現行の renderer が1パスの全画面描画のため未対応。パスの分け方は #479 B の scene と合わせて決める。

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
