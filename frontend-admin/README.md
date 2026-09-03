# frontend-admin

ABService の管理画面。作品・記事の登録と公開状態の操作を行う。

## 構成

- **Astro（静的出力）**: 公開サイトと同じ構成に揃える。配信は S3 + CloudFront で、Node のサーバを置かない（#125）
- **Svelte（アイランド）**: 画面の中身はブラウザが管理APIから引く。扱うのが下書きを含む編集中の状態のため、組み立てた時点の内容を配れない（ここが公開サイトとの違い）
- **Tailwind CSS + shadcn-svelte**: 見た目の定義は `src/styles/global.css` のトークンに閉じる（DECISIONS 25）

## 認証

管理APIは `Authorization: Bearer <APIキー>` を要求する（#116）。

**鍵はビルドに含めない。** 静的な成果物として配信するため、埋め込むと成果物を受け取れる誰もが管理操作をできることになる。画面で入力を受け取り、`sessionStorage` にタブが開いている間だけ保持する（`src/lib/credentials.ts`）。断られた鍵は捨てる。

鍵を持続させる必要が出たときは、鍵そのものではなく期限付きのトークンを持つ形へ移す。

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

生成物（`src/lib/api/schema.d.ts`）はコミットする。lint と prettier の対象からは外している。

## スクリプト

| コマンド                                        | 内容                                  |
| ----------------------------------------------- | ------------------------------------- |
| `npm run dev -w abservice-frontend-admin`       | 開発サーバ（4322番）                  |
| `npm run build -w abservice-frontend-admin`     | 静的出力を `dist/` へ                 |
| `npm run typecheck -w abservice-frontend-admin` | `astro check`                         |
| `npm run lint -w abservice-frontend-admin`      | 型の生成・prettier のチェック・eslint |
| `npm run format -w abservice-frontend-admin`    | prettier で整形                       |

lint が先に型を作る（`astro sync`）のは、`astro:env` の型が `.astro/` の生成物にあるため。これを持たない状態で eslint を走らせると、環境変数が `any` として扱われ型情報を使う検査が誤った指摘を出す。
