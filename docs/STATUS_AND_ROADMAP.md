# 開発状況と再開ロードマップ

> **このドキュメントの位置づけ**
> ABService の**実コードと突き合わせて検証した現状**と、優先度付きの再開計画を示すマスタードキュメントです。進捗の正はこのドキュメントを参照してください。
> 個別の設計doc（`VO_REFACTORING.md` / `UNIT_TEST_PLAN.md` 等）は背景・詳細手順のリファレンスとして残しています。
> 残タスクは完了次第この文書から削除し、ロードマップとしての記載事項がゼロに収束することを維持します（何をいつ実施したかの正は git コミット履歴）。

---

## 1. 現状サマリ（レイヤー別・検証済み）

ビルドは現時点でも成功します（`./gradlew -p backend compileJava` = exit 0）。

| レイヤー | 状態 | 補足 |
|---|---|---|
| **ドメイン層** | 🟢 ほぼ完成 | 集約 `Album` / `AlbumArticle` / `Article` / `Tune`、VO 約20、`EventMatchingService`。検証は `Policy` へ移行済み。ビジネスロジックのユニットテスト充実 |
| **インフラ層** | 🟢 完成 | JPAエンティティ・Mapper・RepositoryImpl（4集約）、Flyway、Reactive Panache |
| **アプリケーション層** | 🟢 4集約でCreate/Get縦通し済み | `CommandService` / `QueryService` 基底に加え、Article/Tune/Album/AlbumArticle 各集約の Command（`Create*Service`）/ Query（`Get*Service` + `*View`）を実装。**各集約の残ユースケース（更新/削除、一覧Query）は未着手** |
| **プレゼンテーション層** | 🟢 4集約でCreate/Get縦通し済み | Article/Tune/Album/AlbumArticle 各集約の REST（`*CommandResource` / `*QueryResource` + Request/Response DTO）、RFC9457 `ProblemDetail` + `DomainExceptionMapper` を実装。サンプル `GreetingResource` / `HealthResource` は残置。**各集約の残ユースケース向け Resource は未着手** |
| **共通基盤（lib）** | 🟢 完成 | `Result`（combinator `map`/`flatMap`/`zip` 含む）/ `ErrorResult` を実装。ドメイン例外階層（`DomainException` 抽象基底 + `ValidationException`/`EntityNotFoundException`/`BusinessRuleViolationException`）も整備済み |
| **テスト** | 🟡 ユニット充実・統合は選択的 | VO/集約/エンティティのユニット、Article/Tune/Album/AlbumArticle のアプリ層/例外マッパーのユニット、各集約の REST の E2E 統合テスト、`AlbumRepositoryImplTest` / `AlbumArticleRepositoryImplTest` / `ArticleRepositoryImplTest`。残る統合テストは §4.1 |
| **静的解析** | 🟢 完了 | Checkstyle + Spotless + PMD + ArchUnit + NullAway で多層強制（レイヤ依存方向・配置・戻り値契約・機能的スタイル・コンパイル時 null 安全）。強制設計・対象ルールは [CODING_GUIDELINES.md](CODING_GUIDELINES.md) 静的解析ガバナンス節。SpotBugs / PMD 組込ルールセットの再導入のみフェーズB で検討（§5 フェーズB） |
| **フロントエンド** | ⬜ 未調査 | `frontend-admin`（Svelte）/ `frontend-public`（Svelte+Astro）。本ドキュメントの対象外 |

**一言でいうと**: **Article / Tune / Album / AlbumArticle の4集約で domain→app→REST→統合テストの Create/Get 縦通しが完了**。次に積むべきは **各集約の残ユースケース（更新/削除、一覧Query、tracks/acquisitionChannels 等の子要素追加）** と、残る統合テスト（§4.1、#45）です。

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
│   ├── persistence/{repository, entity(*TableRecord), mapper, datasource}
│   └── datetime/
└── presentation/rest/
    ├── *Resource.java         CQRSで分割（Command/Query）
    ├── request/               リクエストDTO
    ├── response/              レスポンスDTO + エラーレスポンス
    └── exception/             JAX-RS ExceptionMapper
