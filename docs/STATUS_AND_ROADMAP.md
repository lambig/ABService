# 開発状況と再開ロードマップ

> **このドキュメントの位置づけ**
> ABService の**実コードと突き合わせて検証した現状**と、優先度付きの再開計画を示すマスタードキュメントです。進捗の正はこのドキュメントを参照してください。
> 個別の設計doc（`VO_REFACTORING.md` / `UNIT_TEST_PLAN.md` 等）は背景・詳細手順のリファレンスとして残しています。

---

## 1. 現状サマリ（レイヤー別・検証済み）

ビルドは現時点でも成功します（`./gradlew -p backend compileJava` = exit 0）。

| レイヤー | 状態 | 補足 |
|---|---|---|
| **ドメイン層** | 🟢 ほぼ完成 | 集約 `Album` / `AlbumArticle` / `Article` / `Tune`、VO 約20、`EventMatchingService`。検証は `Policy` へ移行済み。ビジネスロジックのユニットテスト充実 |
| **インフラ層** | 🟢 完成 | JPAエンティティ・Mapper・RepositoryImpl（4集約）、Flyway、Reactive Panache。§4 の簡略化3件は解消済み |
| **アプリケーション層** | 🟡 Article のみ縦通し済み | `CommandService` / `QueryService` 基底に加え、Article 集約の Command（`CreateArticleService`）/ Query（`GetArticleService` + `ArticleView`）を実装。**Tune / Album / AlbumArticle は未着手** |
| **プレゼンテーション層** | 🟡 Article のみ縦通し済み | Article REST（`ArticleCommandResource` / `ArticleQueryResource` + Request/Response DTO）、RFC9457 `ProblemDetail` + `DomainExceptionMapper` を実装。サンプル `GreetingResource` / `HealthResource` / `CircleMemberResource` は残置。**他集約の Resource は未着手** |
| **共通基盤（lib）** | 🟢 完成 | `Result`（combinator `map`/`flatMap`/`zip` 含む）/ `ErrorResult` を実装。ドメイン例外階層（`DomainException` 抽象基底 + `ValidationException`/`EntityNotFoundException`/`BusinessRuleViolationException`）も整備済み |
| **テスト** | 🟡 ユニット充実・統合は選択的 | VO/集約/エンティティのユニット、Article のアプリ層/例外マッパーのユニット、Article REST の E2E 統合テスト、`AlbumRepositoryImplTest` / `AlbumArticleRepositoryImplTest` / `ArticleRepositoryImplTest`。残る統合テストは §5.2 |
| **静的解析** | 🟢 完了 | Checkstyle + Spotless + PMD + ArchUnit + NullAway で多層強制（レイヤ依存方向・配置・戻り値契約・機能的スタイル・コンパイル時 null 安全）。強制設計・対象ルールは §7。SpotBugs / PMD 組込ルールセットの再導入のみフェーズD で検討（§6 フェーズD） |
| **フロントエンド** | ⬜ 未調査 | `frontend-admin`（Svelte）/ `frontend-public`（Svelte+Astro）。本ドキュメントの対象外 |

**一言でいうと**: **Article 集約で domain→app→REST→統合テストの縦1本が通り、パターンが確立済み**。次に積むべきは **同パターンの Tune / Album / AlbumArticle への横展開** と、残る統合テスト（§5.2）です。

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

現行パッケージ（`com.abservice.domain / application / infrastructure / presentation`）はこの構成に沿っています。`application` / `presentation` は **Article 集約分のみ実装済み**で、残り3集約への横展開が未整備です。

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
| リソース未存在 | empty `Uni` → `.onItem().ifNull().failWith { EntityNotFoundException }` | Article Query（`GetArticleService`）で確立済み。他集約は横展開時に踏襲 | 横展開時に踏襲 |
| ビジネスルール違反 | `DomainException` 階層（`ValidationException` / `EntityNotFoundException` / `BusinessRuleViolationException` + 具象） | `DomainException`（abstract・`errorCode`付き）+ 3サブクラスを整備済み（下記）。HTTP変換は presentation 層に委譲 | 対応不要 |
| HTTP変換 | `@Provider ExceptionMapper<DomainException>` で 400/404/409/5xx に変換 | RFC9457 `ProblemDetail` + `DomainExceptionMapper` を実装済み（`presentation/rest/exception/`） | 対応不要 |

**整備すべき例外階層:**
```
DomainException (abstract, errorCode付き)
├── ValidationException(List<ErrorResult>)   → 400
├── EntityNotFoundException                   → 404
└── BusinessRuleViolationException            → 409
```

---

## 4. インフラ層の簡略化 — 🟢 解消済み

Mapper で暫定的に空/`null` を返していた3件（#40 AlbumArticle 頒布情報 / #41 AlbumArticle 入手経路 / #39 Article タグ）はいずれも解消済み。`AlbumArticleDataSource` / `ArticleDataSource` の読み取りクエリを eager fetch 化し、`AlbumArticleRepositoryImpl` / `ArticleRepositoryImpl` の save で Entity⇄Domain 双方向の反映（頒布情報・入手経路は `AlbumEntity` の `cascade = ALL` 経由、タグは共有語彙 `ArticleTagEntity` の引当/新規作成 + `ArticleTagLinkEntity` の反映）を実装。ラウンドトリップ統合テストは `AlbumArticleRepositoryImplTest` / `ArticleRepositoryImplTest`。

