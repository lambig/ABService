# リポジトリ実装

## 概要

集約ルートに対してのみリポジトリを提供する実装を行いました。

## 実装内容

### 1. 基底リポジトリインターフェース

**ファイル**: `domain/repository/Repository.java`

- **型制約**: `Repository<T extends Aggregate<T, ID>, ID>`
  - 集約ルート(`Aggregate`)に対してのみリポジトリを作成可能
  - 集約内エンティティに対するリポジトリ作成を型レベルで禁止
- **リアクティブAPI**: すべてのメソッドが`Uni<T>`を返却
- **基本CRUD操作**:
  - `save(T)`, `saveAll(Iterable<T>)`
  - `findById(ID)`, `findAllById(Iterable<ID>)`, `findAll()`
  - `delete(T)`, `deleteAll(Iterable<T>)`
  - `deleteById(ID)`, `deleteAllById(Iterable<ID>)`
  - `existsById(ID)`, `count()`

### 2. 集約ルート別リポジトリ

以下の7つの集約ルートに対してリポジトリインターフェースを作成しました:

#### AlbumRepository
- **パッケージ**: `domain.repository.album`
- **カスタムメソッド**:
  - `findByTitle(AlbumTitle)`: アルバムタイトルで検索
  - `findByArtistCreditId(ArtistCredit.Id)`: アーティストで検索
  - `findByEventId(Event.Id)`: イベントで検索
  - `findByCatalogNumber(CatalogNumber)`: カタログ番号で検索
  - `findByReleaseYear(int)`: リリース年で検索

#### TuneRepository
- **パッケージ**: `domain.repository.tune`
- **カスタムメソッド**:
  - `findByTitle(TuneTitle)`: タイトルで検索
  - `findByTuneKind(TuneKind)`: 種別で検索
  - `findByTuneType(String)`: タイプ（リール、ジグなど）で検索
  - `findByDefaultKey(String)`: デフォルトキーで検索

#### ArticleRepository
- **パッケージ**: `domain.repository.article`
- **カスタムメソッド**:
  - `findByArticleType(ArticleType)`: 記事タイプで検索
  - `findByAlbumId(Album.Id)`: アルバムIDで検索
  - `findByPublicFlag(boolean)`: 公開フラグで検索
  - `findByPublishedAtBetween(LocalDateTime, LocalDateTime)`: 公開日範囲で検索
  - `findByTitleContaining(String)`: タイトル部分一致検索

#### EventRepository
- **パッケージ**: `domain.repository.event`
- **カスタムメソッド**:
  - `findByName(EventName)`: イベント名で検索
  - `findByDate(LocalDate)`: 開催日で検索
  - `findByDateBetween(LocalDate, LocalDate)`: 開催日範囲で検索
  - `findByPlaceContaining(String)`: 会場部分一致検索
  - `findByYear(int)`: 年で検索

#### ArtistCreditRepository
- **パッケージ**: `domain.repository.artistcredit`
- **カスタムメソッド**:
  - `findByDisplayName(ArtistCreditName)`: 表記名で検索
  - `findByDisplayNameContaining(String)`: 表記名部分一致検索
  - `findBySortKey(String)`: ソートキーで検索
  - `findAllOrderBySortKey()`: ソートキー順で全件取得

#### AlbumArticleRepository
- **パッケージ**: `domain.repository.albumarticle`
- **特記事項**: IDが`Album.Id`（1対1関係）
- **カスタムメソッド**:
  - `findByAlbumId(Album.Id)`: アルバムIDで検索
  - `findByLabelTag(LabelTag)`: ラベルタグで検索
  - `findByFirstEventSpaceContaining(String)`: イベントスペース部分一致検索
  - `findWithDistribution()`: 頒布情報を持つ記事を検索
  - `findWithAcquisitionChannels()`: 入手経路を持つ記事を検索

## 設計原則

### 1. 集約ルートにのみリポジトリを提供

```java
// ✅ 正しい: 集約ルートに対するリポジトリ
interface AlbumRepository extends Repository<Album, Album.Id>

// ❌ コンパイルエラー: 型制約により不可能
// interface TrackRepository extends Repository<Track, Track.Id>
// TrackはAggregateを実装していないため型制約を満たさない
```

### 2. コレクション指向

リポジトリはドメインオブジェクトのコレクションとして振る舞います。データベースの詳細（SQL、テーブル等）を隠蔽します。

### 3. 集約全体を永続化

リポジトリは集約全体を一つの単位として扱います。

```java
// 集約全体を保存
Album album = new Album(id, title, releaseDate, tracks);
albumRepository.save(album);
// ↑ tracks等も一緒に保存される
```

### 4. リアクティブ

すべてのメソッドは`Uni<T>`または`Uni<List<T>>`を返します。

```java
// 非同期操作
albumRepository.findById(id)
    .onItem().ifNull().failWith(() -> new AlbumNotFoundException(id))
    .flatMap(album -> albumRepository.save(album))
```

## アーキテクチャ

```
domain/
├── model/
│   └── aggregate/           # 集約ルート
│       ├── album/
│       │   └── Album.java
│       ├── tune/
│       │   └── Tune.java
│       └── ...
└── repository/              # リポジトリインターフェース（ドメイン層）
    ├── Repository.java      # 基底インターフェース
    ├── album/
    │   └── AlbumRepository.java
    ├── tune/
    │   └── TuneRepository.java
    └── ...

infrastructure/
└── persistence/             # リポジトリ実装（インフラ層）
    └── repository/
        ├── AlbumRepositoryImpl.java
        ├── TuneRepositoryImpl.java
        └── ...
```

## 次のステップ

1. **infrastructure層の実装**
   - `infrastructure.persistence.repository`パッケージに各リポジトリの実装クラスを作成
   - Panache Reactive Repositoryを使用した実装
   - ドメインモデルとエンティティのマッピング

2. **データソース層の実装**
   - `infrastructure.persistence.datasource`パッケージにデータソース作成
   - Panache Entityクラスの定義

3. **マッパー層の実装**
   - `infrastructure.persistence.mapper`パッケージにマッパー作成
   - ドメインモデル ⇔ Panache Entity の変換ロジック

## 参考

- ドメイン駆動設計の集約パターンを遵守
- Quarkus Reactive Panacheを使用したリアクティブプログラミング
