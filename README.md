# ABService

WebService/Site implementation for my own use

## 技術スタック

- **Java**: Amazon Corretto 25
- **Quarkus**: 3.28.4
- **Gradle**: 9.1.0
- **Lombok**: 1.18.42 (Java 25対応版)
- **Node.js**: 18+
- **Docker**: Latest

## 動作確認済み

- ✅ ビルド: 成功
- ✅ テスト: 成功
- ✅ サーバー起動: 成功
- ✅ Webアクセス: 成功

## バックエンド パッケージ構造

```
com.abservice/
├── domain/                      # ドメイン層
│   ├── model/                   # ドメインモデル
│   │   ├── DomainObject.java
│   │   ├── EntityId.java
│   │   ├── entity/              # エンティティ
│   │   │   └── DomainEntity.java
│   │   ├── aggregate/           # 集約
│   │   │   └── Aggregate.java
│   │   └── vo/                  # 値オブジェクト
│   │       ├── ValueObject.java
│   │       ├── BusinessDate.java
│   │       └── BusinessDateTime.java
│   ├── service/                 # ドメインサービス
│   │   ├── DomainService.java
│   │   └── BusinessDateTimeProvider.java
│   ├── factory/                 # ファクトリ
│   │   └── Factory.java
│   ├── repository/              # リポジトリインターフェース
│   └── exception/               # ドメイン例外
│       └── DomainException.java
├── application/                 # アプリケーション層
│   ├── service/                 # アプリケーションサービス
│   └── dto/                     # データ転送オブジェクト
├── infrastructure/              # インフラ層
│   ├── persistence/             # 永続化実装
│   └── datetime/                # 日時プロバイダー実装
│       └── SystemBusinessDateTimeProvider.java
└── presentation/                # プレゼンテーション層
    └── rest/                    # RESTエンドポイント
```

## ドメイン駆動設計

ABServiceはドメイン駆動設計（DDD）の原則に基づいています：

- **値オブジェクト**: 不変性、等価性、副作用なし（Java Records推奨）
- **エンティティ/集約**: 同一性、Lombok `@With`による不変更新パターン
- **集約**: 整合性境界、ID参照
- **業務日付/日時**: Asia/Tokyoタイムゾーン固定

詳細は [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) を参照してください。

## 開発

### ビルドとテスト

```bash
# バックエンドのビルド
cd backend
./gradlew build

# テスト実行
./gradlew test

# 開発モードで起動
./gradlew quarkusDev
```

### コード品質

ABServiceでは以下のLinting/フォーマットツールを使用しています：

- **Checkstyle**: Google Java Style Guideに基づくコードスタイルチェック
- **Spotless**: 自動コードフォーマッタ（Eclipse JDT）

> **注意**: SpotBugsはJava 25との互換性問題により現在無効化されています。Java 25対応版がリリースされ次第、再度有効化する予定です。

#### コード品質チェック

```bash
# すべてのコード品質チェックを実行
./gradlew check

# Checkstyleのみ実行
./gradlew checkstyleMain checkstyleTest

# Spotlessフォーマットチェック
./gradlew spotlessCheck
```

#### コードフォーマット

```bash
# コードを自動フォーマット
./gradlew spotlessApply

# ビルド時に自動フォーマットが実行されます
./gradlew build
```

#### Git Hooks

コミット前・プッシュ前に自動でコード品質チェックを実行できます：

```bash
# プロジェクトルートで実行（初回のみ）
./scripts/setup-git-hooks.sh
```

**導入されるhooks:**
- `pre-commit`: コード整形とCheckstyleを実行
- `pre-push`: ビルドとテストを実行

**Hooksをスキップする場合（非推奨）:**
```bash
git commit --no-verify
git push --no-verify
```

#### レポート

コード品質チェックのレポートは以下に生成されます：

- Checkstyle: `backend/build/reports/checkstyle/`
- Spotless: コンソール出力

#### SpotBugsについて

⚠️ SpotBugsは現在Java 25のバイトコード(major version 69)に対応していないため、このプロジェクトでは使用していません。
- 最新版: 4.9.8 (2024年10月)
- Java 25でのビルドはサポートされていますが、実行時の解析は未対応です