---

## 5. 残タスク棚卸し（全体）

### 5.1 JSpecify nullability 移行（NullAway で enforce）— 🟢 完了

`@NullMarked`（package-info）＋ `@Nullable` を NullAway でコンパイル時強制。`main` 全体（`..persistence.entity..` は Hibernate populate 体のため対象外）を ERROR で強制済み（違反0）。設計・除外方針は #44、強制設定は `backend/build.gradle`。

- **バージョン固定（管理下の一時的負債・要追随）**: `error_prone_core 2.39.0` + `nullaway 0.12.7`（`net.ltgt.errorprone 5.1.0`）。ErrorProne 内部 API 密結合のため両者を揃える（最新 `error_prone_core 2.50.0` は非互換）。**昇格トリガ**: NullAway が 2.50 系対応版を出したら両者 bump。**退避路**: JSpecify アノテーションはツール非依存のため Checker Framework へ差し替え可能。

### 5.2 ユニット/統合テスト（`UNIT_TEST_PLAN.md`）

- 🟢 完了: Phase 1–5（VO / Enum / 集約 / エンティティ のユニットテスト）、Phase 6（Article のアプリ層/例外マッパーのユニットテスト）、Phase 10（Article REST の E2E 統合テスト）、Phase 8: Mapper 統合テスト（§4 の #39/#40/#41 解消分。`AlbumArticleRepositoryImplTest` / `ArticleRepositoryImplTest` のラウンドトリップテストとして実装）
- 🔴 未着手（横展開）:
  - Phase 6/10 の Tune / Album / AlbumArticle 分（横展開時に追加）
  - Phase 7: RepositoryImpl 統合テスト（`AlbumRepositoryImpl` 済み。`AlbumArticleRepositoryImpl` / `ArticleRepositoryImpl` は #39/#40/#41 観点のみ部分実装・全CRUD網羅は未着手。残り `TuneRepositoryImpl` は未着手） → #45
  - Phase 9: DataSource 統合テスト（Read Model 用 DataSource 構築後）

### 5.3 アプリケーション層 / プレゼンテーション層

- 🟢 Article 集約: Command（作成）/ Query（詳細）ユースケース、REST Resource、`DomainExceptionMapper`（RFC9457）を実装済み。**縦1本通しのパターン確立済み**
- 🔴 Tune / Album / AlbumArticle 集約への横展開（Command/Query ユースケース + Input/Output/Result DTO、REST Resource、Request/Response DTO）
- 🔴 Article 集約の残ユースケース（更新/削除、一覧 Query）

---

## 6. 推奨再開ロードマップ（優先度順）

> 方針: 「動く縦の1本」を最優先で通し、そのうえで横展開・品質ゲートを固める。Article 集約（依存が少なく単純）で domain→app→REST→統合テストの縦通しを終えてパターンを確立済み。**次はこのパターンの横展開**。

### フェーズ A: エラー設計とlib・静的解析の土台（縦通しの前提）— 🟢 完了

静的解析ガバナンス（ArchUnit・機能的スタイル強制ハーネス・NullAway。§7 / #37 / #44）、`Result` の合成 combinator（`map`/`flatMap`/`zip`）、`DomainException` 階層（§3）を整備済み。VO の外部入力用 `fromInput()`（`Result`返却）は Article集約のVO（`ArticleType`/`MarkupContent`）で導入済み、残VOはフェーズC で横展開。

### フェーズ B: 縦の1本通し（Article集約でパターン確立）— 🟢 完了

Article 集約で Command（記事作成）/ Query（記事詳細）ユースケース、`ArticleCommandResource` / `ArticleQueryResource` + Request/Response DTO、RFC9457 `ProblemDetail` + `DomainExceptionMapper`、REST の E2E 統合テストを実装済み。以降の横展開はこのパターンを踏襲する。

### フェーズ C: 横展開（現在地）
1. B のパターンを Tune / Album / AlbumArticle に展開（+ 残VOの `fromInput()` 横展開）
2. RepositoryImpl 統合テスト（Phase 7）を残り集約に追加（#45）

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

- 個別計画・設計判断（背景・詳細手順）: `UNIT_TEST_PLAN.md`（`backend/`）/ `VO_REFACTORING.md`。§4 の簡略化3件は issue #39/#40/#41、JSpecify 移行は #44 が正
- 設計: `ARCHITECTURE.md` / `DOMAIN_MODEL_DESIGN.md` / `DATABASE_DESIGN.md` / `ID_DESIGN_POLICY.md` / `AUDIT_COLUMNS.md`
- 規約: `CODING_GUIDELINES.md` / `RESULT_TYPE_GUIDE.md` / `REPOSITORY_IMPLEMENTATION.md`
