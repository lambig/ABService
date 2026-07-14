# 開発状況と再開ロードマップ

> **このドキュメントの位置づけ**
> 開発が一時停止していた ABService を再開するにあたり、散在していた計画ドキュメント（`JSPECIFY_MIGRATION_PLAN.md` / `VO_REFACTORING.md` / `REPOSITORY_SIMPLIFICATIONS.md` / `UNIT_TEST_PLAN.md` 等）の内容を、**実コードと突き合わせて検証した現状**として1本に集約し、優先度付きの再開計画を示すマスタードキュメントです。
> 個別の計画docは背景・詳細手順のリファレンスとして残していますが、進捗の正はこのドキュメントを参照してください。
>
> 最終更新: 2026-07-08 / 検証時コミット: `47a1aaf`

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
| **静的解析** | 🟢 多層 | Checkstyle + Spotless + PMD + ArchUnit 稼働。レイヤ依存方向・配置・戻り値契約・機能的スタイルを強制（§7.3: Java 相当21件を全件強制）。SpotBugs は 4.10.2 で Java25 対応済みだが未導入（PMD 組込ルールセットと共に再導入はフェーズD で検討＝§6-17） |
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

### 5.1 JSpecify nullability 移行（`JSPECIFY_MIGRATION_PLAN.md`）

import 済み 11 件から進んでおらず残多数（集約 `AlbumArticle`・集約内エンティティ・VO 大半・infra 層が未対応）。詳細と進め方は #44。

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

### フェーズ A: エラー設計とlib・静的解析の土台（縦通しの前提）
1. **ArchUnit 導入 ＋ 規約ベースのルールを先行**（§7.1）: `archunit-junit5` を test依存に追加し、レイヤー依存方向（domain ← application ← presentation、domain ← infrastructure）・`@Entity` の配置/命名・`@Transactional` 禁止・Repository/ApplicationService の `Uni` 返却契約に加え、**app/presentation の命名・配置・戻り値型契約も前提として先行導入する**。これらは §2 で確定済みの規約と既存の基底型（`CommandService`/`QueryService`）だけで述語を書けるため、実装クラスが0件でも導入でき、クラスが無い間は不活性（該当ルールのみ `allowEmptyShould(true)`）、最初のユースケース実装と同時に強制が効く。**「機能実装を待ってからルール化」はしない**（原則は §7.1）
2. **機能的スタイル強制ルールを先行導入**（§7.4 / #37）: 構造非依存で既存 domain/lib と新規コード双方に効くため前提として入れる。各ルールは「導入前に違反0」を受け入れ条件とする。
   - **導入済み（#26–#33・#35 完了）**: `if` 文全廃（#26）・null三項→`Optional`（#27）・逐次 null+空/blank 複合の禁止（#28）・VO 検証の Policy 経由（#29）・`switch (this)` 禁止（#30）・否定ラムダの `Predicate.not` 化（#31）・中置論理演算子禁止＋述語合成DSL（#32）・不要変数禁止（#33）・三項の書式（条件を行頭）確定（#35）。各ルールは実違反スニペットで検出を確認済み（§7.3）
   - **単独作業（ルールでない）**: `multiple` 検証支援API（#34 完了）・Javadoc 例更新（#36）
3. 🟢 完了: `Result` に `map` / `flatMap` / `zip`（複数VO検証の合成。arity 2・3でエラー集約）を追加
4. 🟢 完了: `DomainException` 階層を整備（abstract 基底 + `ValidationException` / `EntityNotFoundException` / `BusinessRuleViolationException`。HTTP変換は presentation 層へ委譲）
5. 🟡 一部: VO に外部入力用 `fromInput()`（`Result`返却）を段階導入。Article集約のVO（`ArticleType`/`MarkupContent`）は導入済み、残VOはフェーズCで横展開

