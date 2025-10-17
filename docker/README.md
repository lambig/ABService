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
├── keycloak/
│   ├── realm/
│   │   └── abservice-realm.json     # Keycloakレルム設定
│   └── data/                        # Keycloakデータディレクトリ
├── redis/
│   └── data/                        # Redisデータディレクトリ
├── minio/
│   └── data/                        # MinIOデータディレクトリ
└── README.md                        # このファイル
```

## Docker Composeファイル

### docker-compose.yml
本番環境用の基本設定。以下のサービスを含みます：

- **PostgreSQL**: メインデータベース
- **Keycloak**: 認証・認可サーバー
- **Redis**: キャッシュサーバー（将来の使用）
- **Mailhog**: 開発用SMTPサーバー
- **MinIO**: S3互換オブジェクトストレージ

### docker-compose.dev.yml
開発環境用の拡張設定。上記サービスに加えて：

- **pgAdmin**: PostgreSQL管理ツール
- **Redis Commander**: Redis管理ツール
- デバッグ用の設定とログ出力

### docker-compose.override.yml
開発環境用のオーバーライド設定。ローカル開発時の設定を上書きします。

## 使用方法

### 基本サービスの起動

```bash
# 本番環境用サービスを起動
docker-compose up -d

# 開発環境用サービスを起動（管理ツール含む）
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d

# ログを確認
docker-compose logs -f
```

### 個別サービスの起動

```bash
# PostgreSQLのみ起動
docker-compose up -d postgres

# Keycloakのみ起動
docker-compose up -d keycloak
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
- **データベース**: abservice, keycloak
- **ユーザー**: abservice / keycloak
- **パスワード**: abservice / keycloak

### Keycloak
- **ポート**: 8180
- **管理コンソール**: http://localhost:8180
- **管理者**: admin / admin123
- **レルム**: abservice

### Redis
- **ポート**: 6379
- **パスワード**: なし（開発環境）

### Mailhog
- **SMTPポート**: 1025
- **Web UI**: http://localhost:8025

### MinIO
- **APIポート**: 9000
- **コンソール**: http://localhost:9001
- **ユーザー**: minioadmin / minioadmin123

### pgAdmin（開発環境のみ）
- **ポート**: 5050
- **ユーザー**: admin@abservice.com / admin123

### Redis Commander（開発環境のみ）
- **ポート**: 8081
- **ユーザー**: admin / admin123

## データの永続化

各サービスのデータは以下の場所に永続化されます：

- PostgreSQL: `docker/postgres/data/`
- Keycloak: `docker/keycloak/data/`
- Redis: `docker/redis/data/`
- MinIO: `docker/minio/data/`

## トラブルシューティング

### ポートの競合
他のアプリケーションが同じポートを使用している場合：

```bash
# 使用中のポートを確認
lsof -i :5432
lsof -i :8180

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
docker-compose up -d
```

### ログの確認
各サービスのログを確認：

```bash
# 全サービスのログ
docker-compose logs

# 特定のサービスのログ
docker-compose logs postgres
docker-compose logs keycloak

# リアルタイムログ
docker-compose logs -f postgres
```

## セキュリティ注意事項

この設定は開発環境用です。本番環境では以下を必ず変更してください：

1. **パスワード**: すべてのデフォルトパスワードを変更
2. **ネットワーク**: 適切なネットワーク設定を適用
3. **SSL/TLS**: HTTPS接続を有効化
4. **ファイアウォール**: 不要なポートを閉じる
5. **アクセス制限**: 管理者アクセスを制限

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
KEYCLOAK_ADMIN_PASSWORD=your-secure-password
```
