# seed-loader

初期データ（サイトの文言・作品・記事・カバー画像）を**管理API経由で**投入する（#373）。

SQL で直に入れない。SQL は値オブジェクトの検証（ISDN のチェックデジット、トラック名の規則、マークアップの形式）を
すべて迂回し、壊れた行が画面で初めて見つかる。管理APIを通せば、入ること自体が検証になる（#164 の方針）。
管理APIクライアントは `packages/admin-api` で、E2E のシードと同じ経路を通る。

**投入する内容はこのリポジトリに置かない。** 置き場は運用リポジトリ（#375）で、ここが持つのは仕組みだけ。

## 使い方

```sh
SEED_API_BASE_URL=<バックエンドの起点> ADMIN_API_KEY=<管理APIの鍵> \
  npm run load -w abservice-seed-loader -- --dir <投入ディレクトリ> --dry-run
```

| 名前                | 意味                                                                                                                                                                   |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `SEED_API_BASE_URL` | バックエンドの起点。本番は配信のオリジン（`/api/*` を CloudFront が backend へ流す）。`/api` は含めない                                                                |
| `ADMIN_API_KEY`     | 管理APIの鍵。本番は Parameter Store から取る（`infra/README.md`「管理者APIキー」）。機械側は期限付きセッションへ交換せず、鍵をそのまま Bearer で使える（DECISIONS 22） |
| `--dir`             | 投入ディレクトリ                                                                                                                                                       |
| `--dry-run`         | 形を確かめ、投入先の今の状態と突き合わせた計画を出して終わる。書き込みは送らない                                                                                       |

`--dry-run` を外すと、同じ計画を先に出してから、その計画どおりに送る。終了コードは、成功が 0、投入ファイルの
誤り・計画の問題・投入の失敗が 1、引数や環境変数の不足が 2。

## 投入ディレクトリの形

```
<dir>/
  site/<key>.md | <key>.txt            サイトの文言。ファイル名がキー、拡張子が形式（Markdown / プレーンテキスト）
  albums/<catalogNumber>/album.json    作品。ディレクトリ名がカタログナンバー
  albums/<catalogNumber>/description.md | description.txt   概要説明（任意。どちらか1つ）
  albums/<catalogNumber>/<画像>         album.json の coverImage が指す（任意。png / jpg / jpeg / webp）
  articles/<name>/article.json         記事。ディレクトリ名の順に公開する
  articles/<name>/body.md | body.txt   本文（任意。どちらか1つ）
```

同定に使う値（カタログナンバー・キー）はファイルの中に書かない。ディレクトリ名・ファイル名から取る。記事は
タイトルで同定し、ディレクトリ名は並び順だけを決める。**公開の順序がそのまま一覧の並び（新しいものが先）に
なる**ため、先頭に置きたい記事を最後の名前にする。

本文ファイルの末尾の改行は1つだけ落とす（編集器が付ける改行を内容にしない）。

### `album.json`

```json
{
  "title": "作品名",
  "releaseDate": "2026-04-01",
  "artistDisplayName": "名義",
  "artistSortKey": "めいぎ",
  "isdn": "2784000001004",
  "event": {
    "name": "初出イベント",
    "date": "2026-03-30",
    "place": "会場",
    "spaceNumber": "A-01",
    "note": "補足"
  },
  "basePrice": { "amount": 1500, "currency": "JPY" },
  "originalWorkNote": "「原作」より各曲",
  "tracks": [
    {
      "title": "トラック名",
      "artistDisplayName": "トラックの名義",
      "tunes": [
        {
          "tuneTitle": "チューン",
          "composerCreditOverride": "作曲",
          "arrangerCreditOverride": "編曲"
        }
      ]
    },
    { "tunes": [{ "tuneTitle": "名を持たないトラックのチューン" }] }
  ],
  "externalAudioUrls": ["https://soundcloud.com/..."],
  "coverImage": "cover.png",
  "published": true
}
```

必須は `title` / `releaseDate` / `artistDisplayName` / `artistSortKey` / `published`。他は省ける（省いた項目は
API の既定に従う）。並びは配列の位置がそのまま表すため、番号は書かない（#391）。トラック名を省くとチューン名を
繋いだものが名になる（#360）。

### `article.json`

```json
{
  "articleType": "ALBUM",
  "title": "記事のタイトル",
  "introShort": "一覧のカードに出る短い紹介",
  "album": "<参照先のカタログナンバー>",
  "tags": ["タグ"],
  "published": true
}
```

`articleType` は `ALBUM` / `NOTE` / `NEWS` / `EVENT` / `OTHER`。作品を参照できるのは `ALBUM` だけで、`ALBUM` は
`album` を要する。参照先は投入内容の作品か、投入先に既にある作品。

### 形の誤りは送る前に落とす

知らない項目（綴り違い）、必須項目の欠け、空文字、片方しか置けないファイルの重複、無い画像、受け入れない
画像形式、同じタイトルの記事、解けない作品参照は、どのファイルのどの項目かを示して止まる。値の妥当性
（ISDN のチェックデジット、Markdown の形式、URL の形）はバックエンドが検証し、送った時点で落ちる。

## 冪等性と中断からの再開

計画は**無いものだけを作る**。

| 対象 | 無いとき                                             | あるとき                                                                                    |
| ---- | ---------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| 文言 | 登録する                                             | 何もしない。内容が違っても揃えない（報告に出す）                                            |
| 作品 | 作り、`published` なら公開する                       | 内容は揃えない。画像が無く投入内容が画像を持つなら付ける。下書きで `published` なら公開する |
| 記事 | 作り（作品参照・タグ込み）、`published` なら公開する | 内容は揃えない。下書きで `published` なら公開する                                           |

公開後の内容の正は DB（管理画面）であり、投入ファイルは一度きりの出発点でしかない（#375）。「あるとき」に
埋めるのは「作ったが公開・画像まで進まなかった」という途中の状態だけで、これは前回の実行が中断した跡になる。

途中で落ちたら、その段を示して止まる。**原因を直してから同じコマンドを再実行する**と、入った分は飛ばして続きから
進む。控えのファイルや状態は持たない——投入先の今の状態そのものが「どこまで入ったか」を表す。

注意: 投入後に管理画面で意図して下書きへ戻した作品・記事は、投入ファイルが `published: true` のままだと
再実行で公開される。初期投入は一度きりのものとして扱い、公開後に流し直さない。

## リハーサルと受け入れ

**空の DB に対して通しで1回リハーサルしてから本番へ流す。** 手順は運用リポジトリの `seed/README.md` が持つ。

機構の受け入れは `scripts/acceptance.mjs` が、E2E 用のスタック（`npm run dev:backend:e2e`）に対して合成の内容で
通す。dry-run が書き込みを送らないこと、途中から再開できること、2回流しても変わらないこと、誤りが送る前に
落ちることを見る。CI の E2E ジョブが Playwright の後に実行する。

```sh
docker compose up -d postgres minio minio-init
npm run dev:backend:e2e          # 別のシェルで
npm run acceptance -w abservice-seed-loader
```
