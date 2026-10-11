# 主旋律・和音の製品接続

`prepare.py` は固定した Deep Salience 主旋律モデルと Chordino 和音解析を一つのコマンドで実行し、PWAが読めるJSONを生成する。再生は既存のRust/WASM音声基盤を使い、モデル推論はPCの準備工程で行う。

```sh
python prepare.py --audio input.flac --config engines.json --output analysis.json
```

## 生成環境

設定は `weights`（melody2.h5）、`host`（Sonic Annotator）、`plugin`（NNLS Chroma）、`dictionary`（chord.dict）、`transform`（Vamp transform）の絶対パスと、同じキーを持つ `sha256` オブジェクトを指定する。`runtime` には `numpy`、`scipy`、`librosa`、`tensorflow`、`keras`、`h5py`、`soundfile` のインストール済みバージョンを固定する。実行前後に入力・依存ファイルを照合する。ネットワーク取得は行わない。

主旋律は6系列HCQT、22.05 kHz、hop 256、melody2、開始salience 0.35／継続0.2を使う。20秒以下は全体を一窓で処理する。長尺は約15秒の区間に前後約2秒の文脈を付け、メモリを制限する。長尺分割の境界・局所正規化は全曲一括推論と数値同値ではない。継続判定は区間をまたいで保持する。モデルsalienceは正解確率ではない。

和音は検証済みの短窓transform（block 8192／hop 1024／22.05 kHz）とsus4・5を含む辞書を固定して使う。`N` と末尾の空白は保持する。コードの根音と転回形のベース音を区別する。未知のラベルは推測せず生成を失敗させる。

一時ファイルへ書いた後で完成物を置換する。解析失敗・中断時に既存の出力ファイルは置き換えない。音源・モデル・辞書・transformが生成中に変化した場合も失敗する。出力先には入力や依存ファイルを指定しない。

## PWAでの保存と同期

準備済み音源がある状態で、管理PINを解除し「展示設定 → 主旋律・和音の解析を追加」から生成ファイルを選ぶ。保存済み音源のSHA-256、再生時間、timelineの順序・範囲を照合して保存する。管理入力は通常の試聴画面に出さない。

結果はpackage世代と音源SHA-256に対応付ける。同じ音源でも配布世代が変わった場合は再取込が必要。現在の経路は管理取込であり、配布APIによる自動添付とは区別する。保存済みの結果はオフライン起動後も利用できる。破損・別音源・不正な更新を検出し、更新失敗で前の結果を消さない。

再生では`HTMLMediaElement.currentTime`を使い、左右の閉開区間`[start,end)`を二分探索する。シーク・ループ・作品切替はその時刻の結果を直接参照する。空白区間は未検出であり、前の音高や和音を延長しない。停止・エラー・アンロードでは入力を空にする。pauseでは停止時刻の値を保持する。

演出との境界は`MusicalPlayback`と`musical-moment`イベント。主旋律の連続MIDI音高・salience、和音の根音・種別・ベース音、各区間の開始／終了を渡す。既存の周波数特徴量とは別の入力であり、完成演出の見た目は後続の制作工程で決める。リズム分類は含まない。

端末の読込上限は16 MiB、両系列の合計20万区間、音源長4時間。上限超過は明示的に拒否する。上限は精度や任意の長尺の動作を保証する値ではない。

## 検証

```sh
python -m unittest discover -s tools/musical-analysis -p test_prepare.py
npm run test -w abservice-installation
npm run test:browser -w abservice-offline-player -- e2e/musical-analysis.spec.ts
```

契約試験で空白・境界・重複・非有限値を検査し、ブラウザー試験で実保存、別音源の拒否、破損、オフライン再起動、seek・loop・選択・pauseを確認する。音高や和音名の正解精度および完成演出の採否は、接続試験の合格とは別の判断である。
