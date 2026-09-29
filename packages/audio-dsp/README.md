# Audio DSP PoC (#290)

ブラウザから独立したJS参照DSP。数値処理の意味論を先に照合するための実装であり、AudioWorkletの締切やAndroid実機性能を保証しない。性能のための可変バッファやWASM採用は実測に基づいて判断する。

左右逆相の音楽を無音として扱わないため、チャンネル間で波形を足す代わりにパワーを平均する。周波数観測は窓補正後のパワーであり、過渡信号では無窓RMSの二乗と一致しない。

立ち上がりは3帯域の振幅増分から求める。ビート時刻の推定や全FFT binのspectral fluxとして解釈しない。定常入力の数値誤差で微小な立ち上がりを出し続けないよう閾値を持つ。正規化と窓・時刻の定義は実装のJSDoc、境界事例は単体テストを正とする。

AudioWorklet の中には高分解能の時計が無い（Chromium では `performance` が無く、`Date.now` の 1 ms 分解能だけ）。そのため quantum ごとの処理時間の分布は `benchmarkFeatureStream` が同じ DSP を別スレッドで quantum ずつ測った近似として出し、Worklet の中では通知の区間の平均負荷率だけを測る（`abservice-audio-worklet` の `meanLoad`）。出力の途切れは `AudioContext.playbackStats` で見る。近似と実際の差は、判断に余裕を持たせて吸収する。

検証は `npm run test:audio-dsp`、`npm run typecheck:audio-dsp`、`npm run lint:audio-dsp`。端末評価とJS/WASM比較は #290 が追跡する。
