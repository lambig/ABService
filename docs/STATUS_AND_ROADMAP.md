# 開発状況と再開ロードマップ

> **このドキュメントの位置づけ**
> 開発が一時停止していた ABService を再開するにあたり、散在していた計画ドキュメント（`JSPECIFY_MIGRATION_PLAN.md` / `VO_REFACTORING.md` / `REPOSITORY_SIMPLIFICATIONS.md` / `UNIT_TEST_PLAN.md` 等）の内容を、**実コードと突き合わせて検証した現状**として1本に集約し、優先度付きの再開計画を示すマスタードキュメントです。
> 個別の計画docは背景・詳細手順のリファレンスとして残していますが、進捗の正はこのドキュメントを参照してください。
>
> 最終更新: 2026-07-06 / 検証時コミット: `1b09cd0`

---

## 1. 現状サマリ（レイヤー別・検証済み）

ビルドは現時点でも成功します（`./gradlew -p backend compileJava` = exit 0）。

| レイヤー | 状態 | 補足 |
|---|---|---|
| **ドメイン層** | 🟢 ほぼ完成 | 集約 `Album` / `AlbumArticle` / `Article` / `Tune`、VO 約20、`EventMatchingService`。ビジネスロジックのユニットテスト充実 |
| **インフラ層** | 🟢 完成（一部簡略化残） | JPAエンティティ・Mapper・RepositoryImpl（4集約）、Flyway V1〜V24、Reactive Panache。§4 の簡略化3件が未解消 |
| **アプリケーション層** | 🟡 基底のみ | `CommandService` / `QueryService` インターフェース（使用例つき）は完備。**具象ユースケースは0件** |
| **プレゼンテーション層** | 🔴 未着手 | サンプル `GreetingResource` / `HealthResource` / `CircleMemberResource` のみ。集約向けRESTエンドポイント・DTO・ExceptionMapperなし |
| **共通基盤（lib）** | 🟡 一部 | `Result` / `ErrorResult` は移植済み。ただし combinator（`map`/`flatMap`/`zip`）とドメイン例外階層は未整備（§3, §5） |
| **テスト** | 🟡 ユニット充実・統合が薄い | ユニット31クラス（VO/集約/エンティティ）。統合テストは `AlbumRepositoryImplTest` と `SystemBusinessDateTimeProviderTest` の2本のみ |
| **静的解析** | 🟡 style層のみ | Checkstyle + Spotless 稼働。SpotBugs は Java25非対応で無効。**アーキテクチャ制約の強制（ArchUnit）は未導入**（§7） |
| **フロントエンド** | ⬜ 未調査 | `frontend-admin`（Svelte）/ `frontend-public`（Svelte+Astro）。本ドキュメントの対象外 |

**一言でいうと**: ドメイン＋永続化基盤は固まっており、次に積むべきは **アプリケーション層（ユースケース）→ プレゼンテーション層（REST）** の縦の1本通しと、それを支える **エラー設計・統合テスト・アーキテクチャ制約** です。

---

## 2. 参考アーキテクチャ: internal-platforms / products/album

再開development のアーキテクチャ参照先は `internal-platforms/products/album`（Kotlin/Quarkus/Reactive）です。ABService は Java プロジェクトのため、Kotlin固有の機構（value class, sealed, `init`）は直接移植できませんが、**レイヤー構成・CQRS・エラー設計・テスト分割の思想はそのまま踏襲可能**です。基底型は別リポジトリ `common-libs-kotlin`（`com.abservice:internal-lib`）に集約されています。

### 2.1 album のレイヤー構成（参照マップ）

