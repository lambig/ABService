# ABService Backend

ABServiceのバックエンドAPIサーバーです。Quarkusフレームワークを使用して構築されています。

## 技術スタック

- **フレームワーク**: Quarkus 3.6.0
- **Java**: 25+
- **データベース**: PostgreSQL
- **データアクセス**: Blaze-Persistence (JPA拡張)
- **マイグレーション**: Flyway
- **認証・認可**: OIDC + Keycloak + JWT
- **API**: RESTEasy Reactive
- **ビルドツール**: Gradle (Kotlin DSL)

## 前提条件

- Java 25+ (Amazon Corretto推奨)
- Gradle 8.5+
- PostgreSQL 15+
- Keycloak (開発環境)

## セットアップ

### 1. 依存関係のインストール

```bash
# Gradleラッパーを使用して依存関係をインストール
./gradlew build -x test
```

### 2. データベースの準備

PostgreSQLデータベースを作成し、接続情報を設定します。

```sql
CREATE DATABASE abservice;
CREATE USER abservice WITH PASSWORD 'abservice';
GRANT ALL PRIVILEGES ON DATABASE abservice TO abservice;
```

### 3. 環境変数の設定

```bash
# データベース接続情報
export DB_URL=jdbc:postgresql://localhost:5432/abservice
export DB_USERNAME=abservice
export DB_PASSWORD=abservice

# Keycloak設定
export OIDC_AUTH_SERVER_URL=http://localhost:8180/realms/abservice
export OIDC_CLIENT_ID=abservice-backend
export OIDC_CLIENT_SECRET=your-client-secret
```

### 4. 開発サーバーの起動

```bash
# 開発モードで起動
./gradlew quarkusDev
```

## プロジェクト構造

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/abservice/
│   │   │   ├── entity/          # JPAエンティティ
│   │   │   ├── repository/      # データアクセス層
│   │   │   ├── service/         # ビジネスロジック
│   │   │   ├── resource/        # REST APIエンドポイント
│   │   │   ├── dto/             # データ転送オブジェクト
│   │   │   ├── config/          # 設定クラス
│   │   │   └── security/        # セキュリティ設定
│   │   └── resources/
│   │       ├── application.yml  # アプリケーション設定
│   │       ├── db/migration/    # Flywayマイグレーションファイル
│   │       └── META-INF/
│   └── test/
│       ├── java/com/abservice/
│       └── resources/
├── build.gradle.kts             # Gradle設定
├── gradlew                      # Gradleラッパー
└── README.md
```

## 利用可能なコマンド

### 開発

```bash
# 開発モードで起動
./gradlew quarkusDev

# テストの実行
./gradlew test

# 統合テストの実行
./gradlew integrationTest

# アプリケーションのビルド
./gradlew build
```

### データベース

```bash
# マイグレーションの実行
./gradlew flywayMigrate

# マイグレーションの確認
./gradlew flywayInfo

# マイグレーションのロールバック
./gradlew flywayRepair
```

## API仕様

開発サーバー起動後、以下のURLでAPI仕様を確認できます：

- **Swagger UI**: http://localhost:8080/swagger-ui
- **OpenAPI JSON**: http://localhost:8080/q/openapi

## 設定

### データベース設定

`src/main/resources/application.yml`でデータベース接続を設定できます：

```yaml
quarkus:
  datasource:
    db-kind: postgresql
    username: ${DB_USERNAME:abservice}
    password: ${DB_PASSWORD:abservice}
    jdbc:
      url: ${DB_URL:jdbc:postgresql://localhost:5432/abservice}
```

### セキュリティ設定

OIDCとKeycloakの設定：

```yaml
quarkus:
  oidc:
    auth-server-url: ${OIDC_AUTH_SERVER_URL:http://localhost:8180/realms/abservice}
    client-id: ${OIDC_CLIENT_ID:abservice-backend}
    credentials:
      secret: ${OIDC_CLIENT_SECRET:your-client-secret}
```

## テスト

### 単体テスト

```bash
./gradlew test
```

### 統合テスト

```bash
./gradlew integrationTest
```

### テストカバレッジ

```bash
./gradlew jacocoTestReport
```

## デプロイメント

### JARファイルの作成

```bash
./gradlew build
```

### ネイティブイメージの作成

```bash
./gradlew build -Dquarkus.package.type=native
```

### Dockerイメージの作成

```bash
docker build -f src/main/docker/Dockerfile.jvm -t abservice-backend .
```

## トラブルシューティング

### よくある問題

1. **Java 25が見つからない**
   - JAVA_HOME環境変数を正しく設定してください
   - Amazon Corretto JDK 25をインストールしてください

2. **データベース接続エラー**
   - PostgreSQLが起動していることを確認してください
   - 接続情報（URL、ユーザー名、パスワード）を確認してください

3. **Keycloak接続エラー**
   - Keycloakが起動していることを確認してください
   - レルムとクライアント設定を確認してください

### ログの確認

```bash
# アプリケーションログの確認
tail -f logs/abservice.log

# デバッグログの有効化
export QUARKUS_LOG_LEVEL=DEBUG
```

## 開発ガイドライン

### コーディング規約

- Java コーディング規約に準拠
- テストカバレッジ80%以上を維持
- APIドキュメント（OpenAPI）を必ず更新

### コミット規約

```
<type>(<scope>): <description>

<body>

<footer>
```

### プルリクエスト

1. 実装案の承認記録
2. テストの実行・通過
3. ドキュメントの更新
4. レビュアーの指定