### フェーズ B: 縦の1本通し（Article集約でパターン確立）
6. Command ユースケース1件（例: 記事作成）を `CommandService` で実装 + Input/Output DTO
7. Query ユースケース1件（例: 記事一覧/詳細）を `QueryService` + `DataSource` + Read Model DTO で実装
8. `presentation/rest/` に `ArticleCommandResource` / `ArticleQueryResource` + Request/Response DTO
9. `ExceptionMapper`（DomainException → 400/404/409/5xx）
10. REST 統合テスト（rest-assured）でE2E疎通確認 → **動作確認スキル/実機起動で観測**

### フェーズ C: 横展開
11. B のパターンを Tune / Album / AlbumArticle に展開
12. §4 の簡略化3件を解消（AlbumArticle 頒布情報・入手経路 → Article タグ）し、Mapper統合テスト（Phase 8）を追加
13. RepositoryImpl 統合テスト（Phase 7）を残り3集約に追加

### フェーズ D: 品質ゲート
14. **ArchUnit 残ルールの点検**（§7.2）: 命名・配置・戻り値型契約はフェーズAで先行導入済み（§7.1 の原則により規約ベースで先行できるもの）。フェーズDで新たに追加するのは、§2 の規約だけでは述語を書けず**具体実装がないと表現できない**真に構造依存なルールに限る（原則として想定なし。必要が生じた時点で判断）。テスト規約のうち `@DisplayName` 必須は `TestConventionsArchTest`、AssertJ 統一は Checkstyle で強制済み
15. JSpecify 移行の続行（§5.1、集約→エンティティ→VO→infra の順）
16. DataSource 統合テスト（Phase 9）、カバレッジ計測（JaCoCo導入検討）
17. **SpotBugs / PMD 組込ルールセットの再導入検討**: SpotBugs 4.10.2（Gradle plugin 6.5.8）は Java25 対応済み。errorprone/バグパターン系を SpotBugs で、errorprone/collection/security 系を PMD 組込ルールセットで補う。本プロジェクト固有の規約26ルール（§7.3）とは別系統で、導入時に新規顕在化する違反の段階是正・除外スコープ設計が要る。品質ゲート＝ポリシー変更のため都度承認のうえ実施

---

## 7. ArchUnit 導入計画（アーキテクチャ制約の強制）

ABService のアーキテクチャ制約（レイヤー依存方向・配置・ライブラリ依存・戻り値契約など）を ArchUnit のテストで強制します。CI で違反を検出し、新規コードを最初から制約に沿わせます。

### 7.1 導入と基本ルール（フェーズAで先行）

```gradle
// build.gradle（test依存）
testImplementation 'com.tngtech.archunit:archunit-junit5:1.4.2'
```
アーキテクチャテストは `src/test/java/com/abservice/architecture/` に JUnit5 テストとして配置（DB不要なので unit 側でよい）。

**原則: 規約ベースのルールは実装を待たず先行導入する。** ArchUnit の述語が §2 で確定した規約（パッケージ配置・命名・基底型実装）と既存の基底型だけで表現できるルールは、対象クラスが未実装でも先行導入する。対象0件の間はマッチせず不活性になる（その1ルールのみ `allowEmptyShould(true)` を付け、実装が入り次第 `false` へ戻す）。これにより最初の実装が書かれた瞬間から契約が強制され、新規コードは最初から準拠する。逆に「機能実装を待ってからルール化」すると初回実装が非準拠で書かれ後追い是正になるため採らない。

**フェーズAで先行して入れる基本ルール**（既存構造だけで検証できるもの）:
- レイヤー依存方向: `layeredArchitecture()` で domain ← application ← presentation、domain ← infrastructure
- `@Entity` は `..infrastructure.persistence.entity..` 内のみ
- `..domain..` から `java.time..` への依存禁止（`BusinessDate`/`BusinessDateTime` は除外）
- `@Transactional` 禁止（Reactive は `@WithTransaction`）
- `Repository` 継承IFのメソッド戻り値は `Uni<...>`