```
com.abservice.album/
├── domain/
│   ├── model/{aggregate, entity, vo, transition}
│   ├── repository/            interface（実装は infrastructure）
│   ├── service/               ドメインサービス interface (+ 一部Impl)
│   ├── factory/               ファクトリ interface + Impl
│   ├── external/              外部サービス抽象
│   └── exception/             ドメイン例外階層 ★ABServiceに不足
├── application/
│   ├── service/<agg>/         CommandService実装 + Input/Output DTO
│   └── query/                 QueryService実装 + Request/Result
│       ├── model/             Read Model DTO
│       └── mapper/            Row→DTO マッパー
├── infrastructure/
│   ├── persistence/{repository, entity(*Record), mapper}
│   ├── client/                外部/イベント発行
│   ├── domainservice/         ドメインサービスImpl
│   └── datetime/
├── presentation/api/          ★ABServiceに不足
│   ├── *Resource.kt           CQRSで分割（Command/Query/PartialUpdate/Leave）
│   ├── request/               リクエストDTO + Jacksonデシリアライザ
│   ├── response/              レスポンスDTO + エラーレスポンス
│   └── exception/             JAX-RS ExceptionMapper
└── lib/logging/
```

ABService の現行パッケージ（`com.abservice.domain / application / infrastructure`）はこの構成とほぼ一致しています。**未整備なのは `application` の具象、`presentation`（丸ごと）、`domain/exception` の階層化**の3点です。

### 2.2 album から取り込むべき設計パターン

| パターン | album の実装 | ABService への適用方針 |
|---|---|---|
| **CQRS の Read/Write 分離** | Command は Repository（Panache/Write Model）経由、Query は `PgPool` 直SQL（Read Model、Repository/Domainを経由しない） | ABService は既に `DataSource`（Panache）と `Repository` を分離済み。Query は `DataSource` を使い Read Model DTO を返す方針で踏襲 |
| **Command ユースケース** | `@ApplicationScoped class RegisterAlbumService : CommandService<Input, Output>` / `@WithTransaction execute(): Uni<Output>` | `CommandService` 基底の使用例（`UpdateAlbumTitleService`）どおりに実装。Input/Output は同パッケージの record |
| **Query ユースケース** | `QueryService<Request, Result>` / `@WithSession query(): Uni<Result>`、`QueryStatus`(SUCCESS/INSUFFICIENT_DATA/NOT_FOUND)で200/422/404を出し分け | `application/query/` に配置。Read Model は `application/query/model/` |
| **REST Resource** | CQRSで分割（`AlbumCommandResource` / `AlbumQueryResource`）、`Uni<Response>` 返却、MicroProfile OpenAPIアノテーション | `presentation/rest/`（README記載の想定パッケージ）に集約ごとに Command/Query Resource を作成 |
| **VO の2系統生成** | `ofPersisted()`（DB復元・例外戦略）と `fromInput()`（外部入力・`Result`戦略）を分離 | ABService の VO は現状コンパクトコンストラクタで例外throwのみ。外部入力用に `Result` を返す `fromInput()` の追加を検討（§5） |
| **3層のエラー表現** | 値検証=`Result`、未存在=empty `Uni`+`failWith`、ビジネス違反=`DomainException`階層 | §3 / §5 参照。ABService は `Result` はあるが例外階層が未整備 |
| **テスト3分割** | `unitTest`（Fake注入・DI無）/ `integrationTest`（@QuarkusTest・実DB）/ `communicationTest`（実HTTP） | ABService は unit / integrationTest の2分割済み。communicationは外部連携が出てきた時点で検討 |
| **ガイドライン文書の流用** | `products/album/docs/guidelines/*.md`（DOMAIN_MODEL_CHARTER, STATE_MANAGEMENT_DESIGN, TEST_GUIDELINES, API_DESIGN_GUIDELINES, CODE_QUALITY） | 再開development の規約整備で内容を参照・流用可能 |

---

## 3. エラーハンドリング設計のギャップ

album は「値検証 / 未存在 / ビジネス違反」を層で明確に使い分けています。ABService の現状との差分:

| 種別 | album | ABService 現状 | 対応 |
|---|---|---|---|
| 値検証（複数エラー収集） | `Result<T>` + `resolve/zip/map/flatMap` | `Result<T>` あり。ただし **combinator が `resolve`/`orElse`/`orElseGet`/`orElseDo` のみ**。`map`/`flatMap`/`zip`（複数VO検証の合成）が無い | `Result` に `map`/`flatMap`/`zip` を追加（album の `Result.kt` 相当） |
| リソース未存在 | empty `Uni` → `.onItem().ifNull().failWith { EntityNotFoundException }` | Repository は実装済みだが、未存在→例外化の共通パターン未確立 | ユースケース実装時に規約化 |
| ビジネスルール違反 | `DomainException` 階層（`ValidationException` / `EntityNotFoundException` / `BusinessRuleViolationException` + 具象） | `DomainException` は **単一の基底クラスのみ**。サブクラス階層なし | 例外階層を整備（下記） |
| HTTP変換 | `@Provider ExceptionMapper<DomainException>` で 400/404/409/5xx に変換 | 未実装 | presentation層で `ExceptionMapper` を実装 |

**整備すべき例外階層（album準拠）:**
```
DomainException (abstract, errorCode付き)
├── ValidationException(List<ErrorResult>)   → 400
├── EntityNotFoundException                   → 404
└── BusinessRuleViolationException            → 409
```

---

## 4. インフラ層の簡略化（未解消・実コードで確認済み）

`REPOSITORY_SIMPLIFICATIONS.md` に記載の3件は**いずれも現在も未解消**です（コード確認済み）。

| # | 箇所 | 現状 | ファイル:行 |
|---|---|---|---|
| 1 | Article タグ | `Collections.emptyList()` を返す（`ArticleTagLink` 連携なし） | `ArticleMapper.java:41` |
| 2 | AlbumArticle 頒布情報 | 常に `null` | `AlbumArticleMapper.java:37` |
| 3 | AlbumArticle 入手経路 | `Collections.emptyList()` を返す | `AlbumArticleMapper.java:38, 61` |

詳細な実装要件は `REPOSITORY_SIMPLIFICATIONS.md` を参照。優先度は「頒布情報・入手経路（AlbumArticle）> タグ（Article）」。

---

## 5. 残タスク棚卸し（全体）

### 5.1 JSpecify nullability 移行（`JSPECIFY_MIGRATION_PLAN.md`）

**検証結果**: jspecify を import している domain クラスは **11件**（`Album`, `Track`, `Article`, `Tune`, `ArticleTag`, `CatalogNumber`, `AlbumTitle`, `TuneTitle`, `Credit`, `ArtistCredit`, `MarkupContent`）。計画docの「10件完了」からほぼ進んでおらず、**残りは docの見積り以上に多い**。

- 🔴 未対応: 集約 `AlbumArticle`、集約内エンティティ `TrackTune` / `AlbumAcquisitionChannel` / `AlbumDistribution`
- 🔴 未対応: VO の大半（`Isdn`, `Price`, `EventReleasedAt`, `TrackTitle`, `Url`, `LabelTag`, `ChannelType`, `Duration`※削除済, `BusinessDate`, `BusinessDateTime`, `ArtistCreditName`, `TuneKind`, `ArticleType`, `ArticleType`, event系VO群 ほか）
- 🔴 未対応: infrastructure層（Mapper / RepositoryImpl / Entity / DataSource）、application層

### 5.2 ユニット/統合テスト（`UNIT_TEST_PLAN.md`）

- 🟢 完了: Phase 1–5（VO / Enum / 集約 / エンティティ のユニットテスト、計31クラス）
- 🔴 未着手:
  - Phase 6: Application Service のテスト（※ユースケース実装後に発生）
  - Phase 7: RepositoryImpl 統合テスト（現状 `AlbumRepositoryImplTest` のみ、残り3集約）
  - Phase 8: Mapper 統合テスト
  - Phase 9: DataSource 統合テスト
  - Phase 10: REST API 統合テスト（※presentation実装後に発生）
