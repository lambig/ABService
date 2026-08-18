# テスト追加計画（Phase 6–10、全フェーズ完了）

> **進捗の正は [docs/STATUS_AND_ROADMAP.md](../docs/STATUS_AND_ROADMAP.md)。**
> ユニットテスト（VO / Enum / 集約 / エンティティ）、Article/Tune/Album/AlbumArticle 4集約の Create/Get/Update/Delete/List 分の Phase 6–10（Application Service / Repository / Mapper / DataSource / REST API の各統合テスト）は全て整備済み。本書は背景・詳細手順のリファレンスとして残す。対象クラスの実体は git 履歴を参照。

## Phase 8: Mapper の統合テスト

- 対象: `infrastructure/persistence/mapper/` の `AlbumMapper` / `ArticleMapper` / `TuneMapper` / `AlbumArticleMapper`（ドメインモデル ⇔ エンティティ変換）。
- 種別: 統合テスト（関連エンティティの読み込みに実DBが必要）。
- 状態: `ArticleMapper` / `AlbumArticleMapper` は `ArticleRepositoryImplTest` / `AlbumArticleRepositoryImplTest`（#39/#40/#41のラウンドトリップ）で担保済み。`AlbumMapper` / `TuneMapper` は専用の `AlbumMapperTest` / `TuneMapperTest` で担保済み。

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
