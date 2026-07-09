# テスト追加計画（残: Phase 6–10）

> **進捗の正は [docs/STATUS_AND_ROADMAP.md](../docs/STATUS_AND_ROADMAP.md) §5.2。**
> ユニットテスト（VO / Enum / 集約 / エンティティ）は整備済みのため、本書は未着手の統合テスト系フェーズ（6–10）を計画として扱う。完了済みフェーズの対象クラスは §5.2 と git 履歴を参照。

各フェーズは依存する実装が満たされた時点で着手する。

## Phase 6: Application Service のテスト

- 対象: 今後実装する具象ユースケース（`CommandService` / `QueryService` 実装）。基底インターフェースは `application/service/CommandService.java`・`application/query/QueryService.java`。
- 種別: ユニットテスト（`src/test/java/`）。リポジトリを Fake / モック化してロジックを検証。
- 前提: アプリケーション層のユースケース実装。

## Phase 7: Repository 実装の統合テスト 🔴

- 対象: `infrastructure/persistence/repository/` の `AlbumRepositoryImpl` / `ArticleRepositoryImpl` / `TuneRepositoryImpl` / `AlbumArticleRepositoryImpl`。
- 種別: 統合テスト（`src/integrationTest/java/`・`@QuarkusTest`・実DB）。
- 状態: `AlbumRepositoryImpl` は統合テスト済み。残り3集約への追加は #45。

## Phase 8: Mapper の統合テスト

- 対象: `infrastructure/persistence/mapper/` の `AlbumMapper` / `ArticleMapper` / `TuneMapper` / `AlbumArticleMapper`（ドメインモデル ⇔ エンティティ変換）。
- 種別: 統合テスト（関連エンティティの読み込みに実DBが必要）。
- 前提: §4 の簡略化（#39 / #40 / #41）の解消。

## Phase 9: DataSource の統合テスト

- 対象: `infrastructure/persistence/datasource/` の `AlbumDataSource` / `ArticleDataSource` / `AlbumArticleDataSource` / `TuneDataSource`。
- 種別: 統合テスト（実DB）。
- 前提: Read Model 用 DataSource の構築。

## Phase 10: REST API の統合テスト

- 対象: 集約向けの Command / Query Resource（`presentation/rest/` に新設予定）。現状の `com/abservice/` 直下の `GreetingResource` / `HealthResource` / `CircleMemberResource` はサンプル。
- 種別: 統合テスト（REST Assured・E2E）。
- 前提: プレゼンテーション層の実装。

---

## 実装ガイドライン

### ユニットテスト（`src/test/java/`）

- データベース・外部システム接続は不要。`@QuarkusTest` は使用しない。
- 対象: Value Object / Domain Entity / Domain Service（ロジック部分）/ Aggregate / Application Service（モック使用）。
- 実行: `./gradlew test`

### 統合テスト（`src/integrationTest/java/`）

- データベースが必要。`@QuarkusTest` 必須（必要に応じて `@TestTransaction`）。
- 対象: Repository 実装 / Mapper / DataSource / REST エンドポイント。
- 前提: リポジトリルートで `docker compose up -d` → `backend/` で `./gradlew flywayMigrate`。
- 実行: `./gradlew integrationTest`

### 全テスト（CI）

- `./gradlew check`

詳細な規約は `TEST_GUIDE.md` を参照。
