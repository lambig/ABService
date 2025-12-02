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

