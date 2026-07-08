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
| **静的解析** | 🟡 style層のみ | Checkstyle + Spotless 稼働。SpotBugs は Java25非対応で無効。**アーキテクチャ制約の強制（ArchUnit）は未導入**（§7） |
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
- ⚠️ `UNIT_TEST_PLAN.md` のパス陳腐化の是正は #42

### 5.3 アプリケーション層 / プレゼンテーション層（新規・最重要）

- 🔴 各集約の Command ユースケース（作成/更新/削除）と Input/Output DTO
- 🔴 各集約の Query ユースケース（一覧/詳細）と Read Model DTO
- 🔴 REST Resource（Command/Query）、Request/Response DTO
- 🔴 `ExceptionMapper`（DomainException → HTTP）

### 5.4 その他

- `VO_REFACTORING.md` の命名陳腐化の是正は #43

---

## 6. 推奨再開ロードマップ（優先度順）

> 方針: 「動く縦の1本」を最優先で通し、そのうえで横展開・品質ゲートを固める。まず **Article 集約**（依存が少なく単純）で 1 ユースケースを domain→app→REST→統合テストまで貫通させ、パターンを確立してから他集約へ横展開する。

### フェーズ A: エラー設計とlib・静的解析の土台（縦通しの前提）
1. **ArchUnit 導入 ＋ 基本ルール先行**（§7.1）: `archunit-junit5` を test依存に追加し、レイヤー依存方向（domain ← application ← presentation、domain ← infrastructure）・`@Entity` の配置・`@Transactional` 禁止・Repository/ApplicationService の `Uni` 返却契約など、**既存構造だけで検証できる基本ルール**を先に入れる。以降の新規コードが最初から制約に沿うようにする（詳細な命名/戻り値ルールはフェーズDに残す）
2. 🟢 完了: `Result` に `map` / `flatMap` / `zip`（複数VO検証の合成。arity 2・3でエラー集約）を追加
3. 🟢 完了: `DomainException` 階層を整備（abstract 基底 + `ValidationException` / `EntityNotFoundException` / `BusinessRuleViolationException`。HTTP変換は presentation 層へ委譲）
4. 🟡 一部: VO に外部入力用 `fromInput()`（`Result`返却）を段階導入。Article集約のVO（`ArticleType`/`MarkupContent`）は導入済み、残VOはフェーズCで横展開

### フェーズ B: 縦の1本通し（Article集約でパターン確立）
4. Command ユースケース1件（例: 記事作成）を `CommandService` で実装 + Input/Output DTO
5. Query ユースケース1件（例: 記事一覧/詳細）を `QueryService` + `DataSource` + Read Model DTO で実装
6. `presentation/rest/` に `ArticleCommandResource` / `ArticleQueryResource` + Request/Response DTO
7. `ExceptionMapper`（DomainException → 400/404/409/5xx）
8. REST 統合テスト（rest-assured）でE2E疎通確認 → **動作確認スキル/実機起動で観測**

### フェーズ C: 横展開
9. B のパターンを Tune / Album / AlbumArticle に展開
10. §4 の簡略化3件を解消（AlbumArticle 頒布情報・入手経路 → Article タグ）し、Mapper統合テスト（Phase 8）を追加
11. RepositoryImpl 統合テスト（Phase 7）を残り3集約に追加

### フェーズ D: 品質ゲート
12. **ArchUnit 詳細ルールの追加**（§7.2）: app/presentation の構造が揃ってから、命名規約・戻り値型契約（`ApplicationService` の `execute`/`query` は `Uni<...>` 返却 等）といった構造依存の残りルールを段階追加する。テスト規約のうち `@DisplayName` 必須は `TestConventionsArchTest` で、AssertJ 統一は Checkstyle で強制済み
13. JSpecify 移行の続行（§5.1、集約→エンティティ→VO→infra の順）
14. DataSource 統合テスト（Phase 9）、カバレッジ計測（JaCoCo導入検討）

---

## 7. ArchUnit 導入計画（アーキテクチャ制約の強制）