**同じくフェーズAで先行して入れる契約ルール**（§2 の規約＋既存基底型だけで述語を書けるもの。上記原則により実装を待たない）:
- ApplicationService の命名/配置: `application/service/<agg>/` の Command 実装・`application/query/` の Query 実装は `*Service` 命名で該当パッケージに置く
- ApplicationService の戻り値型: `execute`/`query` は `Uni<...>` 返却（基底 `CommandService`/`QueryService` の署名で既に javac が強制。ArchUnit は命名/配置/基底IF実装の逸脱を補完する）

テスト規約のうち `@DisplayName` 必須は `TestConventionsArchTest` で強制済み（§7.2）。フェーズDに残すのは、§2 の規約だけでは述語を書けず具体実装がないと表現できない真に構造依存なルールに限る（§6 フェーズD）。

### 7.2 ArchUnit で強制する制約（全体）

下表のうち §7.1 に挙げた基本ルールはフェーズAで先行導入済み。テスト規約の `@DisplayName` 必須は `TestConventionsArchTest`（テストクラスを検査対象に含めるため `LayeredArchitectureTest` とは別クラス）で強制済み。命名・配置・戻り値型契約（「Entity 命名」「ApplicationService の戻り値」等）は §7.1 の原則により**規約ベースで前提（フェーズA）に先行導入する**（実装0件の間は `allowEmptyShould` で不活性）。フェーズD に残すのは具体実装がないと述語を書けない真に構造依存なルールのみ。なお「JUnit assertion 禁止」は ArchUnit ではなく Checkstyle で強制している。

| ルール | 内容 |
|---|---|
| @Entity の配置 | `@Entity` 付与クラスは `..infrastructure.persistence.entity..` 内のみ |
| domain の java.time 禁止 | `..domain..` から `java.time..` への依存禁止（`BusinessDate`/`BusinessDateTime` は除外） |
| Provider を持たない | ドメインモデルは `BusinessDateTimeProvider` 型フィールドを持たない |
| println 禁止 | `System.out`/`System.err` アクセスをロギングパッケージ以外で禁止 |
| Entity 命名 | `@Entity` 付与クラス名は `*Entity` サフィックス |
| @Transactional 禁止 | Reactive は `@WithTransaction` を使う |
| JUnit assertion 禁止 | テストは AssertJ に統一 |
| @DisplayName 必須 | `@Test` / `@ParameterizedTest` メソッドは `@DisplayName` を付与（`TestConventionsArchTest` で強制） |
| Repository の戻り値 | `Repository` 継承IFのメソッド戻り値は `Uni<...>` |
| ApplicationService の戻り値 | `execute`/`query` の戻り値は `Uni<...>` |
| domain の Uni 禁止 | ドメインモデルの戻り値に `Uni` を使わない（同期実装） |
| コンストラクタ可視性 | `domain.model`（非record）のコンストラクタは非 public（record は言語制約上不可のため、コンパクトコンストラクタ＋ファクトリで担保） |
| EntityId は record | `EntityId` 実装は record（値型・不変） |
| domain フィールド final | `..domain.model..` のフィールドは `final`（不変。状態変更は Wither で表現） |
| レイヤー依存方向 | domain ← application ← presentation、domain ← infrastructure |

メソッド本文やコメントの検査が必要な制約（try-catch 禁止、VO のバリデーション必須など）は ArchUnit では表現できないため対象外。必要なら Checkstyle/PMD で個別対応する。

### 7.3 detekt カスタムルール26件との対応（Java 担保手段）

ABService は Java プロジェクトのため、Kotlin 向けの detekt カスタムルール26件はそのまま使えない。同等の制約を **ArchUnit / Checkstyle / javac・言語文法**で担保するか、Java に構文的対応物がないものは対象外とする。移植可否は「**強制手段の構文**」ではなく「**制約の意図が Java に適用可能か**」で判定する。

現状: **強制済み21件（ArchUnit 11・Checkstyle 6・PMD 3・ArchUnit+PMD 併用 1）／ 部分0 ／ 対象外5件 ／ 未強制0**。Java 相当のある21件を全件（100%）強制。`ForbiddenVarInDomain` は Checkstyle と ArchUnit の併用、`ForbiddenInternalConstructorInDomainModel` は ArchUnit（非 record の可視性）と PMD（record の検証必須）の併用（各1件計上）。全ルールは実違反スニペットを一時投入して検出を確認済み（no-op でないこと・実効性を検証済み）。

