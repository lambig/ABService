# コーディングガイドライン

本書は ABService の**設計上の意図・判断基準**（なぜその書き方を選ぶか）を記述する。

**機械的に検証できる規約は静的解析で強制済みであり、本書では繰り返さない。** 強制ルールの一覧と実体は §1、および各設定（`backend/config/`・`backend/build.gradle`）が正。

具体的な実装例は本書に埋め込まず、**現行コードの実クラスを正**とする（記載例の陳腐化と実装との乖離を避けるため）。

---

## 1. 静的解析ガバナンス（強制済み）

ABService のアーキテクチャ制約・コーディング規約は多層の静的解析で **CI 強制済み**。**正は設定・テストの実体**（本節は概要のみ）:

| 手段 | 実体 | 主な強制内容 |
|---|---|---|
| ArchUnit | `backend/src/test/java/com/abservice/architecture/`（`LayeredArchitectureTest` / `TestConventionsArchTest` / `AggregateConstructionArchTest`） | レイヤ依存方向、`@Entity` 配置/命名、`@Transactional` 禁止、Repository/ApplicationService の `Uni` 返却契約、domain の `java.time`/`Uni`/Provider 禁止、コンストラクタ可視性、フィールド final、`@DisplayName` 必須、Aggregate/Entity の private 全項目コンストラクタは `@AggregateFactory` 付きメソッド以外から呼べない（Always Valid、#101）、Command REST リソース（`*CommandResource`）と管理向け Query REST リソース（`*AdminQueryResource`）は `@RolesAllowed` 必須（#116）、参照先集約の状態に依存する遷移（`@CrossAggregateTransition`）は参照先を伴って構築される操作オブジェクト（`@CrossAggregateOperation`）以外から呼べない（呼び出し・メソッド参照の双方を検査、#176） |
| Checkstyle | `backend/config/checkstyle/`（+ `suppressions.xml`・`checkstyle-xpath-suppressions.xml`） | domain の try-catch禁止、production全域の可変コレクション直接生成禁止（`infrastructure.persistence` 境界のみ例外）、中置論理演算子禁止、全ローカル final、JUnit assertion 禁止（AssertJ 統一）、`@SuppressWarnings` 理由必須、`domain.model` 配下はクラス・フィールド・publicメソッドへのJavadoc必須（`JavadocVariable`/`JavadocType`/`JavadocMethod`。privateメソッドは対象外）、production全域でインラインコメント（`//`）は大文字+ハイフン＋コロンのprefixを伴う"why not"のみ許可（`InlineCommentRequiresWhyNotPrefix`。一般的な理由=whyはPMD/ArchUnit側のJavadocへ、#99。詳細は§8）、Always Validパターンの全項目構築経路（`@DomainConstructor`/`@DomainFactory`）は`SuppressionXpathFilter`で`ParameterNumber`を抑止し個別の理由コメントを不要化（#103） |
| PMD | `backend/config/pmd/ruleset.xml` | `if` 文全廃、VO/record の検証必須、FQN 禁止、`if` 値 return / `switch` 文禁止、可変コレクタ（`Collectors.toList/toSet/toMap`）・Collection/Map変異呼び出し禁止（型解決で判定、`infrastructure.persistence` 境界のみ例外） |
| NullAway / ErrorProne | `backend/build.gradle` | `@NullMarked`（package-info）＋ `@Nullable` によるコンパイル時 null 安全。`main` 全体（`..persistence.entity..` は Hibernate populate 体のため対象外）を ERROR で強制。設計・除外方針は #44 |
| Spotless | `backend/config/spotless/eclipse-formatter.xml` | フォーマット |

detekt（Kotlin）カスタムルール26件相当は、Java に構文的対応物のある21件を全件強制・5件は対象外（`!!`・バッククォート名など Java に存在しない構文）。

