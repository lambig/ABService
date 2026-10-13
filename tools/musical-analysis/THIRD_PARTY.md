# 解析実行器の依存

主旋律のネットワーク構成は [Rachel Bittner / Deep Salience](https://github.com/rabitt/ismir2017-deepsalience) の構成をKerasへ接続する。著作権表示・MITライセンスを `DeepSalience-LICENSE.txt` に保持する。重みは設定で指定し、全テンソルの読戻しを照合する。

和音は外部の Sonic Annotator / NNLS Chroma（Chordino）をプロセスとして実行する。実行ファイル、プラグイン、辞書、transform、モデルはこのディレクトリに同梱していない。各配布元のライセンスを保持した管理環境で準備し、SHA-256で対象版を固定する。

解析出力は推定であり、モデルのsalienceを正解確率へ読み替えない。無出力区間とChordinoの末尾Nをそのまま空白にする。