- ⚠️ `UNIT_TEST_PLAN.md` はパス記述が陳腐化（`application/service/QueryService` → 実際は `application/query/`、`interfaces/rest/` → 実際はルート直下、`SampleResource` → `GreetingResource`）

### 5.3 アプリケーション層 / プレゼンテーション層（新規・最重要）

- 🔴 各集約の Command ユースケース（作成/更新/削除）と Input/Output DTO
- 🔴 各集約の Query ユースケース（一覧/詳細）と Read Model DTO
- 🔴 REST Resource（Command/Query）、Request/Response DTO
- 🔴 `ExceptionMapper`（DomainException → HTTP）

### 5.4 その他

- `VO_REFACTORING.md` は完了済みの記録だが命名が陳腐化（`EventInfo` → 実際は `EventReleasedAt`、複数日程対応で構造も変化）
- `Result` の combinator 拡充（§3）
- ドメイン例外階層の整備（§3）

---

## 6. 推奨再開ロードマップ（優先度順）

> 方針: 「動く縦の1本」を最優先で通し、そのうえで横展開・品質ゲートを固める。まず **Article 集約**（依存が少なく単純）で 1 ユースケースを domain→app→REST→統合テストまで貫通させ、パターンを確立してから他集約へ横展開する。

### フェーズ A: エラー設計とlib・静的解析の土台（縦通しの前提）
1. **ArchUnit 導入 ＋ 基本ルール先行**（§7.1）: `archunit-junit5` を test依存に追加し、レイヤー依存方向（domain ← application ← presentation、domain ← infrastructure）・`@Entity` の配置・`@Transactional` 禁止・Repository/ApplicationService の `Uni` 返却契約など、**既存構造だけで検証できる基本ルール**を先に入れる。以降の新規コードが最初から制約に沿うようにする（詳細な命名/戻り値ルールはフェーズDに残す）
2. `Result` に `map` / `flatMap` / `zip`（複数VO検証の合成）を追加
3. `DomainException` 階層を整備（`ValidationException` / `EntityNotFoundException` / `BusinessRuleViolationException`）
4. VO に外部入力用 `fromInput()`（`Result`返却）を段階導入（まず縦通しで使うVOから）

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
12. **ArchUnit 詳細ルールの追加**（§7.2）: app/presentation の構造が揃ってから、命名規約（`*Entity` サフィックス等）・戻り値型契約・`@DisplayName` 必須・AssertJ統一など、構造依存の残りルールを段階追加（導入と基本ルールはフェーズAで実施済み）
13. JSpecify 移行の続行（§5.1、集約→エンティティ→VO→infra の順）
14. DataSource 統合テスト（Phase 9）、カバレッジ計測（JaCoCo導入検討）

---

## 7. ArchUnit 導入計画（arch相当のアーキテクチャ制約）

album は `common-libs-kotlin/arch-rules`（カスタム arch ルール25件）でアーキテクチャ制約を強制しています。ABService は Java プロジェクトのため arch は使えず、**同等の制約を ArchUnit で再現**します。

### 7.1 導入と基本ルール（フェーズAで先行）

```gradle
// build.gradle（test依存）
testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'
```
アーキテクチャテストは `src/test/java/com/abservice/architecture/` に JUnit5 テストとして配置（DB不要なので unit 側でよい）。

**フェーズAで先行して入れる基本ルール**（既存構造だけで検証でき、新規コードを最初から制約に沿わせるもの）:
- レイヤー依存方向: `layeredArchitecture()` で domain ← application ← presentation、domain ← infrastructure
- `@Entity` は `..infrastructure.persistence.entity..` 内のみ
- `..domain..` から `java.time..` への依存禁止（`BusinessDate`/`BusinessDateTime` は除外）
- `@Transactional` 禁止（Reactive は `@WithTransaction`）
- `Repository` 継承IFのメソッド戻り値は `Uni<...>`

app/presentation の構造に依存する命名・戻り値・テスト規約ルール（§7.2 の表の残り）は**フェーズD**で追加する。