**維持すべき設計方針**（今後の拡張で保つ）:
- **規約ベースのルールは実装を待たず先行導入する**。対象0件の間は `allowEmptyShould(true)` で不活性、最初の実装が入った瞬間から強制。「機能実装を待ってからルール化」はしない。
- **値の生成は式のみ**（ternary / switch 式）。`if` は副作用・例外（`throw`）分岐に限る。sealed 型 + switch 式で網羅性を javac が担保。§6 のエラー表現と整合。
- **NullAway / ErrorProne のバージョン固定（管理下の一時的負債・要追随）**: `error_prone_core 2.39.0` + `nullaway 0.12.7`（`net.ltgt.errorprone 5.1.0`）。ErrorProne 内部 API 密結合のため両者を揃える（最新 `error_prone_core 2.50.0` は非互換）。**昇格トリガ**: NullAway が 2.50 系対応版を出したら両者 bump。**退避路**: JSpecify アノテーションはツール非依存のため Checker Framework へ差し替え可能。
- 追加ルールの検討状況は [STATUS_AND_ROADMAP.md](STATUS_AND_ROADMAP.md) の残タスク参照（SpotBugs/PMD 組込ルールセットの再導入）。

---

## 2. 型安全性を最優先する

Java 25 の型システムを最大限活用し、不正状態をコンパイル時に排除する。

- **値オブジェクト**: `record`（不変）。ビジネス概念を表す値はプリミティブを直接使わず VO 化する。
- **状態表現**: sealed interface + `record` で状態を型として表し、`switch` 式の網羅性検査を javac に担保させる。boolean フラグ + 実行時チェックで状態を管理しない。
- **不正状態の検証**: 実行時例外に依存せず、まず型で防ぐ。型で防げない検証は `Policy`（`domain.model.policy`）で表現する。**命令的な `if`+`throw` は禁止**（PMD `ForbiddenIfStatement`）。`if` は副作用・例外分岐に限り、値の生成は式（三項 / `switch` 式）で行う。

参照実装: 状態の型表現は `domain.model.vo.event`（`EventToParticipate` とその permits）、検証の Policy 化は各集約・VO（`Album` / `AlbumTitle` 等）。

## 3. 不変性

- **値オブジェクト**: 完全に不変（`record`）。
- **エンティティ / 集約**: Lombok `@With(AccessLevel.PRIVATE)` による不変更新パターン。更新は新しいインスタンスを返す。
- コレクションは `List.copyOf` 等で不変化して保持する。

## 4. 生成パターンの選択

コンストラクタは `private`（ArchUnit で強制）とし、用途に応じて生成手段を選ぶ:

| 状況 | 手段 | 参照 |
|---|---|---|
| 単純な値の詰め込み（検証のみ） | Lombok ファクトリ（`@AllArgsConstructor(staticName = "of")`）またはコンパクトコンストラクタ + `of()` | `AlbumTitle` |
| 型変換・簡単な加工 | Static Factory Method（`create()` / `reconstruct()`） | `Track` |
| 外部依存（Repository 等）や複雑な生成 | `Factory` クラス（`@ApplicationScoped`） | `domain.factory` |
| 永続化層からの再構成 | `reconstruct()` | 各集約・エンティティ |

VO の外部入力用の生成は、例外 throw の `of()`（内部生成）と `Result` を返す `fromInput()`（外部入力・複数エラー収集）の2系統を用意する（`ArticleType` / `MarkupContent` が先行例）。

## 5. CQRS とリアクティブ

- **更新系は `CommandService`**: `Repository`（Write Model）経由でドメインモデルを操作。`execute()` は `@WithTransaction` で `Uni<Output>` を返す。
- **照会系は `QueryService`**: `DataSource` から Read Model DTO を取得。`query()` は `Uni<Result>` を返す。
- ドメインオブジェクトを外部へ直接公開せず、Input/Output・Request/Response DTO を介す。
- 同期→非同期は `Uni.createFrom().item(() -> ...)`、連鎖は `flatMap`、同期変換は `onItem().transform`、未存在の例外化は `onItem().ifNull().failWith(...)`。
- ブロッキングの `@Transactional` は使わない（Mutiny 非互換）。`@WithTransaction` を使う。

レイヤ依存方向・返却型契約・CQRS の非対称（Command=Repository / Query=DataSource）は ArchUnit が強制する（§1）。参照実装: `application.service.article` / `application.query.article`、REST は `presentation.rest.article`。

