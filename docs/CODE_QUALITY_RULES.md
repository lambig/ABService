# コード品質ルール移植計画

> **位置づけ / ステータス**
> ABService が目標とするコード品質ルール群（全26項目）を Java で機械強制するための**移植計画（提案・レビュー用）**。
> 本ドキュメントは合意形成のための設計であり、個々のルールの実装は本計画の承認後に着手する。
> ArchUnit 基本ルールの現況は `STATUS_AND_ROADMAP.md` §7 を参照。

---

## 1. 前提と基本方針

目標ルール群は元々 **Kotlin 専用の静的解析（detekt）カスタムルール**として定義・強制されていた。ABService は **Java** 実装のため detekt は使用できず、**1:1 の移植は不可能**である。したがって各ルールは次のいずれかに振り分ける:

- **ArchUnit**: 構造（レイヤー依存・配置・型・戻り値・可視性・命名）に関するルール。Java ネイティブで最も確実。
- **Checkstyle**: トークン/正規表現で判定できる本文レベルのルール（`try` 使用・`@SuppressWarnings` 理由必須 等）。
- **PMD**: errorprone/collection 系。PMD 7.16（2025-07）以降が Java 25 に対応（最新は 7.24, 2026-04）→ **再導入する**。
- **SpotBugs**: errorprone/バグパターン検出。4.9.8（2025-10）/ Maven・Gradle plugin が Java 25 に対応 → **再導入する**（実行時の Java 25 動作は導入時に確認）。
- **カスタム**: 上記で表現できない意味論的ルール（値を返す `if` の禁止 等）。実装コスト高。
- **適用不可（N/A）**: Kotlin 固有機能に依存し Java に対応概念が無いルール。

**方針決定（§4）**: Java 適用可能なルールは、Kotlin 由来のイディオム的ルール（論理演算子の関数化・値を返す `if` の禁止 等）も含め**すべてハード強制する**。可読性トレードオフよりルール統一を優先する。

### 既存コードへの影響（重要）

いくつかのルール（論理演算子禁止・値を返す `if` 禁止 等）は、**現行 ABService コードのほぼ全体が違反**している。方針として（§4 決定）:

- **一括改修（既存違反も是正）**: baseline での先送りはせず、ルール導入時に既存の違反箇所もすべて規約準拠へ改修する。
- **レビュー可能な単位に分割**: 「一括」はルール単位・レイヤー単位に PR を分け、各 PR 内ではそのルールの違反を残さない（＝ルールごとに完全準拠）。巨大単一 PR は避ける。
- 各 PR は pre-commit フックと CI（`check`）で回帰を防ぐ。

---

## 2. ルール別マッピング（全26項目）

採否凡例: ✅採用 / 🔀適応（Java向けに手段や形を変える）/ 📝ガイドライン（機械強制せずレビュー観点）/ ⛔N/A（Java適用不可）

### 2.1 構造ルール（ArchUnit）

| # | ルール（意図） | Java強制手段 | 採否 | 既存違反 | フェーズ |
|---|---|---|---|---|---|
| 1 | domain の `java.time` 直接参照禁止 | ArchUnit | ✅ **実装済** | なし | — |
| 2 | `@Entity` は永続化エンティティ層のみ | ArchUnit | ✅ **実装済** | なし | — |
| 3 | `@Transactional` 禁止（Reactive は `@WithTransaction`） | ArchUnit | ✅ **実装済** | なし | — |
| 4 | Repository IF のメソッドは `Uni<...>` 返却 | ArchUnit | ✅ **実装済** | なし | — |
| 5 | domain.model が `BusinessDateTimeProvider` を保持しない | ArchUnit（フィールド型） | ✅ | 要確認（低） | P1 |
| 6 | domain.model の戻り値に `Uni` を使わない | ArchUnit（戻り値型） | ✅ | 要確認（低） | P1 |
| 7 | `EntityId` 実装のコンストラクタは private / domain.model のコンストラクタは非public | ArchUnit（可視性） | ✅ | 要確認 | P1 |
| 8 | `@Entity` クラス名は `*Entity` サフィックス | ArchUnit（命名） | 🔀 | なし（現行踏襲） | P1 |
| 9 | `println`/`System.out`/`System.err` をロギング以外で禁止 | ArchUnit（アクセス） | ✅ | 要確認（低） | P1 |
| 10 | ApplicationService の `execute`/`query` は `Uni<...>` 返却 | ArchUnit | ✅ | — | P4（app層実装後） |

> #8: 元ルールは JPA エンティティ名を `Record` サフィックスに強制するが、ABService は `*Entity` を採用済みのため命名を**適応**する。

### 2.2 本文レベルルール（Checkstyle / PMD）

| # | ルール（意図） | Java強制手段 | 採否 | 既存違反 | フェーズ |
|---|---|---|---|---|---|
| 11 | domain で `try/catch` 禁止 | Checkstyle（`IllegalToken`/正規表現, domainスコープ） | ✅ | 要確認（少） | P2 |
| 12 | `@SuppressWarnings` に同行末尾の理由コメント必須（10文字以上） | Checkstyle（正規表現） | ✅ | 要確認 | P2 |
| 13 | 可変コレクション生成の禁止（`new ArrayList/HashMap/HashSet` 等） | Checkstyle `IllegalInstantiation` / PMD | 🔀 | あり（lib/infra除外要） | P2 |
| 14 | domain で可変（再代入可能）変数の禁止 | Checkstyle（`final` 強制の近似） | 🔀 | 要確認 | P5 |
| 15 | インライン FQN（本文中のパッケージ修飾型参照）禁止 | Checkstyle（正規表現の近似） | 🔀 | あり | P5 |

> #13/#14/#15 は Java では近似的にしか判定できず、除外スコープ（lib・infra・test）の設計が要る。

