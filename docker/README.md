# Docker Configuration for ABService

このディレクトリには、ABServiceの開発環境用Docker設定が含まれています。

## ファイル構成

```
docker/
├── postgres/
│   ├── conf/
│   │   └── postgresql.conf          # PostgreSQL設定ファイル
│   ├── init/
│   │   └── 01-init-databases.sql    # データベース初期化スクリプト
│   └── data/                        # PostgreSQLデータディレクトリ
├── minio/
│   └── data/                        # MinIOデータディレクトリ
└── README.md                        # このファイル
```

## Docker Composeファイル

### docker-compose.yml
ローカル開発・CI専用の基本設定。すべての公開ポートは127.0.0.1に限定する。以下のサービスを含みます：

- **PostgreSQL**: メインデータベース
- **MinIO**: S3互換オブジェクトストレージ

### docker-compose.dev.yml
名前付きボリュームとPostgreSQLの明示的なログ設定を使う構成です。
基本設定と組み合わせて指定し、既定のoverrideとは併用しません。

### docker-compose.override.yml
既定で基本設定へ重なる開発用設定です。PostgreSQL・MinIOのデータをホストのディレクトリへ保存します。
公開ポートは基本設定の127.0.0.1限定を引き継ぎます。
dev構成は名前付きボリュームを使うため別に保持し、既存データを暗黙に移し替えません。

## 使用方法

PostgreSQLとMinIOは固定したソース・上流イメージと必要な修正からローカルでビルドします。Dockerfileの変更後は `--build` を付けて再起動します。既存DBのボリュームは保持します。

### 基本サービスの起動

```bash
# ローカル開発用サービスを起動
docker-compose up -d --build

# 名前付きボリュームとPostgreSQLのログ設定を使って起動
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build

# ログを確認
docker-compose logs -f
```

### 個別サービスの起動

```bash
# PostgreSQLのみ起動
docker-compose up -d postgres

# MinIOとバケット初期化
docker-compose up -d minio minio-init
```

### サービスの停止

```bash
# 全サービスを停止
docker-compose down

# ボリュームも削除
docker-compose down -v
```

## サービス情報

### PostgreSQL
- **ポート**: 5432
- **データベース**: abservice（開発）, abservice_test（統合テスト）, abservice_e2e（E2E）
- **ユーザー**: abservice
- **パスワード**: abservice

テストは開発用と別のデータベースへ書く（#252）。統合テストは実行のたびに `abservice_test` を空にする。
E2E用のbackendの起動・初期化・停止は[E2Eガイド](../e2e/README.md)を参照する。

テスト用のデータベースは初期化スクリプト（`docker/postgres/init/01-init-databases.sql`）が作る。
このスクリプトはデータ領域が空のときだけ走るため、**既にボリュームがある環境では手で作る**。

```bash
docker-compose exec -T postgres psql -U abservice -d abservice -c "CREATE DATABASE abservice_test"
docker-compose exec -T postgres psql -U abservice -d abservice -c "CREATE DATABASE abservice_e2e"
```

### MinIO
- **APIポート**: 9000
- **コンソール**: http://localhost:9001
- **ユーザー**: minioadmin / minioadmin123
- **バケット**: `abservice-assets`（アセット保管先）。`minio-init` サービスが起動時に作成する一過性コンテナで、
  MinIO が healthy になってから `mc mb` を実行して終了する
- **配信対象の読み取り**: `minio-init` は配信対象の接頭辞（`assets/`）だけを鍵なしで読める状態にする。
  本番は CloudFront が OAC で S3 を読み、配信するのは同じ接頭辞だけで、受け入れ前（`pending/`）は
  配信パスの外にある（`infra/README.md`）。ローカルには CloudFront が無いため、同じ「配信対象だけが届く」
  形をバケットの側で作る。画面が指す `/assets/*` を保管先へ取り次ぐのは、開発サーバでは Astro の
  設定（両フロントの `astro.config.mjs`）、E2E では静的配信（`e2e/scripts/serve-app.mjs`）

アセットの統合テストは実際に MinIO へ署名付きURLで PUT するため、テスト実行前に以下で起動する。

```bash
docker compose build minio minio-init
docker compose up -d minio minio-init
```

#### MinIO の取得と更新

