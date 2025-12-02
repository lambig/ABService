# コーディングガイドライン

このドキュメントは、ABServiceプロジェクトにおけるコーディング規約と実装ガイドラインを定義します。

## 目次

1. [基本方針](#基本方針)
2. [命名規則](#命名規則)
3. [変数宣言規約](#変数宣言規約)
4. [ドメインモデル実装規約](#ドメインモデル実装規約)
5. [リアクティブプログラミング規約](#リアクティブプログラミング規約)
6. [CQRS実装規約](#cqrs実装規約)
7. [データベース関連規約](#データベース関連規約)
8. [コード品質](#コード品質)

---

## 基本方針

### 1. 型安全性の重視

Java 25の型システムを最大限活用し、コンパイル時に可能な限り多くのエラーを検出します。

#### ✅ 推奨する実装
- **値オブジェクト**: Java Records（不変性を保証）
- **エンティティ**: Lombok `@With(AccessLevel.PRIVATE)`（不変更新パターン）
- **ドメインID**: `EntityId<T>`インターフェース実装（型安全なID）

#### ❌ 避けるべき実装
- プリミティブ型の直接使用（ビジネス概念を表す場合）
- nullを許容する設計（Optional使用を検討）
- 可変なドメインオブジェクト

### 2. 不変性（Immutability）の原則

- **値オブジェクト**: 完全に不変
- **エンティティ**: 不変更新パターン（`@With`で新しいインスタンスを返す）
- **集約**: 整合性を保ちながら不変更新

### 3. 明示的な実装

リフレクションやマジックに頼らず、意図を明示的にコードで表現します。

### 4. ドメインモデルにおける日付・日時の型使用規約

ドメインモデル（集約、エンティティ、値オブジェクト）が持つ日付や日時のフィールドは、以下の値オブジェクトを使用します。

#### 使用する型

- **日付**: `BusinessDate` を使用
- **日時**: `BusinessDateTime` を使用

#### ✅ 推奨する実装

```java
// ✅ 推奨: BusinessDateを使用
public class Album implements Aggregate<Album, Album.Id> {
    private final BusinessDate releaseDate;
    
    public static Album create(AlbumTitle title, BusinessDate releaseDate, ...) {
        return new Album(Id.generate(), title, releaseDate, ...);
    }
}

// ✅ 推奨: BusinessDateTimeを使用
public class EventInfo implements ValueObject<EventInfo> {
    private final BusinessDateTime createdAt;
}
```

#### ❌ 避けるべき実装

```java
// ❌ 非推奨: LocalDateを直接使用
public class Album implements Aggregate<Album, Album.Id> {
    private final LocalDate releaseDate;  // BusinessDateを使うべき
}

// ❌ 非推奨: LocalDateTimeを直接使用
public class EventInfo implements ValueObject<EventInfo> {
    private final LocalDateTime createdAt;  // BusinessDateTimeを使うべき
}
```

#### 理由

- **ビジネス概念の明確化**: 単なる技術的な日付ではなく、ビジネスタイムゾーン（Asia/Tokyo）での日付であることを型で表現
- **型安全性の向上**: ドメイン層での日付操作を一貫した型で扱える
- **変換処理の一元化**: タイムゾーン処理やフォーマット処理を値オブジェクトに集約

#### 例外

- インフラ層（JPA Entity、DataSourceなど）では`LocalDate`/`LocalDateTime`/`Instant`の使用を許可
- アプリケーション層での変換処理（DTO ⇔ ドメインモデル間）では両方の型を扱う

---

## 命名規則

### パッケージ構造

```
com.abservice/
├── domain/                      # ドメイン層
│   ├── model/                   # ドメインモデル
│   │   ├── aggregate/           # 集約ルート
│   │   ├── entity/              # エンティティ
│   │   └── vo/                  # 値オブジェクト
│   ├── service/                 # ドメインサービス
│   ├── factory/                 # ファクトリ
│   ├── repository/              # リポジトリインターフェース
│   └── exception/               # ドメイン例外
├── application/                 # アプリケーション層
│   ├── service/                 # コマンドサービス
│   └── query/                   # クエリサービス
├── infrastructure/              # インフラ層
│   ├── persistence/             # 永続化実装
│   │   ├── entity/              # JPA Entity
│   │   ├── datasource/          # DataSource (Panache)
│   │   └── repository/          # Repository実装
│   └── datetime/                # 日時プロバイダー実装
└── presentation/                # プレゼンテーション層
    └── rest/                    # RESTエンドポイント
```

### ドメイン層

| 種類 | 命名規則 | 例 | パッケージ |
|------|---------|-----|-----------|
| Aggregate | `<概念名>` | `Album`, `Article`, `Event` | `domain.model.aggregate` |
| Aggregate ID | `Id`（ネストクラス） | `Album.Id`, `Article.Id` | 集約クラス内 |
| Entity | `<概念名>` | `Track`, `ArticleTag` | `domain.model.entity` |
| ValueObject | `<概念名>` | `AlbumTitle`, `TrackTitle` | `domain.model.vo` |
| Repository Interface | `<集約名>Repository` | `AlbumRepository`, `ArticleRepository` | `domain.repository.<集約名>` |
| DomainService Interface | `<概念><操作>Service` | `BusinessDateTimeProvider` | `domain.service` |
| Factory Interface | `<集約名>Factory` | `AlbumFactory` | `domain.factory` |
| DomainException | `<概念>Exception` | `AlbumNotFoundException` | `domain.exception` |

### インフラ層

| 種類 | 命名規則 | 例 |
|------|---------|-----|
| JPA Entity | `<概念名>Entity` | `AlbumEntity`, `ArticleEntity` |
| DataSource | `<概念名>DataSource` | `AlbumDataSource`, `ArticleDataSource` |
| Repository実装 | `<集約名>RepositoryImpl` | `AlbumRepositoryImpl` |
| Factory実装 | `<インターフェース名>Impl` | `AlbumFactoryImpl` |

**重要**: インフラ層では`Repository`という名前は使用しません。`DataSource`を使用します。
- **DataSource**: DAO/ActiveRecordパターン（技術的な永続化実装）
- **Repository**: 集約管理責務（ドメイン概念）

### アプリケーション層

| 種類 | 命名規則 | 例 |
|------|---------|-----|
| CommandService | `<動詞><概念>Service` | `CreateAlbumService`, `UpdateArticleService` |
| Input DTO | `<サービス名>Input` | `CreateAlbumInput`, `UpdateArticleInput` |
| Output DTO | `<サービス名>Output` | `CreateAlbumOutput`, `UpdateArticleOutput` |
| QueryService | `<動詞><概念>QueryService` | `FindAlbumsByLabelQueryService` |
| Query DTO | `<クエリ名>Query` | `FindAlbumsByLabelQuery` |
| Result DTO | `<クエリ名>Result` | `AlbumListResult`, `AlbumDetailResult` |

### プレゼンテーション層

| 種類 | 命名規則 | 例 |
|------|---------|-----|
| REST Resource | `<概念名>Resource` | `AlbumResource`, `ArticleResource` |
| Request DTO | `<操作>Request` | `CreateAlbumRequest`, `UpdateArticleRequest` |
| Response DTO | `<操作>Response` | `AlbumResponse`, `ArticleListResponse` |

### ファイル命名規則

- Javaファイル: `PascalCase.java`
- テストファイル: `<クラス名>Test.java`
- マイグレーション: `V<番号>__<説明>.sql` (例: `V1__Create_album_table.sql`)

---

## 変数宣言規約

### 基本原則: イミュータビリティの優先

可能な限り`final`を使用し、不変性を保ちます。

```java
// ✅ 推奨: final変数
public void process(Album album) {
    final var title = album.title();
    final var tracks = album.tracks();
    // ...
}

// ❌ 非推奨: 可変変数
public void process(Album album) {
    var title = album.title();  // 再代入の余地を残す
    title = new AlbumTitle("New Title");  // 混乱を招く
}
```

### varの使用

型が明確な場合のみ`var`を使用します。

```java
// ✅ 推奨: 型が明確
var album = albumRepository.findById(id);  // Uni<Album>が明確
var title = new AlbumTitle("example");     // AlbumTitleが明確

// ❌ 非推奨: 型が不明確
var result = process();  // 何が返るか不明確
var data = getData();    // 型推論が困難
```

---

## ドメインモデル実装規約

### コンストラクタアクセス制御とファクトリパターン

**原則**: ドメインオブジェクトのコンストラクタは`private`とし、生成は以下のパターンを使用します。

#### 1. Lombokファクトリ（単純な値の詰め込みのみ）

値オブジェクトなど、バリデーション以外の処理が不要な場合。

```java
/**
 * アルバムタイトル
 */
@AllArgsConstructor(staticName = "of", access = AccessLevel.PRIVATE)
public record AlbumTitle(String value) implements ValueObject<AlbumTitle> {
    public AlbumTitle {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("アルバムタイトルは必須です");
        }
        if (value.length() > 200) {
            throw new IllegalArgumentException("アルバムタイトルは200文字以内である必要があります");
        }
    }

    @Override
    public boolean equivalentTo(AlbumTitle other) {
        return this.equals(other);
    }
}

// 使用例
var title = AlbumTitle.of("My Album");
```

#### 2. Static Factory Method（簡単な変換処理を含む）

型変換や簡単な加工が必要な場合。

```java
@Getter
@With(AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Track implements DomainEntity<Track, Track.Id> {
    private final Id id;
    private final TrackTitle title;
    private final Duration duration;

    /**
     * Static factory method - 新規生成
     */
    public static Track create(TrackTitle title, Duration duration) {
        return new Track(Id.generate(), title, duration);
    }

    /**
     * Static factory method - 再構成（永続化層からの復元用）
     */
    public static Track reconstruct(Id id, TrackTitle title, Duration duration) {
        return new Track(id, title, duration);
    }

    /**
     * タイトルを更新（新しいインスタンスを返す）
     */
    public Track updateTitle(TrackTitle newTitle) {
        return this.withTitle(newTitle);
    }

    @Override
    public boolean equivalentTo(Track other) {
        return this.id.equals(other.id);
    }

    /**
     * Track ID
     */
    public record Id(String value) implements EntityId<Track> {
        public Id {
            if (!EntityId.isValidUuid(value)) {
                throw new IllegalArgumentException("Invalid Track ID format: " + value);
            }
        }

        public static Id of(String value) {
            return new Id(value);
        }

        public static Id generate() {
            return new Id(EntityId.generateUuidV7());
        }
    }
}
```

#### 3. Factoryクラス（複雑な生成ロジック）

外部依存（リポジトリ、ドメインサービス）が必要な場合や、複雑なバリデーション・初期化が必要な場合。

```java
// ファクトリインターフェース
public interface AlbumFactory extends Factory<Album, AlbumFactory.CreateParams> {

    /**
     * 新規アルバム生成
     */
    Uni<Album> create(CreateParams params);

    /**
     * 永続化層からの再構成
     */
    Album reconstruct(ReconstructParams params);

    /**
     * 生成パラメータ
     */
    record CreateParams(
        AlbumTitle title,
        LocalDate releaseDate,
        ArtistCredit artistCredit,
        EventInfo eventInfo,
        CatalogNumber catalogNumber
    ) implements Factory.Params {}

    /**
     * 再構成パラメータ
     */
    record ReconstructParams(
        Album.Id id,
        AlbumTitle title,
        LocalDate releaseDate,
        ArtistCredit artistCredit,
        EventInfo eventInfo,
        CatalogNumber catalogNumber,
        List<Track> tracks
    ) implements Factory.Params {}
}

// ファクトリ実装
@ApplicationScoped
public class AlbumFactoryImpl implements AlbumFactory {

    private final AlbumRepository albumRepository;

    public AlbumFactoryImpl(AlbumRepository albumRepository) {
        this.albumRepository = albumRepository;
    }

    @Override
    public Uni<Album> create(CreateParams params) {
        // 複雑なバリデーション
        return validateCatalogNumber(params.catalogNumber())
            .map(valid -> new Album(
                Album.Id.generate(),
                params.title(),
                params.releaseDate(),
                params.artistCredit(),
                params.eventInfo(),
                params.catalogNumber(),
                Collections.emptyList()
            ));
    }

    @Override
    public Album reconstruct(ReconstructParams params) {
        // 永続化データから再構成（バリデーション不要）
        return new Album(
            params.id(),
            params.title(),
            params.releaseDate(),
            params.artistCredit(),
            params.eventInfo(),
            params.catalogNumber(),
            params.tracks()
        );
    }

    private Uni<Boolean> validateCatalogNumber(CatalogNumber catalogNumber) {
        if (catalogNumber == null) {
            return Uni.createFrom().item(true);
        }
        return albumRepository.findByCatalogNumber(catalogNumber)
            .onItem().transform(existing -> {
                if (existing != null) {
                    throw new IllegalArgumentException("カタログ番号が重複しています: " + catalogNumber.value());
                }
                return true;
            });
    }
}
```

### 値オブジェクト（Value Object）

Java Recordsを使用します。コンストラクタは`private`（または暗黙的にpackage-private）。

```java
/**
 * アルバムタイトル
 */
public record AlbumTitle(String value) implements ValueObject<AlbumTitle> {
    public AlbumTitle {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("アルバムタイトルは必須です");
        }
        if (value.length() > 200) {
            throw new IllegalArgumentException("アルバムタイトルは200文字以内である必要があります");
        }
    }

    // Static factory method
    public static AlbumTitle of(String value) {
        return new AlbumTitle(value);
    }

    @Override
    public boolean equivalentTo(AlbumTitle other) {
        return this.equals(other);
    }
}
```

### エンティティ（Entity）

Lombok `@With(AccessLevel.PRIVATE)`を使用した不変更新パターン。コンストラクタは`private`。

```java
@Getter
@With(AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Track implements DomainEntity<Track, Track.Id> {
    private final Id id;
    private final TrackTitle title;
    private final Duration duration;

    /**
     * 新規生成
     */
    public static Track create(TrackTitle title, Duration duration) {
        return new Track(Id.generate(), title, duration);
    }

    /**
     * 再構成（永続化層から）
     */
    public static Track reconstruct(Id id, TrackTitle title, Duration duration) {
        return new Track(id, title, duration);
    }

    /**
     * タイトルを更新（新しいインスタンスを返す）
     */
    public Track updateTitle(TrackTitle newTitle) {
        return this.withTitle(newTitle);
    }

    @Override
    public boolean equivalentTo(Track other) {
        return this.id.equals(other.id);
    }

    /**
     * Track ID
     */
    public record Id(String value) implements EntityId<Track> {
        public Id {
            if (!EntityId.isValidUuid(value)) {
                throw new IllegalArgumentException("Invalid Track ID format: " + value);
            }
        }

        public static Id of(String value) {
            return new Id(value);
        }

        public static Id generate() {
            return new Id(EntityId.generateUuidV7());
        }
    }
}
```

### 集約（Aggregate）

整合性境界を明確にし、集約内の整合性を保護します。コンストラクタは`private`。

```java
@Getter
@With(AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Album implements Aggregate<Album, Album.Id> {
    private final Id id;
    private final AlbumTitle title;
    private final List<Track> tracks;

    /**
     * トラックを追加（整合性を保ちながら新しいインスタンスを返す）
     */
    public Album addTrack(Track track) {
        final var newTracks = new ArrayList<>(this.tracks);
        newTracks.add(track);
        return this.withTracks(List.copyOf(newTracks));
    }

    /**
     * タイトルを更新
     */
    public Album updateTitle(AlbumTitle newTitle) {
        return this.withTitle(newTitle);
    }

    // ... その他のビジネスロジック

    /**
     * Album ID
     */
    public record Id(String value) implements EntityId<Album> {
        public Id {
            if (!EntityId.isValidUuid(value)) {
                throw new IllegalArgumentException("Invalid Album ID format: " + value);
            }
        }

        public static Id of(String value) {
            return new Id(value);
        }

        public static Id generate() {
            return new Id(EntityId.generateUuidV7());
        }
    }
}
```

### ファクトリパターン選択ガイド

| 状況 | 推奨パターン | 理由 |
|------|------------|------|
| 単純な値の詰め込みのみ | Lombokファクトリ (`@AllArgsConstructor(staticName = "of")`) | シンプルで明確 |
| 型変換や簡単な加工が必要 | Static Factory Method | クラス内で完結する |
| 外部依存が必要（Repository等） | Factoryクラス | 依存性注入可能 |
| 複雑なバリデーション | Factoryクラス | 責任分離 |
| 複数の生成バリエーション | Factoryクラス | 意図が明確 |
| 永続化層からの再構成 | Static Factory Method または Factoryクラスの`reconstruct()` | 用途が明確 |

---

## リアクティブプログラミング規約

### 基本方針

- **ドメインモデル**: 同期処理（ビジネスロジック）
- **Services/Repositories**: 非同期処理（Mutiny）

### Mutiny使用規約

```java
@ApplicationScoped
public class CreateAlbumService implements CommandService<CreateAlbumInput, CreateAlbumOutput> {
    private final AlbumRepository albumRepository;
    private final AlbumFactory albumFactory;

    @WithTransaction
    @Override
    public Uni<CreateAlbumOutput> execute(CreateAlbumInput input) {
        return Uni.createFrom().item(() -> albumFactory.create(input))
            .flatMap(album -> albumRepository.save(album))
            .onItem().transform(album -> new CreateAlbumOutput(
                album.id().value(),
                album.title().value()
            ));
    }
}
```

**ルール**:
- CommandService: `execute()` は `Uni<O>` を返す
- QueryService: `query()` は `Uni<R>` を返す
- Repository: すべてのメソッドは `Uni<T>` または `Uni<List<T>>` を返す
- 同期→非同期: `Uni.createFrom().item(() -> ...)` で包む
- 非同期チェーン: `flatMap()` で連鎖
- 同期変換: `onItem().transform()` で変換
- エラー処理: `onItem().ifNull().failWith()` または `onFailure().recoverWithItem()`

### トランザクション

```java
// ✅ 推奨: @WithTransaction（Reactive）
@WithTransaction
@Override
public Uni<Output> execute(Input input) {
    // リアクティブトランザクション
}

// ❌ 非推奨: @Transactional（Blocking）
// Mutinyと互換性なし
```

---

## CQRS実装規約

### CommandService（更新系）

```java
@ApplicationScoped
public class UpdateAlbumTitleService
    implements CommandService<UpdateAlbumTitleInput, UpdateAlbumTitleOutput> {

    private final AlbumRepository albumRepository;

    @WithTransaction
    @Override
    public Uni<UpdateAlbumTitleOutput> execute(UpdateAlbumTitleInput input) {
        return albumRepository.findById(Album.Id.of(input.albumId()))
            .onItem().ifNull().failWith(() -> new AlbumNotFoundException(input.albumId()))
            .onItem().transform(album -> album.updateTitle(new AlbumTitle(input.newTitle())))
            .flatMap(album -> albumRepository.save(album))
            .onItem().transform(album -> new UpdateAlbumTitleOutput(
                album.id().value(),
                album.title().value()
            ));
    }
}
```

### QueryService（照会系）

```java
@ApplicationScoped
public class AlbumQueryService
    implements QueryService<FindAlbumsByLabelQuery, AlbumListResult> {

    private final AlbumDataSource albumDataSource;

    @Override
    public Uni<AlbumListResult> query(FindAlbumsByLabelQuery query) {
        return albumDataSource.findByLabel(query.labelName())
            .onItem().transform(entities -> new AlbumListResult(
                entities.stream()
                    .map(this::toDto)
                    .toList()
            ));
    }

    private AlbumDto toDto(AlbumEntity entity) {
        return new AlbumDto(entity.getId(), entity.getTitle());
    }
}
```

### 原則

1. **更新はCommandService**: Repositoryを使用してドメインモデルを操作
2. **照会はQueryService**: DataSourceを使用してReadModelを取得
3. **責任分離**: 一つのサービスは一つのユースケース
4. **DTO変換**: ドメインオブジェクトを直接公開しない

---

## データベース関連規約

### 共通監査列

すべてのテーブルは以下の7つの監査列を含む必要があります：

1. `created_at` - レコード作成日時
2. `updated_at` - レコード最終更新日時
3. `created_by_service` - 作成時のサービス名
4. `updated_by_service` - 更新時のサービス名
5. `created_by_user` - 作成者ユーザーID
6. `updated_by_user` - 更新者ユーザーID
7. `version` - 楽観ロック用バージョン番号

詳細は [AUDIT_COLUMNS.md](AUDIT_COLUMNS.md) を参照してください。

### ID設計

- **ドメインID**: UUIDv7形式の文字列（`EntityId<T>`）
- **DB内部ID**: Long型（`@Id @GeneratedValue`）
- **分離の理由**: ドメイン層とインフラ層の独立性

詳細は [ID_DESIGN_POLICY.md](ID_DESIGN_POLICY.md) を参照してください。

### Flyway Migration

```sql
-- V1__Create_album_table.sql
CREATE TABLE album (
    -- DB内部ID（主キー）
    id BIGSERIAL PRIMARY KEY,

    -- ドメインID（ビジネスキー）
    album_id VARCHAR(36) NOT NULL UNIQUE,

    -- ビジネスデータ
    title VARCHAR(200) NOT NULL,

    -- 共通監査列
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_service VARCHAR(100) NOT NULL,
    updated_by_service VARCHAR(100) NOT NULL,
    created_by_user VARCHAR(100),
    updated_by_user VARCHAR(100),
    version INTEGER NOT NULL DEFAULT 0
);

-- インデックス
CREATE INDEX idx_album_album_id ON album(album_id);
CREATE INDEX idx_album_title ON album(title);
```

---

## コード品質

### Checkstyle

Java標準スタイルガイドに準拠：

```bash
# チェック実行
./gradlew checkstyleMain checkstyleTest

# ビルドに統合済み
./gradlew build  # checkstyleが自動実行される
```

### Spotless

コードフォーマッター：

```bash
# フォーマットチェック
./gradlew spotlessCheck

# 自動フォーマット
./gradlew spotlessApply
```

### 統合チェック

すべてのコード品質チェックとテストを実行:

```bash
./gradlew check
```

これは以下を実行します:
- checkstyleMain
- checkstyleTest
- spotlessCheck
- test

---

## 参考資料

- [アーキテクチャ設計書](ARCHITECTURE.md)
- [ドメインモデル設計](DOMAIN_MODEL_DESIGN.md)
- [共通監査列ガイドライン](AUDIT_COLUMNS.md)
- [ID設計ポリシー](ID_DESIGN_POLICY.md)
- [リポジトリ実装ガイド](REPOSITORY_IMPLEMENTATION.md)
