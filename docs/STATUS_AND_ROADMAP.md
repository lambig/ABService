# 開発状況と再開ロードマップ

> **このドキュメントの位置づけ**
> 開発が一時停止していた ABService を再開するにあたり、散在していた計画ドキュメント（`JSPECIFY_MIGRATION_PLAN.md` / `VO_REFACTORING.md` / `REPOSITORY_SIMPLIFICATIONS.md` / `UNIT_TEST_PLAN.md` 等）の内容を、**実コードと突き合わせて検証した現状**として1本に集約し、優先度付きの再開計画を示すマスタードキュメントです。
> 個別の計画docは背景・詳細手順のリファレンスとして残していますが、進捗の正はこのドキュメントを参照してください。

---

## 1. 現状サマリ（レイヤー別・検証済み）

ビルドは現時点でも成功します（`./gradlew -p backend compileJava` = exit 0）。

| レイヤー | 状態 | 補足 |
|---|---|---|
| **ドメイン層** | 🟢 ほぼ完成 | 集約 `Album` / `AlbumArticle` / `Article` / `Tune`、VO 約20、`EventMatchingService`。ビジネスロジックのユニットテスト充実 |
| **インフラ層** | 🟢 完成（一部簡略化残） | JPAエンティティ・Mapper・RepositoryImpl（4集約）、Flyway V1〜V24、Reactive Panache。§4 の簡略化3件が未解消 |
| **アプリケーション層** | 🟡 基底のみ | `CommandService` / `QueryService` インターフェース（使用例つき）は完備。**具象ユースケースは0件** |
| **プレゼンテーション層** | 🔴 未着手 | サンプル `GreetingResource` / `HealthResource` / `CircleMemberResource` のみ。集約向けRESTエンドポイント・DTO・ExceptionMapperなし |
| **共通基盤（lib）** | 🟢 完成 | `Result`（combinator `map`/`flatMap`/`zip` 含む）/ `ErrorResult` を実装。ドメイン例外階層（`DomainException` 抽象基底 + `ValidationException`/`EntityNotFoundException`/`BusinessRuleViolationException`）も整備済み |
| **テスト** | 🟡 ユニット充実・統合が薄い | ユニット31クラス（VO/集約/エンティティ）。統合テストは `AlbumRepositoryImplTest` と `SystemBusinessDateTimeProviderTest` の2本のみ |
| **静的解析** | 🟢 完了 | Checkstyle + Spotless + PMD + ArchUnit + NullAway で多層強制（レイヤ依存方向・配置・戻り値契約・機能的スタイル・コンパイル時 null 安全）。強制設計・対象ルールは §7。SpotBugs / PMD 組込ルールセットの再導入のみフェーズD で検討（§6 フェーズD） |
| **フロントエンド** | ⬜ 未調査 | `frontend-admin`（Svelte）/ `frontend-public`（Svelte+Astro）。本ドキュメントの対象外 |

**一言でいうと**: ドメイン＋永続化基盤は固まっており、次に積むべきは **アプリケーション層（ユースケース）→ プレゼンテーション層（REST）** の縦の1本通しと、それを支える **エラー設計・統合テスト・アーキテクチャ制約** です。

---

## 2. 目標アーキテクチャと設計方針

ABService は Quarkus/Reactive を土台に、DDD レイヤ構成・CQRS・段階的なエラー設計を採用します。本節は目標とするパッケージ構成と設計パターン（Java での実現方針）を示します。

### 2.1 目標パッケージ構成

