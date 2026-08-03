# テスト追加計画（残: Phase 7–9 の統合テスト分、各集約の残ユースケース分）

> **進捗の正は [docs/STATUS_AND_ROADMAP.md](../docs/STATUS_AND_ROADMAP.md) §4.1。**
> ユニットテスト（VO / Enum / 集約 / エンティティ）と、Article/Tune/Album/AlbumArticle 4集約の Create/Get 分の Phase 6 / Phase 10 は整備済み。本書は残る統合テスト系フェーズと、各集約の残ユースケース（更新/削除・一覧）分を計画として扱う。完了済みフェーズの対象クラスは §4.1 と git 履歴を参照。

各フェーズは依存する実装が満たされた時点で着手する。

## Phase 6: Application Service のテスト

- 対象: 各集約の具象ユースケース（`CommandService` / `QueryService` 実装）。Article/Tune/Album/AlbumArticle 各集約の Create（`Create*Service`）/ Get（`Get*Service`）は整備済み。各集約の残ユースケース（更新/削除・一覧）は実装時に追加。
- 種別: ユニットテスト（`src/test/java/`）。リポジトリを Fake / モック化してロジックを検証。

## Phase 7: Repository 実装の統合テスト 🔴

- 対象: `infrastructure/persistence/repository/` の `AlbumRepositoryImpl` / `ArticleRepositoryImpl` / `TuneRepositoryImpl` / `AlbumArticleRepositoryImpl`。
- 種別: 統合テスト（`src/integrationTest/java/`・`@QuarkusTest`・実DB）。
- 状態: `AlbumRepositoryImpl` は統合テスト済み。残り3集約への追加は #45。

## Phase 8: Mapper の統合テスト

- 対象: `infrastructure/persistence/mapper/` の `AlbumMapper` / `ArticleMapper` / `TuneMapper` / `AlbumArticleMapper`（ドメインモデル ⇔ エンティティ変換）。
- 種別: 統合テスト（関連エンティティの読み込みに実DBが必要）。
- 状態: `ArticleMapper` / `AlbumArticleMapper` は `ArticleRepositoryImplTest` / `AlbumArticleRepositoryImplTest`（#39/#40/#41のラウンドトリップ）で担保済み。`AlbumMapper` / `TuneMapper` は未着手。

## Phase 9: DataSource の統合テスト

- 対象: `infrastructure/persistence/datasource/` の `AlbumDataSource` / `ArticleDataSource` / `AlbumArticleDataSource` / `TuneDataSource`。
- 種別: 統合テスト（実DB）。
- 状態: Read Model 用の単純な `findByDomainId`（JOINなし・自集約の直接カラムのみ）は4集約とも整備済み（各 `Get*Service` から利用）。DataSource 自体を対象とした統合テストは未着手。

## Phase 10: REST API の統合テスト

- 対象: 集約向けの Command / Query Resource（`presentation/rest/`）。Article/Tune/Album/AlbumArticle 各集約の Create/Get REST（`*CommandResource` / `*QueryResource`）の E2E は整備済み。各集約の残ユースケース（更新/削除・一覧）向け Resource は実装時に追加。`com/abservice/` 直下の `GreetingResource` / `HealthResource` はサンプル。
- 種別: 統合テスト（REST Assured・E2E）。

---

## 実装ガイドライン

### ユニットテスト（`src/test/java/`）

- データベース・外部システム接続は不要。`@QuarkusTest` は使用しない。
- 対象: Value Object / Domain Entity / Domain Service（ロジック部分）/ Aggregate / Application Service（モック使用）。
- 実行: `./gradlew test`

### 統合テスト（`src/integrationTest/java/`）

- データベースが必要。`@QuarkusTest` 必須（必要に応じて `@TestTransaction`）。
- 対象: Repository 実装 / Mapper / DataSource / REST エンドポイント。
- 前提: リポジトリルートで `docker compose up -d`。マイグレーションは Quarkus の migrate-at-start により `integrationTest` 実行時に自動適用される（別タスクの実行は不要）。
- 実行: `./gradlew integrationTest`

### 全テスト（CI）

- `./gradlew check`

詳細な規約は `TEST_GUIDE.md` を参照。
