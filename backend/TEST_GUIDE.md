# テスト分離ガイド

## ディレクトリ構成

```
backend/src/
├── main/java/              # 本番コード
├── test/java/              # ユニットテスト（DBなし・高速実行）
│   └── com/abservice/
│       └── domain/
│           └── model/
│               └── vo/
│                   ├── BusinessDateTest.java
│                   └── BusinessDateTimeTest.java
└── integrationTest/java/   # 統合テスト（DB必要・低頻度実行）
    └── com/abservice/
        └── infrastructure/
            └── datetime/
                └── SystemBusinessDateTimeProviderTest.java
```

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
- `@TestTransaction` (必要に応じて)

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

統合テストはデータベースが必要なため、以下を実行:

```bash
# Dockerコンテナ起動
docker-compose up -d

# または開発環境用
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d

# マイグレーション実行
./gradlew flywayMigrate
```

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

### 例

#### ユニットテストの例
```java
// src/test/java/com/abservice/domain/model/vo/AlbumTitleTest.java
package com.abservice.domain.model.vo.album;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AlbumTitleTest {
    @Test
    void testValidTitle() {
        var title = new AlbumTitle("Test Album");
        assertThat(title.value()).isEqualTo("Test Album");
    }

    @Test
    void testInvalidTitle() {
        assertThatThrownBy(() -> new AlbumTitle(""))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

#### 統合テストの例
```java
// src/integrationTest/java/com/abservice/infrastructure/persistence/repository/AlbumRepositoryIntegrationTest.java
package com.abservice.infrastructure.persistence.repository;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.repository.album.AlbumRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@QuarkusTest
class AlbumRepositoryIntegrationTest {

    @Inject
    AlbumRepository albumRepository;

    @Test
    void testSaveAndFind() {
        var album = Album.create(
            Album.Id.generate(),
            new AlbumTitle("Integration Test")
        );

        var saved = albumRepository.save(album).await().indefinitely();
        var found = albumRepository.findById(saved.id()).await().indefinitely();

        assertThat(found).isPresent();
        assertThat(found.get().title()).isEqualTo(album.title());
    }
}
```

## CI/CD設定例

```yaml
# .github/workflows/test.yml
jobs:
  unit-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run unit tests
        run: ./gradlew test

  integration-test:
    runs-on: ubuntu-latest
    needs: unit-test
    steps:
      - uses: actions/checkout@v3
      - name: Start services
        run: docker-compose up -d
      - name: Run integration tests
        run: ./gradlew integrationTest
```