### 7.2 ArchUnit で再現する制約 全体（archルール → ArchUnit、移植可能な12件）

下表のうち §7.1 に挙げた基本ルールはフェーズAで先行導入。残り（命名・戻り値型契約・テスト規約など）はフェーズDで追加する。

| arch ルール | ArchUnit での表現 |
|---|---|
| `ForbiddenJpaEntityOutsidePersistenceLayer` | `@Entity` 付与クラスは `..infrastructure.persistence.entity..` 内のみ |
| `ForbiddenJavaTimeInDomain` | `..domain.model..` から `java.time..` への依存禁止（`BusinessDate`/`BusinessDateTime` は除外） |
| `ForbiddenProviderInDomainModel` | ドメインモデルは `BusinessDateTimeProvider` 型フィールドを持たない |
| `ForbiddenPrintlnOutsideLogging` | `System.out`/`System.err` アクセスをロギングパッケージ以外で禁止 |
| `RequireRecordSuffixForJpaEntity` | `@Entity` 付与クラス名は所定サフィックス（現行は `*Entity`。albumは `*Record`。**命名規約の決定が必要**） |
| `ForbiddenTransactionalAnnotation` | `@Transactional` 禁止（Reactive は `@WithTransaction`） |
| `ForbiddenJUnitAssertions` | テストで JUnit 標準 assertion 依存を禁止し AssertJ に統一 |
| `RequireDisplayNameOnTestMethods` | `@Test` メソッドは `@DisplayName` 必須 |
| `RequireUniReturnTypeOnRepository` | `Repository` 継承IFのメソッド戻り値は `Uni<...>` |
| `RequireUniReturnTypeOnApplicationService` | ApplicationService の `execute`/`query` 戻り値は `Uni<...>` |
| `ForbiddenUniInDomainModel` | ドメインモデルの戻り値に `Uni` 禁止（同期実装） |
| `RequirePrivateConstructorForEntityId` / `ForbiddenInternalConstructorInDomainModel` | `EntityId` 実装のコンストラクタは private / domain.model のコンストラクタは非public |
| （追加）レイヤー依存方向 | `layeredArchitecture()` で domain ← application ← presentation、domain ← infrastructure を強制 |

### 7.3 ArchUnit で再現しない（Java非適用 or 本文/コメント検査）

以下は Kotlin構文依存またはメソッド本文/コメント検査のため ArchUnit では表現不可。Java側では対象外、必要なら Checkstyle/PMD で個別対応:
`ForbiddenLogicalOperators`, `ForbiddenFullyQualifiedTypeReference`, `PreferWhenForValueBranches`, `ProhibitWhenForUnitBranches`, `ForbiddenMutableCollectionFactory`, `ForbiddenTryCatchInDomain`, `RequireValidationInValueObject`, `ForbiddenBacktickTestMethodName`, `RequireSuppressJustification`, `ForbiddenNotNullAssertionInDomain`(`!!`), `RequireValueClassForEntityId`(value class), `ForbiddenInitInValueObject`(init) — 後3者は Kotlin固有概念で該当なし。

---

## 8. 参照ドキュメント

- 個別計画（背景・詳細手順）: `JSPECIFY_MIGRATION_PLAN.md` / `REPOSITORY_SIMPLIFICATIONS.md` / `UNIT_TEST_PLAN.md`（`backend/`）/ `VO_REFACTORING.md`
- 設計: `ARCHITECTURE.md` / `DOMAIN_MODEL_DESIGN.md` / `DATABASE_DESIGN.md` / `ID_DESIGN_POLICY.md` / `AUDIT_COLUMNS.md`
- 規約: `CODING_GUIDELINES.md` / `RESULT_TYPE_GUIDE.md` / `REPOSITORY_IMPLEMENTATION.md`
- 移行元: `MIGRATION_NOTES.md`
- 参考実装（外部）: `internal-platforms/products/album`、基底型 `common-libs-kotlin/lib`、archルール `common-libs-kotlin/arch-rules`
</content>
</invoke>