Docker Hub / Quay の既成イメージ取得障害（#351、#459）を避けるため、
[公式のソース公開方針](https://github.com/minio/minio#source-only-distribution)に従い、
本体と `mc` を公式リポジトリからビルドする。固定コミット・Go・ベースイメージの正は
[`minio/Dockerfile`](minio/Dockerfile)。ローカル開発と CI のみに使い、本番 S3 の構成は変更しない。
初回は Go の依存取得とコンパイルが必要で、GitHub・Go module proxy・Docker Hub・Debian の取得元へ接続する。
通常の再実行には Docker のビルドキャッシュを利用できる。

更新時は公式のリリースとセキュリティ修正を確認し、タグに対応する完全なコミット ID と
イメージの revision ラベルを揃えて変更する。Go は保守対象系列のパッチ版、ベースイメージは
検証したダイジェストへ更新する。`latest` や取得可能な古い MinIO イメージへ戻さない。
ソースとベースは固定するが、Debian パッケージの更新は取り込むため、バイト単位の同一性は保証しない。

更新の受け入れは `docker compose build --no-cache --pull minio minio-init` で両方を作り直し、
起動・バケット初期化に続いて Backend full check と実スタック E2E を最後まで通す。
署名付き PUT、アセットの読み取り・削除、`assets/` の匿名読み取りと `pending/` の非公開性を確認する。
ビルドしたバイナリの `--version` には上流コミットと Go の版が出る。ライセンスはイメージ内に保持する。

## データの永続化

各サービスのデータは以下の場所に永続化されます：

- PostgreSQL: `docker/postgres/data/`
- MinIO: `docker/minio/data/`

## トラブルシューティング

### ポートの競合
他のアプリケーションが同じポートを使用している場合：

```bash
# 使用中のポートを確認
lsof -i :5432
lsof -i :9000

# プロセスを終了
kill -9 <PID>
```

### データのリセット
開発中にデータをリセットしたい場合：

```bash
# サービスを停止してボリュームを削除
docker-compose down -v

# データディレクトリを削除
rm -rf docker/*/data

# 再起動
docker-compose up -d --build
```

### ログの確認
各サービスのログを確認：

```bash
# 全サービスのログ
docker-compose logs

# 特定のサービスのログ
docker-compose logs postgres
docker-compose logs minio

# リアルタイムログ
docker-compose logs -f postgres
```

## セキュリティ注意事項

この構成と例示の資格情報はローカル開発・CI専用です。本番リリースに流用しません。
本番構成とリリース手順は[infra/README.md](../infra/README.md)を参照してください。

## カスタマイズ

### 新しいサービスの追加
`docker-compose.yml`に新しいサービスを追加できます。

### 設定の変更
各サービスの設定ファイルを編集して、`docker-compose down && docker-compose up -d`で再起動してください。

### 環境変数の設定
`.env`ファイルを作成して環境変数を設定できます：

```bash
# .env
POSTGRES_PASSWORD=your-secure-password
```

## ローカル・CI用ストレージの制限

MinIOはCVE-2026-40344/41145への対策としてunsigned-trailer要求を認証ミドルウェアの入口で拒否する。
通常のSigV4要求を使う。これは上流OSS全体の修正完了を意味しない。
S3 Selectとレプリケーションヘッダーも拒否し、CVE-2026-39414/34204の経路を無効にする。
この構成ではOIDC・LDAPを設定しない。これらの機能を追加する場合は、未修正のCVE-2026-33322/33419を再評価する。
根拠は[認証回避](https://github.com/minio/minio/security/advisories/GHSA-hv4r-mvr4-25vw)、
[S3 Select](https://github.com/minio/minio/security/advisories/GHSA-h749-fxx7-pwpg)、
[レプリケーションヘッダー](https://github.com/minio/minio/security/advisories/GHSA-3rh2-v3gr-35p9)の上流勧告を参照する。

`python3 scripts/check-minio-auth.py`はlocalhostの検証用MinIOに対し、ヘッダー・クエリ認証の6経路、レプリケーションヘッダー、S3 Selectの拒否と正常なSigV4 CRUDを確認する。
ローカル・試験用SDKはチェックサムをヘッダーへ載せ、unsigned-trailerを使用しない。チェックサム検証は有効のまま維持する。