### 2.3 判断が要るルール（Kotlin イディオム由来・既存違反が広範）

| # | ルール（意図） | Java強制手段 | 採否 | 既存違反 | フェーズ |
|---|---|---|---|---|---|
| 16 | 論理演算子 `&&`/`\|\|`/`!` 禁止（宣言的 `and/or/not` へ） | Checkstyle `IllegalToken` + ヘルパlib | ✅ | **広範** | P5 |
| 17 | 値を返す `if` 禁止（値位置の if・値を返すガード節）→ `switch` 式へ | カスタム（Checkstyle 近似は困難） | ✅ | **広範** | P5 |
| 18 | 値を返さない位置での `switch`（`when`）禁止 | カスタム | ✅ | 要確認 | P5 |
| 19 | VO の `fromInput` にバリデーション必須（VO化尚早シグナル） | カスタム | ✅ | — | P5 |

> #16/#17 は本番・テスト双方に適用し**ハード強制**する（§4 決定）。既存の違反箇所は #16 用の論理ヘルパ（`and`/`or`/`not`）の導入とあわせて `switch` 式・ヘルパ呼び出しへ一括改修する。#17/#18/#19 は Checkstyle では表現しきれないため、カスタムチェック（AST ベース）の実装が要る。

### 2.4 テスト規約（ArchUnit / Checkstyle）

| # | ルール（意図） | Java強制手段 | 採否 | 既存違反 | フェーズ |
|---|---|---|---|---|---|
| 20 | `@Test` メソッドに `@DisplayName` 必須 | ArchUnit / Checkstyle | ✅ | あり（既存テスト） | P3 |
| 21 | JUnit アサーション禁止（AssertJ 統一） | ArchUnit（import 禁止） | ✅ | あり（既存テスト） | P3 |

> #20/#21 は既存テストの修正（移行）とセットで実施する。

### 2.5 Java 適用不可（N/A）

| # | ルール（意図） | 理由 |
|---|---|---|
| 22 | `EntityId` は value class であること | Java では `record`（EntityId は record 実装）で充足。Kotlin 固有構文 |
| 23 | domain で `!!`（not-null assertion）禁止 | Java に `!!` 演算子が無い（jspecify + `@NonNull` で代替） |
| 24 | テストメソッド名のバッククォート禁止 | Java にバッククォート識別子が無い |
| 25 | VO での `init { }` ブロック禁止 | Kotlin 固有。Java の record コンパクトコンストラクタは #19 と整合する形で別途扱う |

> 参考: 元の #25 は「VO の検証は `init` ではなく `fromInput` で行う」という設計意図。Java では record コンパクトコンストラクタ（内部生成用の例外throw）と `fromInput`（外部入力用の `Result`）の2系統として整理する（`RESULT_TYPE_GUIDE.md` 参照）。

（計: 実装済4 + 新規採用/適応候補17 + N/A 5 = 26）

---

## 3. フェーズ計画

| フェーズ | 内容 | 依存 | リスク |
|---|---|---|---|
| **P1** | ArchUnit 構造ルール前倒し（#5–#9）。現行コードへの適合を確認しつつ追加 | なし | 低 |
| **P2** | Checkstyle 本文レベル低リスク（#11 try-catch, #12 @Suppress理由, #13 可変コレクション） | 除外スコープ設計 | 中 |
| **P3** | テスト規約（#20 @DisplayName, #21 AssertJ統一）＋既存テスト移行 | 既存テスト改修 | 中 |
| **P4** | app層依存ルール（#10 ApplicationService の Uni 返却） | フェーズB（app層実装） | 低 |
| **P5** | 判断ルールの強制＋既存一括改修（#16 論理演算子ヘルパ導入と全置換 / #17–#19 カスタムASTチェック） | §4 決定 | 高（広範な既存改修） |
| **P6** | PMD + SpotBugs 再導入（Java25対応版）。両者の組み込みルールセットで新たに顕在化する既存違反も一括是正 | Java25動作確認 | 中〜高 |

> ※ #1–#25 の「26ルール」は本プロジェクト固有のアーキ/規約ルール。P6 で入れる PMD / SpotBugs の**組み込みルールセット**（errorprone/security/performance 等）はそれとは別系統で、追加の違反が出るため段階導入・除外設計を行う。

各フェーズは独立ブランチ・PR で進め、pre-commit フックと CI（`check`）で回帰を防ぐ。ArchUnit/Checkstyle/PMD/SpotBugs 設定変更は品質ゲート＝ポリシー変更のため、都度承認のうえ実施する。

---

## 4. 決定事項

1. **判断ルール（#16 論理演算子 / #17 値を返す if 等）の採用形態** → **ハード強制**。Java 適用可能なルールはイディオム系も含めすべて機械強制する。
2. **既存違反の扱い** → **一括改修**（baseline 先送りせず、ルール導入と同時に既存違反も是正）。実施はルール/レイヤー単位の PR に分割し、各 PR 内で完全準拠とする。
3. **PMD / SpotBugs 再導入** → **両方再導入する**。PMD 7.16+・SpotBugs 4.9.8+ が Java 25 に対応済み。
4. **`fromInput`（A-4）の扱い** → A-4 はマージ済み。現行 `fromInput` は #16/#17 に違反するため、P5 の一括改修で他の既存コードと同時に是正する（先行個別修正はしない）。

---

## 5. 参照

- ArchUnit 現況・基本ルール: `STATUS_AND_ROADMAP.md` §7
- エラー設計・VO の2系統生成: `RESULT_TYPE_GUIDE.md` / `STATUS_AND_ROADMAP.md` §2.2, §3
- コーディング規約全般: `CODING_GUIDELINES.md`
