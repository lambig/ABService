# コーディングガイドライン

本書は ABService の**設計上の意図・判断基準**（なぜその書き方を選ぶか）を記述する。

**機械的に検証できる規約は静的解析で強制済みであり、本書では繰り返さない。** 強制ルールの一覧と実体は [STATUS_AND_ROADMAP.md](STATUS_AND_ROADMAP.md) §7、および各設定（`backend/config/`・`backend/build.gradle`）が正。以下はその代表例で、いずれも本書の記述ではなくルール／設定が拘束する:

- 命名・配置（`@Entity` 命名/配置、`RepositoryImpl`、レイヤ依存方向）、Repository/ApplicationService の `Uni` 返却契約 → **ArchUnit**
- ローカル変数の `final var`、中置論理演算子禁止、domain の try-catch/可変コレクション禁止、AssertJ 統一 → **Checkstyle**
- `if` 文の全廃（値生成は式のみ）、VO/record の検証必須、単一行三項禁止、FQN 禁止 → **PMD**
- domain の `java.time`（`LocalDate` 等）直接使用禁止・`Uni`/Provider 禁止 → **ArchUnit**
- コンパイル時 null 安全（`@NullMarked` + `@Nullable`） → **NullAway**
- フォーマット（三項の複数行整形を含む） → **Spotless**

具体的な実装例は本書に埋め込まず、**現行コードの実クラスを正**とする（記載例の陳腐化と実装との乖離を避けるため）。

---

## 1. 型安全性を最優先する

Java 25 の型システムを最大限活用し、不正状態をコンパイル時に排除する。

- **値オブジェクト**: `record`（不変）。ビジネス概念を表す値はプリミティブを直接使わず VO 化する。
- **状態表現**: sealed interface + `record` で状態を型として表し、`switch` 式の網羅性検査を javac に担保させる。boolean フラグ + 実行時チェックで状態を管理しない。
- **不正状態の検証**: 実行時例外に依存せず、まず型で防ぐ。型で防げない検証は `Policy`（`domain.model.policy`）で表現する。**命令的な `if`+`throw` は禁止**（PMD `ForbiddenIfStatement`）。`if` は副作用・例外分岐に限り、値の生成は式（三項 / `switch` 式）で行う。

参照実装: 状態の型表現は `domain.model.vo.event`（`EventToParticipate` とその permits）、検証の Policy 化は各集約・VO（`Album` / `AlbumTitle` 等）。

## 2. 不変性

- **値オブジェクト**: 完全に不変（`record`）。
- **エンティティ / 集約**: Lombok `@With(AccessLevel.PRIVATE)` による不変更新パターン。更新は新しいインスタンスを返す。
- コレクションは `List.copyOf` 等で不変化して保持する。

## 3. 生成パターンの選択

コンストラクタは `private`（ArchUnit で強制）とし、用途に応じて生成手段を選ぶ:

| 状況 | 手段 | 参照 |
|---|---|---|
| 単純な値の詰め込み（検証のみ） | Lombok ファクトリ（`@AllArgsConstructor(staticName = "of")`）またはコンパクトコンストラクタ + `of()` | `AlbumTitle` |
| 型変換・簡単な加工 | Static Factory Method（`create()` / `reconstruct()`） | `Track` |
| 外部依存（Repository 等）や複雑な生成 | `Factory` クラス（`@ApplicationScoped`） | `domain.factory` |
| 永続化層からの再構成 | `reconstruct()` | 各集約・エンティティ |

VO の外部入力用の生成は、例外 throw の `of()`（内部生成）と `Result` を返す `fromInput()`（外部入力・複数エラー収集）の2系統を用意する（`ArticleType` / `MarkupContent` が先行例）。

## 4. CQRS とリアクティブ

- **更新系は `CommandService`**: `Repository`（Write Model）経由でドメインモデルを操作。`execute()` は `@WithTransaction` で `Uni<Output>` を返す。
- **照会系は `QueryService`**: `DataSource` から Read Model DTO を取得。`query()` は `Uni<Result>` を返す。
- ドメインオブジェクトを外部へ直接公開せず、Input/Output・Request/Response DTO を介す。
- 同期→非同期は `Uni.createFrom().item(() -> ...)`、連鎖は `flatMap`、同期変換は `onItem().transform`、未存在の例外化は `onItem().ifNull().failWith(...)`。
- ブロッキングの `@Transactional` は使わない（Mutiny 非互換）。`@WithTransaction` を使う。

レイヤ依存方向・返却型契約・CQRS の非対称（Command=Repository / Query=DataSource）は ArchUnit が強制する（§7）。参照実装: `application.service.article` / `application.query.article`、REST は `presentation.rest.article`。

## 5. エラー表現

3層で使い分ける（詳細は [RESULT_TYPE_GUIDE.md](RESULT_TYPE_GUIDE.md) と STATUS_AND_ROADMAP.md §3）:

- **値検証（複数エラー収集）**: `Result<T>`（`lib.Result`）。
- **リソース未存在**: empty `Uni` → `EntityNotFoundException` へ変換。
- **ビジネスルール違反**: `DomainException` 階層（`ValidationException` / `EntityNotFoundException` / `BusinessRuleViolationException`）。
- **HTTP 変換**: presentation 層の `DomainExceptionMapper` が RFC9457 `ProblemDetail`（400/404/409/5xx）へ変換する。

## 6. データベース

- 共通監査列（7列）は [AUDIT_COLUMNS.md](AUDIT_COLUMNS.md) が正。
- ドメイン ID（UUIDv7 文字列）と DB 内部 ID（`Long`）の分離方針は [ID_DESIGN_POLICY.md](ID_DESIGN_POLICY.md) が正。
- ドメイン層の日付・日時は `BusinessDate` / `BusinessDateTime` を使う（`java.time` 直接使用は domain では ArchUnit で禁止）。インフラ層・変換処理では `LocalDate` 等の使用を許可する。

---

## 参考資料

- [STATUS_AND_ROADMAP.md](STATUS_AND_ROADMAP.md) §7 - 強制済み静的解析ルールの一覧（正は各設定・テスト）
- [ARCHITECTURE.md](ARCHITECTURE.md) / [DOMAIN_MODEL_DESIGN.md](DOMAIN_MODEL_DESIGN.md) - アーキテクチャ・ドメインモデル設計
- [REPOSITORY_IMPLEMENTATION.md](REPOSITORY_IMPLEMENTATION.md) - リポジトリ実装ガイド
- [RESULT_TYPE_GUIDE.md](RESULT_TYPE_GUIDE.md) - Result 型の使用ガイド