```

現行パッケージ（`com.abservice.domain / application / infrastructure / presentation`）はこの構成に沿っています。`application` / `presentation` は **Article/Tune/Album/AlbumArticle の4集約で Create/Get が実装済み**です。各集約の残ユースケース（更新/削除・一覧）は今後の実装対象です。

### 2.2 採用する設計パターン

| パターン | ABService での方針 |
|---|---|
| **CQRS の Read/Write 分離** | Command は `Repository`（Panache/Write Model）経由、Query は `DataSource` 直アクセスで Read Model DTO を返す。両者は既に分離済み |
| **Command ユースケース** | `@ApplicationScoped` な `CommandService<Input, Output>` 実装。`@WithTransaction execute(): Uni<Output>`。Input/Output は同パッケージの record |
| **Query ユースケース** | `QueryService<Request, Result>` を `application/query/` に配置。`@WithSession query(): Uni<Result>`。Read Model は `application/query/model/`。`sealed interface`（`Found`/`NotFound`）で 200/404 を出し分け。`INSUFFICIENT_DATA`（422）相当は未導入（将来追加可能） |
| **REST Resource** | `presentation/rest/` に集約ごとに Command/Query Resource を作成。`Uni<Response>` 返却、MicroProfile OpenAPI アノテーション |
| **VO の2系統生成** | 内部生成は例外throwのコンパクトコンストラクタ/`of()`、外部入力は `Result` を返す `fromInput()` の2系統。Article/Tune/Album/AlbumArticle の主要VOで導入済み。`BusinessDate` はドメイン層が文字列パースを提供しない設計のため、境界層（`Create*Service`）が文字列解釈・例外変換を担う。子コレクションを内部に持つ複合VO（`EventReleasedAt` 等）は横展開の対象外（§4.2） |
| **3層のエラー表現** | 値検証=`Result`、未存在=empty `Uni`+`failWith`、ビジネス違反=`DomainException` 階層。§3 / §4 参照 |
| **テスト分割** | `unitTest`（Fake注入・DI無）/ `integrationTest`（@QuarkusTest・実DB）の2分割済み。実HTTP のテストは外部連携が出た時点で検討 |

---

## 3. エラーハンドリング設計のギャップ

「値検証 / 未存在 / ビジネス違反」を層で明確に使い分ける方針です。ABService の現状との差分:

| 種別 | 目標 | ABService 現状 | 対応 |
|---|---|---|---|
| 値検証（複数エラー収集） | `Result<T>` + `resolve/zip/map/flatMap` | `resolve`/`orElse*`/`map`/`flatMap`/`zip`（arity 2・3、エラー集約）を実装済み | 対応不要 |
| リソース未存在 | empty `Uni` → `.onItem().ifNull().failWith { EntityNotFoundException }` | Article/Tune/Album/AlbumArticle の Query（`Get*Service`）で確立済み（`sealed interface` の `NotFound` バリアントとして表現） | 対応不要 |
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

## 4. 残タスク棚卸し（全体）

### 4.1 ユニット/統合テスト（`UNIT_TEST_PLAN.md`）

- Phase 7: RepositoryImpl 統合テスト（`AlbumRepositoryImpl` 済み。`AlbumArticleRepositoryImpl` / `ArticleRepositoryImpl` は #39/#40/#41 観点のみ部分実装・全CRUD網羅は未着手。残り `TuneRepositoryImpl` は未着手） → #45
- Phase 9: DataSource 統合テスト（Read Model 用の単純な `findByDomainId` は4集約とも整備済み。DataSource 自体の統合テストは未着手）

### 4.2 アプリケーション層 / プレゼンテーション層

- 各集約（Article/Tune/Album/AlbumArticle）の残ユースケース（更新/削除、一覧 Query）
- Album の `tracks`、AlbumArticle の `acquisitionChannels`（コレクションへの追加系ユースケース）、Album の `eventReleasedAt`（内部に `List<EventDateAndSpace>` を持つ複合VO）は Create/Get の横展開では対象外とした。`Album.create()`/`AlbumArticle.create()` 自体がコレクションを受け取らない設計、`eventReleasedAt` は複数の `@OneToMany` を1クエリで JOIN FETCH すると Hibernate の multiple-bag-fetch 制約に抵触するため。追加時は個別ユースケースとして設計する

---

## 5. 推奨再開ロードマップ（優先度順）

> 方針: 「動く縦の1本」を最優先で通し、そのうえで横展開・品質ゲートを固める。Article 集約で確立した domain→app→REST→統合テストの縦通しパターンは、Tune / Album / AlbumArticle への横展開（Create/Get）が完了した。

### フェーズ A: 各集約の深さ方向の拡張（現在地）
1. 各集約（Article/Tune/Album/AlbumArticle）の残ユースケース（更新/削除、一覧Query）を実装する
2. RepositoryImpl 統合テスト（Phase 7）を残り集約に追加（#45）

### フェーズ B: 品質ゲート
1. **ArchUnit 残ルールの点検**（[CODING_GUIDELINES.md](CODING_GUIDELINES.md) 静的解析ガバナンス節）: 命名・配置・戻り値型契約は先行導入済み。追加するのは §2 の規約だけでは述語を書けず**具体実装がないと表現できない**真に構造依存なルールに限る（原則として想定なし。必要が生じた時点で判断）
2. DataSource 統合テスト（Phase 9）、カバレッジ計測（JaCoCo導入検討）
3. **SpotBugs / PMD 組込ルールセットの再導入検討**: SpotBugs 4.10.2（Gradle plugin 6.5.8）は Java25 対応済み。バグパターン系を SpotBugs、collection/security 系を PMD 組込ルールセットで補う。本プロジェクト固有規約（[CODING_GUIDELINES.md](CODING_GUIDELINES.md) 静的解析ガバナンス節）とは別系統で、導入時に顕在化する違反の段階是正・除外スコープ設計が要る。品質ゲート＝ポリシー変更のため都度承認のうえ実施

---

## 6. 参照ドキュメント

- 個別計画・設計判断（背景・詳細手順）: `UNIT_TEST_PLAN.md`（`backend/`）/ `VO_REFACTORING.md`。JSpecify 移行の設計・除外方針は #44 が正
- 設計: `ARCHITECTURE.md` / `DOMAIN_MODEL_DESIGN.md` / `DATABASE_DESIGN.md` / `ID_DESIGN_POLICY.md` / `AUDIT_COLUMNS.md`
- 規約: `CODING_GUIDELINES.md` / `RESULT_TYPE_GUIDE.md` / `REPOSITORY_IMPLEMENTATION.md`