```
com.abservice/
├── domain/
│   ├── model/{aggregate, entity, vo}
│   ├── repository/            interface（実装は infrastructure）
│   ├── service/               ドメインサービス interface (+ 一部Impl)
│   ├── factory/               ファクトリ interface + Impl
│   └── exception/             ドメイン例外階層
├── application/
│   ├── service/<agg>/         CommandService実装 + Input/Output DTO
│   └── query/                 QueryService実装 + Request/Result
│       ├── model/             Read Model DTO
│       └── mapper/            Row→DTO マッパー
├── infrastructure/
│   ├── persistence/{repository, entity(*Entity), mapper, datasource}
│   └── datetime/
└── presentation/rest/         ★未着手
    ├── *Resource.java         CQRSで分割（Command/Query）
    ├── request/               リクエストDTO
    ├── response/              レスポンスDTO + エラーレスポンス
    └── exception/             JAX-RS ExceptionMapper
```

現行パッケージ（`com.abservice.domain / application / infrastructure`）はこの構成にほぼ沿っています。**未整備なのは `application` の具象と `presentation`（丸ごと）**の2点です。

### 2.2 採用する設計パターン

| パターン | ABService での方針 |
|---|---|
| **CQRS の Read/Write 分離** | Command は `Repository`（Panache/Write Model）経由、Query は `DataSource` 直アクセスで Read Model DTO を返す。両者は既に分離済み |
| **Command ユースケース** | `@ApplicationScoped` な `CommandService<Input, Output>` 実装。`@WithTransaction execute(): Uni<Output>`。Input/Output は同パッケージの record |
| **Query ユースケース** | `QueryService<Request, Result>` を `application/query/` に配置。`@WithSession query(): Uni<Result>`。Read Model は `application/query/model/`。ステータス(SUCCESS/INSUFFICIENT_DATA/NOT_FOUND)で 200/422/404 を出し分け |
| **REST Resource** | `presentation/rest/` に集約ごとに Command/Query Resource を作成。`Uni<Response>` 返却、MicroProfile OpenAPI アノテーション |
| **VO の2系統生成** | 内部生成は例外throwのコンパクトコンストラクタ/`of()`、外部入力は `Result` を返す `fromInput()` の2系統。Article集約のVO（`ArticleType`/`MarkupContent`）で導入済み、他VOは順次横展開 |
| **3層のエラー表現** | 値検証=`Result`、未存在=empty `Uni`+`failWith`、ビジネス違反=`DomainException` 階層。§3 / §5 参照 |
| **テスト分割** | `unitTest`（Fake注入・DI無）/ `integrationTest`（@QuarkusTest・実DB）の2分割済み。実HTTP のテストは外部連携が出た時点で検討 |

---

## 3. エラーハンドリング設計のギャップ

「値検証 / 未存在 / ビジネス違反」を層で明確に使い分ける方針です。ABService の現状との差分:

| 種別 | 目標 | ABService 現状 | 対応 |
|---|---|---|---|
| 値検証（複数エラー収集） | `Result<T>` + `resolve/zip/map/flatMap` | `resolve`/`orElse*`/`map`/`flatMap`/`zip`（arity 2・3、エラー集約）を実装済み | 対応不要 |
| リソース未存在 | empty `Uni` → `.onItem().ifNull().failWith { EntityNotFoundException }` | Repository は実装済みだが、未存在→例外化の共通パターン未確立 | ユースケース実装時に規約化 |
| ビジネスルール違反 | `DomainException` 階層（`ValidationException` / `EntityNotFoundException` / `BusinessRuleViolationException` + 具象） | `DomainException`（abstract・`errorCode`付き）+ 3サブクラスを整備済み（下記）。HTTP変換は presentation 層に委譲 | 対応不要 |
| HTTP変換 | `@Provider ExceptionMapper<DomainException>` で 400/404/409/5xx に変換 | 未実装 | presentation層で `ExceptionMapper` を実装 |

**整備すべき例外階層:**
```
DomainException (abstract, errorCode付き)
├── ValidationException(List<ErrorResult>)   → 400
├── EntityNotFoundException                   → 404
└── BusinessRuleViolationException            → 409
```

---

## 4. インフラ層の簡略化（未解消・実コードで確認済み）

`REPOSITORY_SIMPLIFICATIONS.md` 記載の3件はいずれも未解消。issue で管理する（優先度: 頒布情報・入手経路 > タグ）。

