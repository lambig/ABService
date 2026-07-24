# ID設計ポリシー

## 1. 概要

本ドキュメントは、ABServiceプロジェクトにおけるID設計の方針を定義します。

### 1.1 背景

ドメイン駆動設計を実践する際、ドメイン層のIDとインフラ層（DB）のIDを明確に分離することが重要です。この分離により、以下のメリットが得られます：

1. **ドメイン層の独立性**: インフラ層の技術的制約からドメイン層を解放
2. **外部システム連携の容易性**: ドメインIDは外部APIやマイクロサービス間で一貫して使用可能
3. **テスタビリティ**: DB自動採番に依存しないテストが可能
4. **分散システム対応**: グローバルに一意なIDを事前生成可能

### 1.2 解決方針

**アプリケーションレベルID（ドメインID）**と**インフラレベルID（DB内部ID）**を明確に分離し、それぞれ異なる目的で使用します。

## 2. ID設計の基本方針

### 2.1 2種類のIDの定義

#### アプリケーションレベルID（ドメインID）

- **目的**: ドメインモデルの識別、ビジネスロジックでの使用、外部システムとの連携
- **形式**: UUIDv7（時系列ソート可能なUUID）
- **生成タイミング**: Factory層でのエンティティ生成時、またはドメイン層での新規ID生成時
- **スコープ**: ドメイン層全体、API、外部システム
- **型**: `String`（36文字、ハイフン付きUUID形式）

**UUIDv7を選択する理由:**
- ✅ 時系列でソート可能（タイムスタンプベース）
- ✅ グローバルに一意
- ✅ 分散システムで安全に生成可能
- ✅ 外部システムとのID連携が容易
- ✅ UUIDv4より性能が良い（インデックス効率）
- ✅ データベース主キーに依存せず事前生成可能

#### インフラレベルID（DB内部ID）

- **目的**: DB内部での行識別、外部キー（FK）関係の管理、JOINの最適化
- **形式**: Long（64bit整数）
- **生成タイミング**: DB永続化時（`@GeneratedValue(strategy = GenerationType.IDENTITY)`）
- **スコープ**: インフラ層（永続化層）のみ
- **型**: `Long`（JPA Entityの`@Id`フィールド）

### 2.2 ID使用の原則

| 観点 | アプリケーションレベルID | インフラレベルID |
|------|------------------------|-----------------|
| ドメイン層での使用 | ✅ 使用する | ❌ 使用しない |
| API公開 | ✅ 公開する | ❌ 公開しない |
| 外部システム連携 | ✅ 使用する | ❌ 使用しない |
| DB内部FK | ❌ 使用しない | ✅ 使用する |
| DB JOIN | ❌ 使用しない | ✅ 使用する |
| インデックス | ✅ UNIQUE制約 | ✅ PRIMARY KEY |

## 3. 実装設計

### 3.1 ドメイン層

#### EntityId<T>インターフェース

```java
public interface EntityId<T extends DomainObject<T>> extends Comparable<EntityId<T>> {
    /**
     * IDの実際の値（UUIDv7形式の文字列）
     */
    String value();

    /**
     * デフォルト実装：値による比較
     */
    @Override
    default int compareTo(EntityId<T> other) {
        return this.value().compareTo(other.value());
    }

    /**
     * UUID v7を生成する
     */
    static String generateUuidV7() {
        return Generators.timeBasedEpochGenerator().generate().toString();
    }

    /**
     * 文字列がUUID形式かどうかを検証する
     */
    static boolean isValidUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
```

#### Album.Idの実装例

```java
public class Album implements Aggregate<Album, Album.Id> {
    private final Id id;
    private final AlbumTitle title;
    // ...

    /**
     * Album ID（ドメインID）
     */
    public record Id(String value) implements EntityId<Album> {
        public Id {
            if (!EntityId.isValidUuid(value)) {
                throw new IllegalArgumentException("Invalid Album ID format: " + value);
            }
        }

        /**
         * 既存のUUID文字列からIDを生成
         */
        public static Id of(String value) {
            return new Id(value);
        }

        /**
         * 新しいUUIDv7を生成してIDを作成
         */
        public static Id generate() {
            return new Id(EntityId.generateUuidV7());
        }
    }
}
```

#### Factoryでの使用例