## 6. エラー表現

3層で使い分ける（`Result` のAPIと使用例は `lib.Result` の Javadoc・`ResultTest`・`lib.example.ResultExample` が正）:

- **値検証（複数エラー収集）**: `Result<T>`（`lib.Result`）。
- **リソース未存在**: empty `Uni` → `EntityNotFoundException` へ変換。
- **ビジネスルール違反**: `DomainException` 階層（`ValidationException` / `EntityNotFoundException` / `BusinessRuleViolationException`）。
- **HTTP 変換**: presentation 層の `DomainExceptionMapper` が RFC9457 `ProblemDetail`（400/404/409/5xx）へ変換する。

## 7. データベース

- 共通監査列（7列）は `AuditableTableRecord` と各マイグレーションが正。運用ルールと理由は [DECISIONS.md](DECISIONS.md) §5。
- ドメイン ID（UUIDv7 文字列）と DB 内部 ID（`Long`）の分離は `EntityId` と `*TableRecord` が正。理由は [DECISIONS.md](DECISIONS.md) §1。
- ドメイン層の日付・日時は `BusinessDate` / `BusinessDateTime` を使う（`java.time` 直接使用は domain では ArchUnit で禁止）。インフラ層・変換処理では `LocalDate` 等の使用を許可する。

## 8. コメント方針

**why（一般的な動機・ルールの背景）はインラインコメントに書かない。** 一般的な設計判断・規約の理由は、その規約自体が定義されている場所（PMD/Checkstyleルールのメッセージ・ArchUnitルールの Javadoc・関連アノテーションの Javadoc）に書く。同じ説明を複数クラスに逐語コピペしない（#99・[[abservice-archunit-plan]] も参照）。フィールド・型・publicメソッドの意味は、コメントではなく命名で表現するか Javadoc に書く（`domain.model` 配下は Checkstyle が Javadoc 必須を強制）。

**インラインコメント（`//`）として許容されるのは "why not" のみ**: その実装箇所において、標準的なやり方やアプリケーション全体の方針と異なる特殊な設計・実装判断が行われた理由（HACK・ワークアラウンド・型推論の限界・lintルール抑制の安全性根拠など）。判定基準: このコメントを消したとき、読み手が「なぜ普通のやり方・アプリの標準パターンにしなかったのか」と疑問に思うなら why not（許容）。「これは何を意味するのか」としか思わないなら why（Javadoc/命名/docsへ）。

why not コメントは大文字+ハイフンの語＋コロンのプレフィックスを付与する（Checkstyle `InlineCommentRequiresWhyNotPrefix` が強制、例: `// HACK: 理由` `// CRTP: 理由`）。複数行にまたがる説明は `//` の行連続ではなく `/* PREFIX: ... */` ブロックコメントを使う（プレフィックスは1行目のみでよく、フォーマッタによる再改行の影響を受けない）。

参照実装: `AuditableTableRecord`（CRTP）、`ArticleRepositoryImpl`（HIBERNATE-CASCADE）、`AlbumDistribution`（EMPTY-RULESET）。

**同一の why not 理由が複数箇所に反復する場合**（`@SuppressWarnings` の仕組み上、理由コメントは抑止対象の箇所ごとに物理的に必要になるため起こりうる）は、その理由が単発の例外ではなく再利用可能な設計パターンであることの表れなので、意味を表す独自アノテーション＋各静的解析ツール固有の抑止経路（Checkstyleは `SuppressionXpathFilter`、PMDはルールごとの `violationSuppressXPath`）に置き換えることを検討する。`@SuppressWarnings` を別アノテーションで包んでも抑止効果は継承されない（Javaのメタアノテーションの仕組み上）ため、各ツールへの接続はツールごとに個別に設定する。ArchUnitと併用する場合は `@Retention(RetentionPolicy.CLASS)` が必要（ArchUnitはコンパイル済みバイトコードをインポートするため）。参照実装: `DomainConstructor` / `DomainFactory`（Always Validパターンの全項目構築経路、`ParameterNumber` 抑止、#103）。