- #40 AlbumArticle 頒布情報 / #41 AlbumArticle 入手経路 / #39 Article タグ

---

## 5. 残タスク棚卸し（全体）

### 5.1 JSpecify nullability 移行（NullAway で enforce）— 🟢 完了

`@NullMarked`（package-info）＋ `@Nullable` を NullAway でコンパイル時強制。`main` 全体（`..persistence.entity..` は Hibernate populate 体のため対象外）を ERROR で強制済み（違反0）。設計・除外方針・進め方は #44 / `JSPECIFY_MIGRATION_PLAN.md`、強制設定は `backend/build.gradle`。

- **バージョン固定（管理下の一時的負債・要追随）**: `error_prone_core 2.39.0` + `nullaway 0.12.7`（`net.ltgt.errorprone 5.1.0`）。ErrorProne 内部 API 密結合のため両者を揃える（最新 `error_prone_core 2.50.0` は非互換）。**昇格トリガ**: NullAway が 2.50 系対応版を出したら両者 bump。**退避路**: JSpecify アノテーションはツール非依存のため Checker Framework へ差し替え可能。

### 5.2 ユニット/統合テスト（`UNIT_TEST_PLAN.md`）

- 🟢 完了: Phase 1–5（VO / Enum / 集約 / エンティティ のユニットテスト、計31クラス）
- 🔴 未着手:
  - Phase 6: Application Service のテスト（※ユースケース実装後に発生）
  - Phase 7: RepositoryImpl 統合テスト → #45
  - Phase 8: Mapper 統合テスト（§4 解消に依存）
  - Phase 9: DataSource 統合テスト（DataSource 構築後）
  - Phase 10: REST API 統合テスト（※presentation実装後に発生）

### 5.3 アプリケーション層 / プレゼンテーション層（新規・最重要）

- 🔴 各集約の Command ユースケース（作成/更新/削除）と Input/Output DTO
- 🔴 各集約の Query ユースケース（一覧/詳細）と Read Model DTO
- 🔴 REST Resource（Command/Query）、Request/Response DTO
- 🔴 `ExceptionMapper`（DomainException → HTTP）

---

## 6. 推奨再開ロードマップ（優先度順）

> 方針: 「動く縦の1本」を最優先で通し、そのうえで横展開・品質ゲートを固める。まず **Article 集約**（依存が少なく単純）で 1 ユースケースを domain→app→REST→統合テストまで貫通させ、パターンを確立してから他集約へ横展開する。

### フェーズ A: エラー設計とlib・静的解析の土台（縦通しの前提）— 🟢 ほぼ完了

土台は完了済み: 静的解析ガバナンス（ArchUnit・機能的スタイル強制ハーネス・NullAway。§7 / #37 / #44）、`Result` の合成 combinator（`map`/`flatMap`/`zip`）、`DomainException` 階層（§3）。残るは VO の外部入力用 `fromInput()`（`Result`返却）の横展開のみ — Article集約のVO（`ArticleType`/`MarkupContent`）で導入済み、残VOはフェーズC で対応。

### フェーズ B: 縦の1本通し（Article集約でパターン確立）
1. Command ユースケース1件（例: 記事作成）を `CommandService` で実装 + Input/Output DTO
2. Query ユースケース1件（例: 記事一覧/詳細）を `QueryService` + `DataSource` + Read Model DTO で実装
3. `presentation/rest/` に `ArticleCommandResource` / `ArticleQueryResource` + Request/Response DTO
4. `ExceptionMapper`（DomainException → 400/404/409/5xx）
5. REST 統合テスト（rest-assured）でE2E疎通確認 → **動作確認スキル/実機起動で観測**

### フェーズ C: 横展開
6. B のパターンを Tune / Album / AlbumArticle に展開
7. §4 の簡略化3件を解消（AlbumArticle 頒布情報・入手経路 → Article タグ。#39/#40/#41）し、Mapper統合テスト（Phase 8）を追加
8. RepositoryImpl 統合テスト（Phase 7）を残り3集約に追加（#45）