| detekt ルール | 制約の意図 | Java での担保手段 | 状態 |
|---|---|---|---|
| ForbiddenJpaEntityOutsidePersistenceLayer | `@Entity` は永続化層のみ | ArchUnit | ✅強制 |
| RequireRecordSuffixForJpaEntity | JPA エンティティは `*Entity` 命名 | ArchUnit | ✅強制 |
| ForbiddenJavaTimeInDomain | domain の `java.time` 直接依存禁止 | ArchUnit | ✅強制 |
| ForbiddenTransactionalAnnotation | `@Transactional` 禁止（Reactive は `@WithTransaction`） | ArchUnit | ✅強制 |
| RequireUniReturnTypeOnRepository | Repository の戻り値は `Uni<...>` | ArchUnit | ✅強制 |
| ForbiddenUniInDomainModel | domain モデルの戻り値に `Uni` を使わない | ArchUnit | ✅強制 |
| ForbiddenProviderInDomainModel | domain モデルは `BusinessDateTimeProvider` を保持しない | ArchUnit | ✅強制 |
| ForbiddenPrintlnOutsideLogging | `System.out`/`System.err` をロギング以外で禁止 | ArchUnit | ✅強制 |
| RequireDisplayNameOnTestMethods | `@Test`/`@ParameterizedTest` に `@DisplayName` 必須 | ArchUnit（`TestConventionsArchTest`） | ✅強制 |
| ForbiddenInternalConstructorInDomainModel | domain モデルの生成を検証経由に限定（可視性の絞り） | ArchUnit `domainModelConstructorsShouldNotBePublic`（非 record は public ctor 禁止）＋ PMD `RequireValidationInDomainValueRecordConstructor`（record=`ValueObject`/`EntityId` 実装はコンパクトコンストラクタでの検証必須）。両ルールの和集合が domain モデルの値/エンティティ型全体を機械的に被覆 | ✅強制 |
| ForbiddenTryCatchInDomain | domain で try/catch 禁止 | Checkstyle（`DomainNoTryCatch`） | ✅強制 |
| RequireSuppressJustification | 抑制に理由必須 | Checkstyle（`@SuppressWarnings` 理由コメント必須） | ✅強制 |
| ForbiddenJUnitAssertions | テストは AssertJ に統一 | Checkstyle（JUnit `Assertions` import 禁止） | ✅強制 |
| ForbiddenMutableCollectionFactory | domain で可変コレクション生成禁止 | Checkstyle（`DomainNoMutableCollection`） | ✅強制 |
| ForbiddenNotNullAssertionInDomain | `!!`（not-null assertion）禁止 | Java に該当演算子なし。意図は JSpecify 移行（§5.1）へ | ⛔対象外 |
| ForbiddenBacktickTestMethodName | バッククォートのテストメソッド名禁止 | Java はメソッド名に空白不可。可読名は `@DisplayName` で充足 | ⛔対象外 |
| ProhibitWhenForUnitBranches | 副作用分岐に `when` を使わず `if` を使う | Java は `if`=文で自動充足（副作用は `if`/`switch` 文） | ⛔対象外 |
| ForbiddenInitInValueObject | VO で `init` ブロック禁止（検証を集約） | record に `init` なし。意図は VO 検証集約（下記 RequireValidationInValueObject）へ | ⛔対象外 |
| RequireUniReturnTypeOnApplicationService | ApplicationService の `execute`/`query` は `Uni<...>` | 基底 `CommandService`/`QueryService` の署名で javac が既に強制。ArchUnit `applicationServiceExecuteAndQueryShouldReturnUni` で命名/配置/基底IF実装を補完（§7.1 の原則で前倒し、実装0件の間は `allowEmptyShould(true)`） | ✅強制 |
| RequireValidationInValueObject | VO は検証を持つ | PMD `RequireValidationInValueObject`（`fromInput` に `verify`/`zip`/`combine`/`nested`/`failure` か内部 `fromInput` 委譲を必須化） | ✅強制 |
| ForbiddenLogicalOperators | 中置論理演算子 `&&`/`\|\|`/`!` を禁止し宣言的合成へ | Checkstyle `IllegalToken`（`LAND`/`LOR`/`LNOT`。production のみ、テストは suppressions で除外）＋述語合成DSL | ✅強制 |
| ForbiddenVarInDomain | domain で可変変数禁止 | Checkstyle `FinalLocalVariable`（全ローカル final）＋ ArchUnit `domainModelFieldsShouldBeFinal`（domain フィールド final） | ✅強制 |
| RequireValueClassForEntityId | EntityId は値型 | ArchUnit `entityIdImplementationsShouldBeRecords`（EntityId 実装は record） | ✅強制 |
| RequirePrivateConstructorForEntityId | EntityId の生成を検証経由に限定（可視性の絞り） | public record は canonical constructor を private 化できない（言語制約のため可視性の絞り自体は対象外）。代替統制として全 EntityId/VO record のコンパクトコンストラクタ検証を PMD `RequireValidationInDomainValueRecordConstructor` で必須化し、「未検証インスタンスの生成不可」を機械的に担保（意図は #10 として強制済み） | ⛔対象外 |
| ForbiddenFullyQualifiedTypeReference | インライン FQN 禁止（import + 単純名） | PMD `ForbiddenFullyQualifiedTypeReference`（XPath `ClassType[@FullyQualified]`） | ✅強制 |
| PreferWhenForValueBranches | 値の分岐を `if` で返さず式にする | PMD `ForbiddenIfValueReturn`＋`ForbiddenSwitchStatement`（`if` の値 return 禁止＋`switch` 文禁止 → ternary / switch 式へ） | ✅強制 |