---

## 9. 述語合成と文字列整形

静的解析が強制するのは「禁止」までで、「何で置き換えるか」は本書が持つ。置き換え先は `io.github.lambig:toolbox-java`（述語合成 DSL と `TextEscape` を同梱）に揃え、同等物を自作しない。

- **述語・条件**: 中置論理演算子（`&&` / `||` / `!`）は Checkstyle が禁止する（§1）。置き換えは `Predicates.and` / `Predicates.or` を static import して `and(...)` / `or(...)`、否定は JDK の `Predicate.not`。射影フィールドの比較は `By.having(getter).thatEqualsTo(value)`（内部が `Objects.deepEquals` のため null 安全）。`Objects.equals` の直書きは避ける（static import しようとしても、継承した `Object.equals(Object)` が非修飾呼び出しをシャドウしてコンパイルできない）
- **型判別**: `instanceof` / キャスト / `switch(this)` は smell。sealed 兄弟型の narrowing は `DomainObject.asType(Class<R>)` に集約し、`Optional.map(asType(X.class))` の形で使う（instanceof の唯一の集約点）
- **取り出し**: 「検査してから `get` / `resolve` で取り出す」は `Optional.get` と同じ smell。applicative（`Result.ap` / `Result.zip`）で `map` を通す。合成の primitive が足りなければライブラリ側へ追加する
- **文字列整形**: `substring` と `+` の連結で組まない。`TextEscape.escape("${a}-${b}")` でテンプレートは1本・ビルダは1回、`.where(key, value)` で束縛し `.compile()` は最後に1回だけ呼ぶ。**条件分岐は `where` へ渡す値の中で吸収する**（条件ごとに escape / compile を作らない）
- point-free・カリー化を優先する（型を先に固定する `asType(型)`、`having(g).that(p)`）

参照実装: 述語合成は各 `Policy` と VO の `equivalentTo`、文字列整形は `domain.model.vo.album.Isdn`。

---

## 10. 名前は処理と揃える（取得と検証を混ぜない）

- **`find*` は取得**。呼び出し側が戻り値を使うことを意味する。戻り値を捨てて検証目的だけで呼ぶなら、それは取得ではない
- **`require*` は検証**。満たさなければ失敗させる意図を名前に出す。取得を伴う必要がなければ戻り値も返さない
- **判定の名前は判定している内容と一致させる**。分岐の理由が増えたら名前を広げる（例: 公開状態だけを見ていたメソッドに参照失効の判定を足したなら、名前は「公開状態の確認」ではなくなる）
- **集約をまたぐ操作は「試み」をオブジェクトにする**。参照先を伴って構築し、規則の判定（`asValidated`）と規則を満たすときだけの遷移を自身に持たせる。規則は値だけで評価できる `Policy` としてその内側に閉じ、呼び出し側が検証対象を組み立てない。参照先を引く責務（I/O）はドメインサービスが持ち、操作オブジェクトを組み立てて実行する
- **「検証してから遷移を呼ぶ」順序を呼び出し側に持たせない**。参照先の状態に依存する遷移は `@CrossAggregateTransition` を付け、`@CrossAggregateOperation` の操作オブジェクト以外から呼べないことを ArchUnit で強制する（参照先を渡さなければ構築できないため、順序を守り忘れる経路が構造上なくなる）
- コマンドサービスは取得・保存・出力の合成に徹し、集約単体で決まる制約（記事種別など）はコマンドサービスかドメインモデルに残す

参照実装: `domain.model.aggregate.article.ArticlePublication` / `AlbumAttachment`（規則と遷移）、`domain.service.ArticlePublicationService`（参照先の取得と組み立て）、`domain.service.AlbumExistenceService`（存在確認のみ）。

---

## 参考資料

- [ARCHITECTURE.md](ARCHITECTURE.md) - 構成・境界・経路の決定
- [DECISIONS.md](DECISIONS.md) - なぜその構造にしたか（設計判断の記録）
- 参照実装は現行コード: `application.service.article` / `application.query.article` / `presentation.rest.article`、リポジトリは `infrastructure.persistence.repository`