### フェーズ D: 品質ゲート
9. **ArchUnit 残ルールの点検**（§7）: 命名・配置・戻り値型契約は先行導入済み。追加するのは §2 の規約だけでは述語を書けず**具体実装がないと表現できない**真に構造依存なルールに限る（原則として想定なし。必要が生じた時点で判断）
10. DataSource 統合テスト（Phase 9）、カバレッジ計測（JaCoCo導入検討）
11. **SpotBugs / PMD 組込ルールセットの再導入検討**: SpotBugs 4.10.2（Gradle plugin 6.5.8）は Java25 対応済み。バグパターン系を SpotBugs、collection/security 系を PMD 組込ルールセットで補う。本プロジェクト固有規約（§7）とは別系統で、導入時に顕在化する違反の段階是正・除外スコープ設計が要る。品質ゲート＝ポリシー変更のため都度承認のうえ実施

---

## 7. 静的解析ガバナンス（強制済み）

ABService のアーキテクチャ制約・コーディング規約は多層の静的解析で **CI 強制済み**。**正は設定・テストの実体**（本節は概要のみ）:

| 手段 | 実体 | 主な強制内容 |
|---|---|---|
| ArchUnit | `backend/src/test/java/com/abservice/architecture/`（`LayeredArchitectureTest` / `TestConventionsArchTest`） | レイヤ依存方向、`@Entity` 配置/命名、`@Transactional` 禁止、Repository/ApplicationService の `Uni` 返却契約、domain の `java.time`/`Uni`/Provider 禁止、コンストラクタ可視性、フィールド final、`@DisplayName` 必須 |
| Checkstyle | `backend/config/checkstyle/`（+ `suppressions.xml`） | domain の try-catch/可変コレクション禁止、中置論理演算子禁止、全ローカル final、JUnit assertion 禁止（AssertJ 統一）、`@SuppressWarnings` 理由必須 |
| PMD | `backend/config/pmd/ruleset.xml` | `if` 文全廃、VO/record の検証必須、FQN 禁止、`if` 値 return / `switch` 文禁止 |
| NullAway / ErrorProne | `backend/build.gradle` | `@NullMarked` パッケージのコンパイル時 null 安全（§5.1） |
| Spotless | `backend/config/spotless/eclipse-formatter.xml` | フォーマット |

detekt（Kotlin）カスタムルール26件相当は、Java に構文的対応物のある21件を全件強制・5件は対象外（`!!`・バッククォート名など Java に存在しない構文）。

**維持すべき設計方針**（今後の拡張で保つ）:
- **規約ベースのルールは実装を待たず先行導入する**。対象0件の間は `allowEmptyShould(true)` で不活性、最初の実装が入った瞬間から強制。「機能実装を待ってからルール化」はしない。
- **値の生成は式のみ**（ternary / switch 式）。`if` は副作用・例外（`throw`）分岐に限る。sealed 型 + switch 式で網羅性を javac が担保。§3 のエラー設計と整合。
- 追加ルールは §6 フェーズD 参照（真に構造依存なもの・SpotBugs/PMD 組込ルールセットの再導入）。

---

## 8. 参照ドキュメント

- 個別計画（背景・詳細手順）: `JSPECIFY_MIGRATION_PLAN.md` / `REPOSITORY_SIMPLIFICATIONS.md` / `UNIT_TEST_PLAN.md`（`backend/`）/ `VO_REFACTORING.md`
- 設計: `ARCHITECTURE.md` / `DOMAIN_MODEL_DESIGN.md` / `DATABASE_DESIGN.md` / `ID_DESIGN_POLICY.md` / `AUDIT_COLUMNS.md`
- 規約: `CODING_GUIDELINES.md` / `RESULT_TYPE_GUIDE.md` / `REPOSITORY_IMPLEMENTATION.md`
</content>
</invoke>
