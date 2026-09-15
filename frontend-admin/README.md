# frontend-admin

ABService の管理画面。作品・記事の登録と公開状態の操作を行う。

## 構成

- **Astro（静的出力）**: 公開サイトと同じ構成に揃える。配信は S3 + CloudFront で、Node のサーバを置かない（#125）
- **Svelte（アイランド）**: 画面の中身はブラウザが管理APIから引く。扱うのが下書きを含む編集中の状態のため、組み立てた時点の内容を配れない（ここが公開サイトとの違い）
- **Tailwind CSS + shadcn-svelte**: 見た目の定義は `src/styles/global.css` のトークンに閉じる（DECISIONS 25）

## 画面

| 経路                              | 内容                                     |
| --------------------------------- | ---------------------------------------- |
| `/admin/`                         | 作品の一覧と、公開・非公開・削除の操作   |
| `/admin/albums/new`               | 作品の追加（作品本体の項目）             |
| `/admin/albums/edit?albumId=<id>` | 作品の編集（作品本体の項目、全項目置換） |

編集の対象は経路の一部ではなく問い合わせ文字列で渡す。静的な成果物のため、組み立ての時点に存在する作品しか経路として出せない。経路の綴りは `src/lib/paths.ts` が `base` から組み立てる（プレフィックスを落としたリンクは公開サイトのバケットへ流れて 404 になる）。

**検証エラーの位置は、応答が返した `field`（API の入力パス）をそのまま入力欄の鍵に使う。** 画面は code から欄を導く対応表を持たない（DECISIONS 29）。位置に対応する欄が無いエラーは捨てず、位置を添えて「どの項目にも紐付かないエラー」として出す。トラック・チューン構成・外部音源・カバー画像の編集は持たない（#122）。カバー画像の鍵は読み込んだ値をそのまま送り返す（更新は全項目置換のため、送らないと画像を外す指定になる）。

**編集は読み込んだ時点の世代（`revision`）を条件に保存する**（DECISIONS 30）。編集を始めた後に別の操作が同じ作品を保存していれば 409 になり、画面は入力を保ったまま「最新を読み込む」へ戻す。古い値を自動で再送はしない。

保存できなかったときは入力を捨てない。検証エラー・競合・通信断・鍵の失効のいずれでも、入力はそのまま残る（鍵の入れ直しを挟む場合も、入れ直した後に同じ入力へ戻る）。保存中は入力欄も塞ぐ（送るのは押した時点の入力で、その後の変更は要求に入らない）。

## 認証

入力したAPIキーは `POST /api/v1/admin/sessions` で30分有効なトークンへ交換し、管理操作には `Authorization: Bearer <トークン>` を送る（#264）。機械側のAPIキー認証は引き続き使える。

**APIキーは保存もビルドへの埋め込みもしない。** 入力欄は交換の成否にかかわらず消去する。`sessionStorage` にはトークンと期限だけを保存し、旧バージョンの保存キーは起動時に削除する（`src/lib/credentials.ts`）。ページ移動・リロードでは期限内のセッションを再利用し、独立した新規タブでは再認証する。ブラウザがタブ複製で保存値を複写した場合は同じセッションを共有し、一方の失効後は他方も次の要求で認証を断られる。

ログアウトは保存値を先に消し、サーバーへ失効を要求する。通信に失敗したときは失効未確認と表示する。ログアウト・再認証より前に始まった要求の応答は、認証情報や画面に反映しない。認証切れでは編集中の入力を保持し、明示的なログアウトでは破棄する。トークンもXSSから読める点は変わらない（DECISIONS 22）。

## Svelte

コンポーネントは runes で書く。`svelte.config.js` が `src/` 配下を runes モードで組むため、Svelte 4 の記法（`export let` / `$:` / `$$props`）はビルドで落ちる。runes モードでもコンパイルが通ってしまう `<slot>` と `on:` のイベントディレクティブは、eslint の `svelte/valid-compile` が落とす。

- props は `$props()`、状態は `$state`、導出は `$derived`
- 子の受け渡しはスニペット（`{@render ...}`）
- 親への通知はコールバックの props（`createEventDispatcher` は `no-restricted-imports` で禁じてある）

`let` は状態の宣言（`$state`）にだけ使う。「全ローカル const」の規約と runes は正面から衝突するため（状態は `let` でしか宣言できない）、Svelte では const の強制を外し、`$state` 以外の `let` を lint が塞ぐ形にしている。

テンプレートの分岐は型の絞り込みを持ち越せない。状態から取り出した値は `$derived` でスクリプト側に用意する（テンプレートに `view.albums` と書くと、型情報を使う検査が解決できない）。

## 開発

依存はリポジトリのルートで入れる（npm workspaces）。

```bash
npm install
```

管理画面はブラウザから管理APIを叩くため、DB とバックエンドを起動しておく。

```bash
docker compose up -d postgres
npm run dev:backend
```

```bash
npm run dev -w abservice-frontend-admin
```

開発サーバは 4322 番に上がる。バックエンドの CORS が許すオリジン（`application.properties` の既定）と揃えてある。

## 環境変数

| 変数                  | 用途                          | 既定                    |
| --------------------- | ----------------------------- | ----------------------- |
| `PUBLIC_API_BASE_URL` | ブラウザから叩く管理APIの起点 | `http://localhost:8080` |

`PUBLIC_` で始まるのは Astro の規約で、ブラウザへ出る値であることを示す。鍵はここに置かない。

## API の型

型は OpenAPI から生成する（手書きしない）。バックエンドをビルドして定義を出してから実行する。

```bash
backend/gradlew -p backend quarkusBuild
npm run generate:api-types -w abservice-frontend-admin
```

生成物（`src/lib/api/schema.d.ts`）はコミットする。lint と prettier の対象からは外している。コミットしたものが定義と一致していることは CI が検査する（判断の理由は [DECISIONS.md](../docs/DECISIONS.md) 33）。ルートから `npm run generate:api-types` を叩けば、生成物を持つワークスペースをまとめて作り直せる。

## スクリプト

| コマンド                                        | 内容                                  |
| ----------------------------------------------- | ------------------------------------- |
| `npm run dev -w abservice-frontend-admin`       | 開発サーバ（4322番）                  |
| `npm run build -w abservice-frontend-admin`     | 静的出力を `dist/` へ                 |
| `npm run typecheck -w abservice-frontend-admin` | `astro check`                         |
| `npm run lint -w abservice-frontend-admin`      | 型の生成・prettier のチェック・eslint |
| `npm run format -w abservice-frontend-admin`    | prettier で整形                       |

lint が先に型を作る（`astro sync`）のは、`astro:env` の型が `.astro/` の生成物にあるため。これを持たない状態で eslint を走らせると、環境変数が `any` として扱われ型情報を使う検査が誤った指摘を出す。