ABService のアーキテクチャ制約（レイヤー依存方向・配置・ライブラリ依存・戻り値契約など）を ArchUnit のテストで強制します。CI で違反を検出し、新規コードを最初から制約に沿わせます。

### 7.1 導入と基本ルール（フェーズAで先行）

```gradle
// build.gradle（test依存）
testImplementation 'com.tngtech.archunit:archunit-junit5:1.4.2'
```
アーキテクチャテストは `src/test/java/com/abservice/architecture/` に JUnit5 テストとして配置（DB不要なので unit 側でよい）。

**フェーズAで先行して入れる基本ルール**（既存構造だけで検証でき、新規コードを最初から制約に沿わせるもの）:
- レイヤー依存方向: `layeredArchitecture()` で domain ← application ← presentation、domain ← infrastructure
- `@Entity` は `..infrastructure.persistence.entity..` 内のみ
- `..domain..` から `java.time..` への依存禁止（`BusinessDate`/`BusinessDateTime` は除外）
- `@Transactional` 禁止（Reactive は `@WithTransaction`）
- `Repository` 継承IFのメソッド戻り値は `Uni<...>`

テスト規約のうち `@DisplayName` 必須は `TestConventionsArchTest` で強制済み（§7.2）。app/presentation の構造に依存する命名・戻り値型契約ルール（§7.2 の表の残り）は、それらのレイヤー実装後（**フェーズD**）に追加する。

### 7.2 ArchUnit で強制する制約（全体）

下表のうち §7.1 に挙げた基本ルールはフェーズAで先行導入済み。テスト規約の `@DisplayName` 必須は `TestConventionsArchTest`（テストクラスを検査対象に含めるため `LayeredArchitectureTest` とは別クラス）で強制済み。残る命名・戻り値型契約など app/presentation の構造に依存するルールは、それらのレイヤー実装後（フェーズD）に追加する。なお「JUnit assertion 禁止」は ArchUnit ではなく Checkstyle で強制している。

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
| コンストラクタ可視性 | `EntityId` 実装のコンストラクタは private、`domain.model` のコンストラクタは非public |
| レイヤー依存方向 | domain ← application ← presentation、domain ← infrastructure |

メソッド本文やコメントの検査が必要な制約（try-catch 禁止、VO のバリデーション必須など）は ArchUnit では表現できないため対象外。必要なら Checkstyle/PMD で個別対応する。

### 7.3 detekt カスタムルール26件との対応（Java 担保手段）

ABService は Java プロジェクトのため、Kotlin 向けの detekt カスタムルール26件はそのまま使えない。同等の制約を **ArchUnit / Checkstyle / javac・言語文法**で担保するか、Java に構文的対応物がないものは対象外とする。移植可否は「**強制手段の構文**」ではなく「**制約の意図が Java に適用可能か**」で判定する。

