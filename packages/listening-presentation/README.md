# Listening presentation data

試聴画面へ渡す表示データ（`PresentationData`）を、検証済みの配布Manifestとartworkの実体から組む。I/OとDOMに依存しない純粋な関数だけを持ち、画面の構成・見せ方（layout・scene・effect）は持たない。試聴画面を作り直す場合も、この写しはそのまま使える。

## 写すもの

- 作品ごとに、作品名・名義（artistDisplayName）・発売日・カタログ番号・原作の出典・説明・artwork・収録曲。値の無い項目と空文字列の項目はキーごと省き、placeholderを事実として持たない。
- artworkは、呼び出し側がasset storeから読んだ検証済みの実体だけを受け取る。渡されなければ省き、欠損時の構成は見せ方の側が選ぶ。読むべきassetIdは `artworkAssetIds` が一度ずつ返す。
- 説明は、MARKDOWNなら共有の描画（`packages/markup`）を通信の無い端末向けの設定で通す。画像はすべて除き、リンクは文字だけを残す。画像は配布物に含まれず取得できず、リンクを開くと試聴の画面から離れて戻れないため。PLAIN_TEXTは段落と改行だけを保ってエスケープする。どちらも生HTMLは描画しない。
- 収録曲は作品の説明で、選んで再生できる項目（`getPlaybackItems`）とは分けて持つ。音源を持たない収録曲を再生項目にしない。
- schema v1/v2のManifestは、表示情報を持たないものとして読む。

## 検証の入口

`npm run test:listening-presentation`。配布応答と同じ見本（`packages/installation/fixtures/manifest-v3.example.json`）を使う単体試験。実ブラウザでの表示は `packages/offline-player` のブラウザ試験で確かめる。
