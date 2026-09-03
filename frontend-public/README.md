# frontend-public

ABService の公開サイト。アルバムと記事の公開情報を閲覧する画面を持つ。

## 構成

- **Astro（静的出力）**: 記事とアルバムはビルド時にバックエンドの公開 Query API から取得し、HTML へ焼き込む。ブラウザからバックエンドを呼ばない（DECISIONS 24 / #125）
- **Svelte**: クライアント側の操作が要る箇所だけアイランドとして置く
- **Tailwind CSS + shadcn-svelte**: 見た目の定義は `src/styles/global.css` のトークンに閉じる（DECISIONS 25）
- **`packages/markup`**: 記事本文の描画。管理画面のプレビューと同じ関数を呼ぶ

## 開発

依存はリポジトリのルートで入れる（npm workspaces）。

```bash
npm install
```

公開サイトはビルド時にバックエンドを叩くため、DB とバックエンドを起動しておく。

```bash
docker compose up -d postgres
npm run dev:backend
```

```bash
npm run dev -w abservice-frontend-public
```

## 環境変数

| 変数              | 用途                             | 既定                    |
| ----------------- | -------------------------------- | ----------------------- |
| `PUBLIC_SITE_URL` | 正規URL・OG の絶対URLの基準      | `http://localhost:4321` |
| `API_BASE_URL`    | ビルド時に叩くバックエンドの起点 | `http://localhost:8080` |

公開ドメインはリポジトリに書かず、ビルド時に `PUBLIC_SITE_URL` で与える（#129）。

## デザイントークン

色・タイポグラフィ・角丸の定義は `src/styles/global.css` にある。Tailwind の既定パレットは落としてあり、`bg-blue-500` のような直指定はクラスが生成されない。見た目を変えるときはこのファイルのトークンを差し替える。

ライトとダークの2組を `prefers-color-scheme` で持ち、サイト内での切り替え機構は持たない。

## Svelte

コンポーネントは runes で書く。`svelte.config.js` が `src/` 配下を runes モードで組むため、Svelte 4 の記法（`export let` / `$:` / `$$props`）はビルドで落ちる。runes モードでもコンパイルが通ってしまう `<slot>` と `on:` のイベントディレクティブは、eslint の `svelte/valid-compile` が落とす。

強制の範囲は `src/` 配下に限る。コンパイラの設定として置くと `node_modules` の Svelte コンポーネントにも及び、依存が Svelte 4 の記法を含むだけでビルドできなくなる。依存の実装方式はこちらの規約とは関係がない。

- props は `$props()`、状態は `$state`、導出は `$derived`
- 子の受け渡しはスニペット（`{@render ...}`）
- 親への通知はコールバックの props（`createEventDispatcher` は `no-restricted-imports` で禁じてある）

書き方を1つに固定するのは、`let` が状態なのかただの変数なのかをファイルごとに読み替えずに済ませるため。既定では runes を使っているファイルだけが runes モードになり、同じ記法の意味がファイルによって変わる。

## shadcn-svelte

コンポーネントは CLI で追加する。

```bash
npm exec -w abservice-frontend-public -- shadcn-svelte add <component> -y
```

追加されたものは `src/lib/components/ui` に入る。上流の生成物として扱い、lint と prettier の対象から外している。デザインの差し替えはトークンで行い、この配下は編集しない。

## スクリプト

| コマンド                                         | 内容                                  |
| ------------------------------------------------ | ------------------------------------- |
| `npm run dev -w abservice-frontend-public`       | 開発サーバ（4321番）                  |
| `npm run build -w abservice-frontend-public`     | 静的出力を `dist/` へ                 |
| `npm run typecheck -w abservice-frontend-public` | `astro check`                         |
| `npm run lint -w abservice-frontend-public`      | 型の生成・prettier のチェック・eslint |
| `npm run format -w abservice-frontend-public`    | prettier で整形                       |

lint が先に型を作る（`astro sync`）のは、`astro:env` の型が `.astro/` の生成物にあるため。これを持たない状態で eslint を走らせると、環境変数が `any` として扱われ型情報を使う検査が誤った指摘を出す。
