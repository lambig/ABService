# 配布条件と上流表示

このWASM部品を構成する組合せはGPL v3以降の条件で提供する。ChordinoがGPL v2-or-laterであるためv3を選択し、Apache-2.0系runtimeとの組合せを整理する。原ソースのMIT/BSD/Apache等の表示は変更しない。

| 部品 | 版/出所 | 原条件・表示場所 |
| --- | --- | --- |
| NNLS Chroma / Chordino | c4dm/nnls-chroma commit `4c5f214a75cb354f8d8c933e161377c5b4d83713` | GPL-2.0-or-later。`upstream/chordino/COPYING`、各source header |
| Vamp SDK | vamp-plugin-sdk-v2.10 | MIT型、名称の広告利用制限。`upstream/vamp/COPYING` |
| KissFFT | 上記Vamp SDK同梱版 | BSD-3-Clause。`upstream/vamp/COPYING`、`src/vamp-sdk/ext/` |
| Zig support/runtime | Zig 0.15.2 | MIT、`runtime-source/LICENSE`、compiler_rt/std各header |
| libc++ / libc++abi | 上記Zig同梱版 | Apache-2.0 WITH LLVM-exception、旧MIT/UIUC表示。各`LICENSE.TXT`とsource header |
| WASI libc | 上記Zig同梱版 | Apache/LLVM/MITと派生部分BSD-2-Clause/MIT/CC0。`runtime-source/lib/libc/wasi/LICENSE*`、各source内表示 |

Chordino: © Matthias Mauch and Centre for Digital Music, Queen Mary University of London。Vamp: © Chris Cannam / QMUL。KissFFT: © Mark Borgerding。名称を保証・推奨の表示に使わない。

NNLSの由来はLawson/HansonのNetlib FORTRANであり、原ファイル`nnls.f`と変換版`nnls.c`の注記を同梱する。辞書は元のプロファイルを維持してsus4/5を追加したもの。生成済みC++に変更箇所と日付を記録する。

ソース・辞書・adapter・loader・ビルド手順・依存版情報と、この配布物に対応するruntimeソースを一式で保存する。実配布時にはバイナリと対応するソースへの同等のアクセス、GPL本文、各著作権/ライセンス表示を提供する。GitHubリポジトリが存在するだけで対応ソース提供を完了と扱わない。

部品をPWAへ組み込む際には、組合せPWAの対応ソースとビルド手順も配布版に対応付ける。既存MITコードの表示を保持する。ファイル分割やWorker分離だけをGPLの適用除外理由にしない。運用データ・認証情報・非公開検証資料を配布ソースへ含める構成にしない。

無保証・責任制限はGPLv3第15～17条と上記各原ライセンスによる。追加の品質保証・保守契約をこの配布物から付与しない。

参照: [上流Chordino](https://github.com/c4dm/nnls-chroma)、[Apache/GPL互換性](https://www.apache.org/licenses/GPL-compatibility.html)、[LLVMライセンス](https://releases.llvm.org/18.1.8/LICENSE.TXT)。実際に同梱した版のライセンス本文を正とする。