**`if`/式の運用方針**（PreferWhenForValueBranches の Java 仕様）:

- 値の生成は**式のみ**（ternary `?:` または switch 式）。`if` 文での値 return と `switch` 文は禁止する。
- `if` が許されるのは **副作用への分岐**と**例外（`throw`）への分岐**のみ。`if (c) throw ...;`・`if (c) { sideEffect(); }`・値を伴わない `return;` は許容し、`if (c) return <値>;` は禁止する。
- sealed 型に switch 式を用いれば、網羅性は **javac がコンパイル時に担保**する（lint 不要）。例外分岐の `if` を許す方針は §3 のエラー設計（ビジネス違反=`DomainException` 階層への分岐）と整合する。

### 7.4 機能的スタイル強制ハーネスの状態

命令的な `if`・逐次検査・生ラムダを排し、**式・Optional・filter・Policy・多態**で表現する機能的スタイルを全面採用する。本文適用・ルール化（静的解析での強制）はいずれも完了済み（src/main の実 `if` は 0、`#26`–`#33`・`#35` のルールは全て導入し、各ルールを実違反スニペットで検出確認済み）。支援 API `multiple`（`#34`）も提供済み。残るは Javadoc コード例の更新（`#36`）のみ。

- トラッキング: **#37**（`harness-backlog` ラベル）
- 各ルールは導入時に「導入前に違反を検出できること」「フォーマッタ/PMD 整合（違反 0）」を確認済み。詳細・検出方針は個別 issue（#26–#36）を参照。

---

## 8. 参照ドキュメント

- 個別計画（背景・詳細手順）: `JSPECIFY_MIGRATION_PLAN.md` / `REPOSITORY_SIMPLIFICATIONS.md` / `UNIT_TEST_PLAN.md`（`backend/`）/ `VO_REFACTORING.md`
- 設計: `ARCHITECTURE.md` / `DOMAIN_MODEL_DESIGN.md` / `DATABASE_DESIGN.md` / `ID_DESIGN_POLICY.md` / `AUDIT_COLUMNS.md`
- 規約: `CODING_GUIDELINES.md` / `RESULT_TYPE_GUIDE.md` / `REPOSITORY_IMPLEMENTATION.md`
</content>
</invoke>
