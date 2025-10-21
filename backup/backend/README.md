# ABService Backend

ABServiceのバックエンドサービスです。Quarkusフレームワークを使用して構築されています。

## 技術スタック

- **フレームワーク**: Quarkus 3.6.0
- **言語**: Java 21+ (一時的対応: Java 25が正式リリースされるまで)
- **ビルドツール**: Gradle Kotlin DSL
- **データベース**: PostgreSQL
- **マイグレーション**: Flyway
- **データアクセス**: Blaze-Persistence
- **認証・認可**: OIDC + Keycloak + JWT
- **API**: RESTEasy Reactive
- **テスト**: JUnit 5 + Mockito

## 前提条件

- Java 21+ (一時的対応: Java 25が正式リリースされるまで)
- Docker & Docker Compose
- Gradle 8.5+

## セットアップ

### 1. 依存関係のインストール

```bash
cd backend
./gradlew build -x test
```

### 2. データベースの起動

```bash
# プロジェクトルートから
docker-compose up -d postgres
```

### 3. アプリケーションの起動

```bash
# 開発モード
./gradlew quarkusDev

# または
npm run dev:backend
```

## プロジェクト構造

```
backend/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/abservice/
│   │   │       ├── controller/     # RESTコントローラー
│   │   │       ├── service/        # ビジネスロジック
│   │   │       ├── entity/         # JPAエンティティ
│   │   │       ├── dto/            # データ転送オブジェクト
│   │   │       ├── config/         # 設定クラス
│   │   │       └── exception/      # 例外クラス
│   │   └── resources/
│   │       ├── application.yml     # アプリケーション設定
│   │       └── db/migration/       # Flywayマイグレーション
│   └── test/
│       └── java/
│           └── com/abservice/
├── config/
│   ├── pmd/                        # PMD設定
│   ├── spotbugs/                   # SpotBugs設定
│   └── checkstyle/                 # Checkstyle設定
├── build.gradle.kts                # Gradleビルド設定
└── README.md
```

## 利用可能なコマンド

### 開発

```bash
# 開発モードで起動（ホットリロード）
./gradlew quarkusDev

# アプリケーションのビルド
./gradlew build

# テストの実行
./gradlew test

# クリーン
./gradlew clean
```

### 静的解析

```bash
# すべての静的解析を実行
./gradlew staticAnalysis

# 個別実行
./gradlew pmdMain pmdTest
./gradlew spotbugsMain spotbugsTest
./gradlew checkstyleMain checkstyleTest
```

### データベース

```bash
# マイグレーションの実行
./gradlew flywayMigrate

# マイグレーションの確認
./gradlew flywayInfo
```

## API仕様

- **OpenAPI**: http://localhost:8080/q/swagger-ui/
- **Health Check**: http://localhost:8080/q/health
- **Metrics**: http://localhost:8080/q/metrics

## テスト

```bash
# すべてのテストを実行
./gradlew test

# 特定のテストクラスを実行
./gradlew test --tests "com.abservice.service.UserServiceTest"

# テストレポートの確認
open build/reports/tests/test/index.html
```

## 静的解析レポート

```bash
# PMDレポート
open build/reports/pmd/main.html

# SpotBugsレポート
open build/reports/spotbugs/main/spotbugs.html

# Checkstyleレポート
open build/reports/checkstyle/main.html
```

## デプロイ

### 開発環境

```bash
./gradlew quarkusDev
```

### 本番環境

```bash
# ネイティブイメージのビルド
./gradlew build -Dquarkus.package.type=native

# JARファイルのビルド
./gradlew build
```

## 設定

### アプリケーション設定

`src/main/resources/application.yml`でアプリケーションの設定を行います。

### データベース設定

```yaml
quarkus:
  datasource:
    db-kind: postgresql
    username: abservice_user
    password: abservice_password
    jdbc:
      url: jdbc:postgresql://localhost:5432/abservice_db
```

### 認証設定

```yaml
quarkus:
  oidc:
    auth-server-url: http://localhost:8180/realms/abservice
    client-id: abservice-backend
    credentials:
      secret: your-client-secret
```

## トラブルシューティング

### よくある問題

1. **Java 25が見つからない**
   - Java 25がインストールされていることを確認
   - `JAVA_HOME`環境変数が正しく設定されていることを確認

2. **データベース接続エラー**
   - PostgreSQLが起動していることを確認
   - 接続情報が正しいことを確認

3. **認証エラー**
   - Keycloakが起動していることを確認
   - クライアント設定が正しいことを確認

### ログの確認

```bash
# アプリケーションログ
tail -f build/quarkus-app/log/quarkus.log

# Dockerログ
docker-compose logs -f postgres
docker-compose logs -f keycloak
```

## 開発ガイドライン

### コーディング規約

- Checkstyleの設定に従う
- PMDの警告を修正する
- SpotBugsの警告を確認する

### コミット規約

```
feat: 新機能の追加
fix: バグ修正
docs: ドキュメントの更新
style: コードスタイルの修正
refactor: リファクタリング
test: テストの追加・修正
chore: ビルドプロセスやツールの変更
```

### プルリクエスト

- 静的解析が通ることを確認
- テストが通ることを確認
- ドキュメントを更新

## ライセンス

このプロジェクトはMITライセンスの下で公開されています。
