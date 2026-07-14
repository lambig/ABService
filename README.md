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
│   ├── service/                 # コマンドサービス（更新系）
│   │   └── CommandService.java  # CQRS基底インターフェース
│   └── query/                   # クエリサービス（照会系）
│       └── QueryService.java    # CQRS基底インターフェース
├── infrastructure/              # インフラ層
│   ├── persistence/             # 永続化実装
│   │   ├── AuditableEntity.java # 共通監査列を持つ基底クラス
│   │   └── AuditInfo.java       # 監査情報を保持するデータクラス
│   └── datetime/                # 日時プロバイダー実装
│       └── SystemBusinessDateTimeProvider.java
└── presentation/                # プレゼンテーション層
    └── rest/                    # RESTエンドポイント
```

## ドメイン駆動設計とCQRS

ABServiceはドメイン駆動設計（DDD）とCQRSパターンに基づいています：

- **値オブジェクト**: 不変性、等価性、副作用なし（Java Records推奨）
- **エンティティ/集約**: 同一性、Lombok `@With`による不変更新パターン
- **集約**: 整合性境界、ID参照
- **CQRS**: コマンド（更新系）とクエリ（照会系）の明確な分離
- **ドメインID**: UUIDv7形式（DB内部IDと分離）
- **業務日付/日時**: Asia/Tokyoタイムゾーン固定
- **共通監査列**: すべてのエンティティに7つの監査列を含める

### ドキュメント

- [アーキテクチャ](docs/ARCHITECTURE.md) - システム全体の設計
- [コーディングガイドライン](docs/CODING_GUIDELINES.md) - 日々の実装規約
- [ID設計ポリシー](docs/ID_DESIGN_POLICY.md) - ドメインIDとDB内部IDの分離方針
- [ドメインモデル設計](docs/DOMAIN_MODEL_DESIGN.md) - DDDの実装詳細
- [共通監査列ガイドライン](docs/AUDIT_COLUMNS.md) - 監査列の標準

## 共通監査列

すべてのデータベーステーブルは、以下の7つの監査列を含む必要があります：

1. `created_at` - レコード作成日時
2. `updated_at` - レコード最終更新日時
3. `created_by_service` - 作成時のサービス名
4. `updated_by_service` - 更新時のサービス名
5. `created_by_user` - 作成者ユーザーID
6. `updated_by_user` - 更新者ユーザーID
7. `version` - 楽観ロック用バージョン番号

詳細は [docs/AUDIT_COLUMNS.md](docs/AUDIT_COLUMNS.md) を参照してください。

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

- **Checkstyle**: Google Java Style Guideに基づくコードスタイルチェック＋独自ルール
- **Spotless**: 自動コードフォーマッタ（Eclipse JDT）
- **PMD**: 独自XPathルール（機能的スタイル強制）＋組込の不要変数検出
- **ArchUnit**: アーキテクチャ制約（レイヤー依存方向・配置・戻り値契約など）をテストで強制

> **注意**: SpotBugs は現在未導入です（4.10.2 で Java25 対応済み。再導入はロードマップのフェーズD で検討）。

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
- `pre-commit`: 品質ゲート（Spotless / Checkstyle / PMD / ユニット+ArchUnit テスト）を実行
- `pre-push`: ビルドとテストを実行

**Hooksをスキップする場合（非推奨）:**
```bash
git commit --no-verify
git push --no-verify
```

#### レポート

コード品質チェックのレポートは以下に生成されます：

- Checkstyle: `backend/build/reports/checkstyle/`
- PMD: `backend/build/reports/pmd/`
- Spotless: コンソール出力

#### SpotBugsについて

SpotBugs は現在このプロジェクトでは未導入です。
- 最新版 4.10.2（2026-06）は Java 25 に対応済み（ASM 9.8 / BCEL 6.11）。Gradle plugin は 6.5.8。
- 再導入（PMD 組込ルールセットと併せた errorprone/バグパターン検出）はロードマップのフェーズD で検討する。
