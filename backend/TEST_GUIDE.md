# テスト分離ガイド

## ディレクトリ構成

ユニットテストは `backend/src/test/java/`、統合テストは `backend/src/integrationTest/java/` に置き、本番コードと同じパッケージ構成をたどる。Gradle のソースセットが分かれているため、実行対象も分かれる。

## テスト分類ルール

### ユニットテスト (`src/test/java/`)

**特徴:**
- データベース不要
- 高速実行（数秒）
- 頻繁に実行
- モック/スタブ使用

**対象:**
- Value Object (VO)
- Domain Entity
- Domain Service（ロジック部分）
- Aggregate（ビジネスロジック）
- ユーティリティクラス

**アノテーション:**
- `@QuarkusTest` **使用しない**
- JUnit標準アノテーションのみ (`@Test`, `@BeforeEach`, etc.)

**アサーションライブラリ:**
- **AssertJ** を使用
- `assertThat()` による流暢なアサーション
- 例: `assertThat(result).isNotNull().hasSize(2);`

**命名規則:**
- `*Test.java`

### 統合テスト (`src/integrationTest/java/`)

**特徴:**
- データベース必要
- 実行時間長め（数十秒〜数分）
- 低頻度実行（マージ前、CI）
- 実際のコンポーネント使用

**対象:**
- Repository実装（DB操作）
- REST APIエンドポイント
- トランザクション境界
- 外部システム連携

**アノテーション:**
- `@QuarkusTest` 必須
- `@RunOnVertxContext` リアクティブなDB操作に必須
- `@TestTransaction` (必要に応じて、ただしReactiveでは非推奨)

**アサーション方法:**
- **UniAsserter** を使用（Quarkus/Mutiny標準）
- `@RunOnVertxContext`と組み合わせてVertxコンテキスト内で実行
- AssertJは使用せず、`UniAsserter`のメソッドを使用:
  - `asserter.assertEquals(expected, actual)`
  - `asserter.assertNotNull(value)`
  - `asserter.assertTrue(condition)`
  - `asserter.assertThat(() -> uniOperation, result -> { ... })`
  - `asserter.assertFailedWith(() -> uniOperation, ExceptionClass.class)`

**命名規則:**
- `*IntegrationTest.java`
- `*RepositoryTest.java`

## 実行コマンド

### ユニットテストのみ（高速・頻回実行）
```bash
./gradlew test
```

### 統合テストのみ（DB必要・低頻度）
```bash
./gradlew integrationTest
```

### 全テスト実行（CI用）
```bash
./gradlew check
```

### 継続的ユニットテスト実行（開発時）
```bash
./gradlew test --continuous
```

## VS Code タスク

- `gradle:test` - ユニットテストのみ
- `gradle:integrationTest` - 統合テストのみ
- `gradle:allTests` - 全テスト実行

## 統合テスト実行前の準備

統合テストは PostgreSQL と、アセットのアップロードを検証するため MinIO（S3互換）を使う。リポジトリルートで以下を実行する:

```bash
docker compose up -d postgres
docker compose up -d minio minio-init
```

マイグレーションは Quarkus の `migrate-at-start` により統合テスト起動時に自動適用される（別タスクの実行は不要）。各サービスの接続情報は `docker/README.md` が正。

## テスト追加ガイドライン

### 新規テスト作成時の判断基準

以下の質問で判断:

1. **データベースアクセスが必要か？**
   - YES → `integrationTest`
   - NO → 次へ

2. **外部システムへの接続が必要か？**
   - YES → `integrationTest`
   - NO → 次へ

3. **`@QuarkusTest`が必要か？**
   - YES → `integrationTest`
   - NO → `test`（ユニット）

4. **純粋なビジネスロジックのテストか？**
   - YES → `test`（ユニット）
   - NO → 再検討

### 参照する実例

現行のテストを参照する。ユニットテストは `domain/model/vo` 配下（VOの検証）と `application/service` 配下（Fake注入によるユースケース検証）、統合テストは `infrastructure/persistence/repository`（`UniAsserter` を使ったDB操作）と `presentation/rest`（RestAssured による E2E）が代表例。

## CI

`.github/workflows/ci.yml` が `check`（spotless / checkstyle / PMD / ユニット+ArchUnit / 統合）を実行する。定義の正はワークフロー本体。