現状: **強制済み14件（ArchUnit 10・Checkstyle 4）／ 対象外5件 ／ 未強制の欠落7件**。Java 相当のある21件に対し 14件（約67%）を強制。

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
| ForbiddenInternalConstructorInDomainModel | domain モデルのコンストラクタ可視性を絞る | ArchUnit（**非 record のみ**。record はコンパクトコンストラクタ＋ファクトリ規約で担保） | 🟡部分 |
| ForbiddenTryCatchInDomain | domain で try/catch 禁止 | Checkstyle（`DomainNoTryCatch`） | ✅強制 |
| RequireSuppressJustification | 抑制に理由必須 | Checkstyle（`@SuppressWarnings` 理由コメント必須） | ✅強制 |
| ForbiddenJUnitAssertions | テストは AssertJ に統一 | Checkstyle（JUnit `Assertions` import 禁止） | ✅強制 |
| ForbiddenMutableCollectionFactory | domain で可変コレクション生成禁止 | Checkstyle（`DomainNoMutableCollection`） | ✅強制 |
| ForbiddenNotNullAssertionInDomain | `!!`（not-null assertion）禁止 | Java に該当演算子なし。意図は JSpecify 移行（§5.1）へ | ⛔対象外 |
| ForbiddenBacktickTestMethodName | バッククォートのテストメソッド名禁止 | Java はメソッド名に空白不可。可読名は `@DisplayName` で充足 | ⛔対象外 |
| ProhibitWhenForUnitBranches | 副作用分岐に `when` を使わず `if` を使う | Java は `if`=文で自動充足（副作用は `if`/`switch` 文） | ⛔対象外 |
| ForbiddenInitInValueObject | VO で `init` ブロック禁止（検証を集約） | record に `init` なし。意図は VO 検証集約（下記 RequireValidationInValueObject）へ | ⛔対象外 |
| RequireUniReturnTypeOnApplicationService | ApplicationService の `execute`/`query` は `Uni<...>` | ArchUnit（**アプリ層の具象実装後**に追加） | ❌保留 |
| RequireValidationInValueObject | VO は検証を持つ | PMD（メソッド本文検査。ArchUnit では表現不可） | ❌未 |
| ForbiddenLogicalOperators | 中置論理演算子 `&&`/`\|\|`/`!` を禁止し宣言的合成へ | Checkstyle `IllegalToken`（`LAND`/`LOR`/`LNOT`） | ❌未 |
| ForbiddenVarInDomain | domain で可変変数禁止 | Checkstyle（domain の field/local に `final` 強制） | ❌未 |
| RequireValueClassForEntityId | EntityId は値型 | ArchUnit（EntityId 実装は record であること） | ❌未 |
| RequirePrivateConstructorForEntityId | EntityId の生成を検証経由に限定 | public record は canonical constructor を private 化できない（言語制約）。全生成経路でコンパクトコンストラクタ検証が必ず走るため意図は充足 | ⛔対象外 |
| ForbiddenFullyQualifiedTypeReference | インライン FQN 禁止（import + 単純名） | Checkstyle 正規表現 | ❌未 |
| PreferWhenForValueBranches | 値の分岐を `if` で返さず式にする | PMD（**`if` 文の値 return 禁止＋`switch` 文禁止 → ternary / switch 式へ**） | ❌未 |

**`if`/式の運用方針**（PreferWhenForValueBranches の Java 仕様）:

- 値の生成は**式のみ**（ternary `?:` または switch 式）。`if` 文での値 return と `switch` 文は禁止する。
- `if` が許されるのは **副作用への分岐**と**例外（`throw`）への分岐**のみ。`if (c) throw ...;`・`if (c) { sideEffect(); }`・値を伴わない `return;` は許容し、`if (c) return <値>;` は禁止する。
- sealed 型に switch 式を用いれば、網羅性は **javac がコンパイル時に担保**する（lint 不要）。例外分岐の `if` を許す方針は §3 のエラー設計（ビジネス違反=`DomainException` 階層への分岐）と整合する。

### 7.4 機能的スタイル強制の残ルール・設計バックログ

命令的な `if`・逐次検査・生ラムダを排し、**式・Optional・filter・Policy・多態**で表現する機能的スタイルを全面採用する。本文適用は完了済み（src/main の実 `if` は 0）。**ルール化（静的解析での強制）と支援 API の残項目**は GitHub issue で管理する。

- トラッキング: **#37**（`harness-backlog` ラベル）
- 各項目の詳細・検出方針・受け入れ条件は個別 issue（#26–#36）を参照。導入時は「導入前に違反を検出できること」「フォーマッタ/PMD 整合（違反 0）」を確認する。

---

## 8. 参照ドキュメント

- 個別計画（背景・詳細手順）: `JSPECIFY_MIGRATION_PLAN.md` / `REPOSITORY_SIMPLIFICATIONS.md` / `UNIT_TEST_PLAN.md`（`backend/`）/ `VO_REFACTORING.md`
- 設計: `ARCHITECTURE.md` / `DOMAIN_MODEL_DESIGN.md` / `DATABASE_DESIGN.md` / `ID_DESIGN_POLICY.md` / `AUDIT_COLUMNS.md`
- 規約: `CODING_GUIDELINES.md` / `RESULT_TYPE_GUIDE.md` / `REPOSITORY_IMPLEMENTATION.md`
</content>
</invoke>