```java
@ApplicationScoped
public class AlbumFactoryImpl implements AlbumFactory {
    @Override
    public Album create(CreateAlbumData data) {
        return Album.create(
            Album.Id.generate(),  // ✅ UUIDv7を生成
            new AlbumTitle(data.title()),
            // ...
        );
    }
}
```

### 3.2 インフラ層

#### AlbumTableRecordの実装例

```java
@Entity
@Table(name = "album")
public class AlbumTableRecord extends AuditableTableRecord {
    /**
     * DB内部ID（主キー）
     * インフラ層でのみ使用
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ドメインID（ビジネスキー）
     * API公開用、ドメイン層との連携用
     */
    @Column(name = "album_id", nullable = false, unique = true, length = 36)
    private String albumId;

    /**
     * ビジネスデータ
     */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    // Getter/Setter...
}
```

#### DataSourceの実装例

```java
@ApplicationScoped
public class AlbumDataSource implements PanacheRepositoryBase<AlbumTableRecord, Long> {
    /**
     * ドメインIDで検索
     */
    public Uni<AlbumTableRecord> findByAlbumId(String albumId) {
        return find("albumId", albumId).firstResult();
    }

    /**
     * タイトルで検索
     */
    public Uni<List<AlbumTableRecord>> findByTitle(String title) {
        return find("title", title).list();
    }
}
```

#### RepositoryImplの実装例

```java
@ApplicationScoped
public class AlbumRepositoryImpl implements AlbumRepository {
    private final AlbumDataSource albumDataSource;
    private final AlbumMapper albumMapper;

    @Override
    public Uni<Album> findById(Album.Id id) {
        return albumDataSource.findByAlbumId(id.value())
            .onItem().transform(entity ->
                entity != null ? albumMapper.toDomain(entity) : null
            );
    }

    @Override
    public Uni<Album> save(Album album) {
        return albumDataSource.findByAlbumId(album.id().value())
            .onItem().ifNull().continueWith(() -> {
                // 新規作成
                var entity = new AlbumTableRecord();
                entity.setAlbumId(album.id().value());
                return entity;
            })
            .onItem().transform(entity -> {
                // ドメインモデルからエンティティへマッピング
                albumMapper.updateEntity(entity, album);
                return entity;
            })
            .flatMap(entity -> albumDataSource.persistAndFlush(entity))
            .onItem().transform(albumMapper::toDomain);
    }
}
```

### 3.3 データベーススキーマ

```sql
CREATE TABLE album (
    -- DB内部ID（主キー）
    -- インフラ層でのFK参照、JOINの最適化に使用
    id BIGSERIAL PRIMARY KEY,

    -- ドメインID（ビジネスキー）
    -- API公開、ドメイン層との連携に使用
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
CREATE UNIQUE INDEX idx_album_album_id ON album(album_id);
CREATE INDEX idx_album_title ON album(title);
```

#### 外部キーの例

```sql
CREATE TABLE track (
    id BIGSERIAL PRIMARY KEY,
    track_id VARCHAR(36) NOT NULL UNIQUE,

    -- FK: DB内部IDを使用（インフラ層での最適化）
    album_db_id BIGINT NOT NULL REFERENCES album(id),

    title VARCHAR(200) NOT NULL,
    duration_seconds INTEGER,

    -- 共通監査列
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_service VARCHAR(100) NOT NULL,
    updated_by_service VARCHAR(100) NOT NULL,
    created_by_user VARCHAR(100),
    updated_by_user VARCHAR(100),
    version INTEGER NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_track_track_id ON track(track_id);
CREATE INDEX idx_track_album_db_id ON track(album_db_id);
```

## 4. ID変換フロー

### 4.1 作成フロー

```
1. Factory層
   Album.Id.generate() → "550e8400-e29b-41d4-a716-446655440000" (UUIDv7)

2. Repository層（save）
   Domain ID → AlbumTableRecord.albumId = "550e8400-e29b-41d4-a716-446655440000"

3. DB永続化
   INSERT INTO album (album_id, ...) VALUES ('550e8400-...', ...)
   RETURNING id → 1 (DB内部IDが自動生成される)
```

### 4.2 取得フロー

