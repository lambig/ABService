# Listening distribution client

試聴端末が配布元から package と asset を取得するためのclient。取得だけを行い、保存方式（`packages/offline-storage`）も、準備や再生の画面（`packages/offline-player` など）も知らない。依存はManifestの契約（`packages/installation`）だけで、使う側に求めるのは `fetch` と `AbortSignal` だけ。

## 取得元

| 対象 | 取得元 | 資格情報 |
|---|---|---|
| package | `GET {apiBase}/api/v1/listening/package` | `Authorization: Bearer <listener token>` |
| 音源（Manifestでplayback item・trackの `audioAssetId` として参照されるasset） | 取得の直前に `GET {apiBase}/api/v1/listening/package/assets/{assetId}/url` で署名URLを解決し、そのURLから直接取る | 解決にだけtokenを使う。音源の取得先へは送らない |
| 表示素材（`presentationAssetIds` とalbumの `artworkAssetId`） | `{assetBase}/{assetId}`（公開サイトと同じ配信パス） | なし |

- `apiBase` の既定は同じorigin、`assetBase` の既定は `/assets`。
- どちらの参照も持たないassetは `unsupported-asset` とし、取得しない。
- 取得はすべて `cache: "no-store"`・`credentials: "omit"`・`redirect: "error"` で行う。
- packageの応答は `parseManifest` のstrictな検証に通してから返す。assetのchecksumは照合しない。照合は保存する側（AssetStore）が保存の前後に行う。
- 署名URLは期限付きで、保存しない。期限切れの取得（取得先の403）は `source-rejected` として返す。このclient自身は再試行しない。assetを取り直せば、URLも解決し直される。

## 資格情報の境界

- listener tokenは `parseListenerToken` で形（`abs_device_` と小文字16進64桁）だけを確かめる。有効かどうかは配布APIへの問い合わせで初めて分かる。
- tokenは `createDistributionClient` に渡した接続の中にだけ持ち、保存もログ出力もしない。
- 失敗の値には分類とHTTPステータスだけを載せ、token・署名URL・応答本文を載せない。
- 期限切れと失効は、配布APIの応答（どちらも401）では区別できない。どちらも `unauthorized` として、再認証を求める。

## 失敗の分類

| error | 起きる場面 | 呼び出し側の次の操作 |
|---|---|---|
| `unauthorized` / `forbidden` | 配布APIが401 / 403を返す | tokenを入力し直す |
| `unavailable` | package APIが404（配布が無効） | 準備できない |
| `not-distributed` | URL解決が404（音源が現在のpackageに無い） | packageを取り直す |
| `source-rejected` | 音源の取得先・公開配信が4xx | そのassetを取り直す |
| `server` / `network` | 5xx、または応答を受け取れない（CORSの拒否を含む） | 再試行する |
| `invalid-response` | JSONでない・形が違う・strictな検証に通らない | 準備できない |
| `unsupported-schema` | 読めないschemaVersion | アプリを更新する |
| `unsupported-asset` / `aborted` | 上記の参照を持たないasset／中止 | — |

## 検証の入口

`npm run test:distribution-client`。fetchを差し替えた単体試験で、backendの配布応答と同じ見本（`packages/installation/fixtures/manifest-v3.example.json`）を使う。実backend・実ブラウザでの取得（CORSを含む）は、試聴端末の受け入れ試験で扱う。