```
1. Repository層（findById）
   Album.Id("550e8400-...") → SQL: WHERE album_id = '550e8400-...'

2. DB検索
   SELECT * FROM album WHERE album_id = '550e8400-...'
   → id=1, album_id='550e8400-...', title='Example'

3. Mapper
   AlbumTableRecord → Album（ドメインモデル）
   DB内部ID（1）は破棄、ドメインID（'550e8400-...'）のみ使用
```

## 5. API設計

### 5.1 REST APIでのID使用

```java
@Path("/albums")
@ApplicationScoped
public class AlbumResource {
    private final AlbumQueryService albumQueryService;
    private final CreateAlbumService createAlbumService;

    /**
     * アルバム取得
     * ✅ ドメインIDを使用
     */
    @GET
    @Path("/{albumId}")
    public Uni<AlbumResponse> getAlbum(@PathParam("albumId") String albumId) {
        return albumQueryService.query(new FindAlbumByIdQuery(albumId))
            .onItem().transform(result -> new AlbumResponse(
                result.albumId(),
                result.title()
            ));
    }

    /**
     * アルバム作成
     * ✅ ドメインIDを返す
     */
    @POST
    public Uni<AlbumResponse> createAlbum(CreateAlbumRequest request) {
        return createAlbumService.execute(new CreateAlbumInput(request.title()))
            .onItem().transform(output -> new AlbumResponse(
                output.albumId(),  // UUIDv7形式
                output.title()
            ));
    }
}
```

### 5.2 API レスポンス例

```json
{
  "albumId": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Example Album",
  "createdAt": "2025-12-02T10:30:00Z"
}
```

**注意**: DB内部ID（1, 2, 3...）は外部に公開しません。

## 6. ベストプラクティス

### ✅ 推奨する設計

1. **ドメイン層**: ドメインID（UUIDv7）のみ使用
2. **API**: ドメインIDのみ公開
3. **Factory**: ID生成は`Album.Id.generate()`
4. **Repository**: ドメインIDで検索・保存
5. **DataSource**: DB内部IDとドメインIDの両方を管理
6. **DB FK**: DB内部IDを使用（パフォーマンス最適化）

### ❌ 避けるべき設計

1. **ドメイン層でDB内部IDを使用**: `Album.Id(1L)` ← NG
2. **APIでDB内部IDを公開**: `/albums/1` ← NG（`/albums/{uuid}`を使用）
3. **Factory層でID=0やnullを使用**: センチネル値による不自然な設計
4. **外部システム連携でDB内部IDを使用**: 他システムに依存関係が生じる

## 7. テスト戦略

### 7.1 ユニットテスト（ドメイン層）

```java
@Test
void testAlbumCreation() {
    // ✅ ドメインIDを使用
    var albumId = Album.Id.generate();
    var album = Album.create(
        albumId,
        new AlbumTitle("Test Album")
    );

    assertEquals(albumId, album.id());
    assertTrue(EntityId.isValidUuid(album.id().value()));
}
```

### 7.2 統合テスト（Repository層）

```java
@Test
@TestTransaction
void testSaveAndFindById() {
    // Given
    var album = Album.create(
        Album.Id.generate(),
        new AlbumTitle("Integration Test Album")
    );

    // When: 保存
    var savedAlbum = albumRepository.save(album).await().indefinitely();

    // Then: ドメインIDで検索可能
    var foundAlbum = albumRepository.findById(savedAlbum.id())
        .await().indefinitely();

    assertEquals(savedAlbum.id(), foundAlbum.id());
    assertEquals(savedAlbum.title(), foundAlbum.title());
}
```

## 8. マイグレーション時の注意点

既存のLong型IDからUUIDv7へ移行する場合：

1. **新カラム追加**: `album_id VARCHAR(36)` を追加
2. **既存データにUUID生成**: UPDATE文で既存レコードにUUIDを付与
3. **アプリケーション更新**: ドメイン層をUUIDベースに変更
4. **段階的移行**: 両方のIDを一時的にサポート
5. **旧ID削除**: 完全移行後、Long型IDカラムを削除（オプション）

## 9. 参考資料

- [UUIDv7 Specification (Draft)](https://datatracker.ietf.org/doc/html/draft-peabody-dispatch-new-uuid-format)
- [ドメイン駆動設計: エンティティと値オブジェクト](https://www.domainlanguage.com/ddd/)
- [アーキテクチャ設計書](ARCHITECTURE.md)
- [コーディングガイドライン](CODING_GUIDELINES.md)
