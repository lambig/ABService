# ドメインモデル設計

## 概要

ABServiceのドメインモデルは、アイリッシュ音楽のアルバム・トラック・チューン管理を中心としたドメイン駆動設計（DDD）に基づいて設計されています。

## 設計原則

### 1. インターフェースと実装の分離

- **インターフェース**: パッケージ直下に配置（例: `com.abservice.domain.model.aggregate.album`）
- **実装**: `internal` サブパッケージに配置（例: `com.abservice.domain.model.aggregate.album.internal`）
- **目的**:
  - ドメイン層の公開APIを明確化
  - 実装の詳細を隠蔽し、依存関係を制御
  - テスタビリティの向上
  - 将来的な実装の差し替えを容易に

### 2. 集約設計

- **集約ルート**: トランザクション境界と整合性制約の単位
- **不変条件**: 集約ルート内で保証
- **参照**: 集約間は ID による参照のみ（オブジェクト参照を持たない）

### 3. 値オブジェクトの活用

- プリミティブ型の代わりに意味のある値オブジェクトを使用
- 不変性を保証
- ドメインルールをカプセル化

---

## 集約設計方針

### 集約ルート候補

データベース設計に基づき、以下の集約ルートを定義します：

#### 1. **Album集約**（現在の主要集約）
- **集約ルート**: Album
- **集約内エンティティ**: Track, TrackTune
- **値オブジェクト**: ArtistCredit（アーティスト名義）、EventInfo（イベント情報）
- **集約外参照**: TuneId（ID参照のみ）
- **トランザクション境界**: アルバム・トラック・セット構成の一貫性保証

#### 2. **AlbumArticle集約**（プレゼンテーション層集約）
- **集約ルート**: AlbumArticle
- **集約内エンティティ**: AlbumDistribution, AlbumAcquisitionChannel（コレクション）
- **集約外参照**: AlbumId（ID参照のみ）
- **トランザクション境界**: 記事・頒布情報・入手経路の一貫性保証

#### 3. **将来の集約候補**
- **Tune**: チューン自体の詳細管理（複数版管理、楽譜、音源など）が必要になれば集約化

---

## パッケージ構造

```
com.abservice.domain.model
├── vo/                          # 共通の値オブジェクト
│   ├── Title.java              # インターフェース
│   ├── Url.java
│   ├── CatalogNumber.java
│   ├── Price.java
│   ├── CreditName.java
│   ├── ArtistCredit.java       # アーティスト名義（埋め込みVO）
│   ├── EventInfo.java          # イベント情報（埋め込みVO）
│   └── internal/               # 実装クラス
│       ├── TitleImpl.java
│       ├── UrlImpl.java
│       └── ...
├── aggregate/
│   ├── album/                  # Album集約
│   │   ├── Album.java          # 集約ルート(interface)
│   │   ├── AlbumId.java        # ID型
│   │   ├── Track.java          # エンティティ(interface)
│   │   ├── TrackId.java
│   │   ├── TrackTune.java      # エンティティ(interface)
│   │   └── internal/           # 実装クラス
│   │       ├── AlbumImpl.java
│   │       ├── TrackImpl.java
│   │       └── TrackTuneImpl.java
│   └── albumarticle/           # AlbumArticle集約
│       ├── AlbumArticle.java   # 集約ルート(interface)
│       ├── AlbumDistribution.java
│       ├── AlbumAcquisitionChannel.java
│       └── internal/           # 実装クラス
└── entity/                     # 将来的に集約ルートになる可能性
    └── tune/
        ├── Tune.java           # 基底インターフェース
        ├── TuneId.java
        ├── TraditionalTune.java    # サブタイプ(interface)
        ├── OriginalTune.java       # サブタイプ(interface)
        ├── ArrangementTune.java    # サブタイプ(interface)
        ├── TuneKind.java           # 列挙型
        └── internal/
            ├── TraditionalTuneImpl.java
            ├── OriginalTuneImpl.java
            └── ArrangementTuneImpl.java
```

---

## 型階層

```
DomainObject<T>
├── ValueObject<T>              // 値で識別されるオブジェクト
│   ├── Title, Url, CreditName
│   ├── CatalogNumber, Isrc
│   ├── Price, Duration
│   ├── ArtistCredit            # アーティスト名義（埋め込みVO）
│   ├── EventInfo               # イベント情報（埋め込みVO）
│   └── その他プリミティブ値の包含
└── DomainEntity<T, ID>         // IDで識別されるオブジェクト
    ├── Track                   # 集約内エンティティ
    ├── TrackTune               # 集約内エンティティ
    ├── AlbumDistribution       # 集約内エンティティ
    ├── AlbumAcquisitionChannel # 集約内エンティティ
    ├── Tune                    # 将来の集約候補（抽象）
    │   ├── TraditionalTune     # トラッド
    │   ├── OriginalTune        # オリジナル
    │   └── ArrangementTune     # アレンジ
    └── Aggregate<T, ID>        # 永続化境界を持つ集約ルート
        ├── Album
        └── AlbumArticle
```

---

## Tune エンティティ設計（将来の集約候補）

### 設計の特徴

Tune は **3つのサブタイプ** を持つ階層構造で設計されています。これにより、チューンの種類（トラッド/オリジナル/アレンジ）をコンパイル時に区別でき、型安全性が保証されます。

### Tune の種類

1. **TraditionalTune（トラッド）**: 伝統曲
   - 作曲者は "Trad." など
   - 原曲情報は不要

2. **OriginalTune（オリジナル）**: 完全オリジナル曲
   - 明確な作曲者が存在
   - 原曲情報は不要

3. **ArrangementTune（アレンジ）**: 既存曲のアレンジ版
   - アレンジャー情報が重要
   - **原曲情報を必須で持つ**（原曲タイトル、原曲作曲者）

### インターフェース設計

```java
// 基底インターフェース
public interface Tune extends DomainObject {
    TuneId id();
    Title title();
    TuneKind kind();  // TRAD, ORIGINAL, ARRANGEMENT
    TuneType tuneType();  // Reel, Jig, Polka, etc.
    MusicalKey defaultKey();
    Tempo defaultTempo();
    CreditName defaultComposerCredit();
    CreditName defaultArrangerCredit();
}

// トラッド（伝統曲）
public interface TraditionalTune extends Tune {
    @Override
    default TuneKind kind() {
        return TuneKind.TRAD;
    }
}

// オリジナル
public interface OriginalTune extends Tune {
    @Override
    default TuneKind kind() {
        return TuneKind.ORIGINAL;
    }
}

// アレンジ
public interface ArrangementTune extends Tune {
    @Override
    default TuneKind kind() {
        return TuneKind.ARRANGEMENT;
    }

    OriginalWorkInfo originalWorkInfo();  // 原曲情報（必須）
}
```

### 補助的な型

#### TuneKind 列挙型
```java
public enum TuneKind {
    TRAD,         // トラッド（伝統曲）
    ORIGINAL,     // オリジナル
    ARRANGEMENT   // アレンジ
}
```

#### OriginalWorkInfo 値オブジェクト
```java
public interface OriginalWorkInfo {
    Title originalTitle();       // 原曲タイトル
    CreditName originalCredit(); // 原曲の作曲者・アーティスト
}
```

### 使用例

```java
// トラッドの場合
TraditionalTune trad = tuneFactory.createTraditionalTune(
    id,
    new Title("The Silver Spear"),
    new TuneType("Reel"),
    new MusicalKey("D"),
    new Tempo(120),
    new CreditName("Trad."),
    null  // アレンジャーなし
);

// オリジナルの場合
OriginalTune original = tuneFactory.createOriginalTune(
    id,
    new Title("My Original Tune"),
    new TuneType("Jig"),
    new MusicalKey("G"),
    new Tempo(110),
    new CreditName("John Doe"),
    null
);

// アレンジの場合
ArrangementTune arrangement = tuneFactory.createArrangementTune(
    id,
    new Title("The Silver Spear (Rock Version)"),
    new TuneType("Reel"),
    new MusicalKey("E"),
    new Tempo(140),
    new CreditName("Trad."),
    new CreditName("Jane Smith"),  // アレンジャー
    new OriginalWorkInfo(
        new Title("The Silver Spear"),
        new CreditName("Trad.")
    )
);

// 型による判定
if (tune instanceof ArrangementTune arr) {
    OriginalWorkInfo original = arr.originalWorkInfo();
    System.out.println("原曲: " + original.originalTitle());
}
```

### 設計の利点

1. **型安全性**: コンパイル時にチューンの種類を区別
2. **明示的な制約**: アレンジの場合のみ原曲情報を持つことを型で表現
3. **拡張性**: 将来的に各サブタイプ固有のメソッドを追加可能
4. **パターンマッチング**: Java のパターンマッチングと相性が良い

---

## Album集約

### 責務
- アルバム基本情報の管理（タイトル、リリース日、カタログ番号）
- トラックリストの管理（順序、録音情報）
- セット構成の管理（トラック内のチューン順序、クレジット上書き）
- アルバム全体の整合性保証（トラック番号の重複防止など）

### 集約境界
```
Album (集約ルート)
├── AlbumId (ID)
├── AlbumTitle (VO)
├── releaseDate: LocalDate
├── artistCreditId: ArtistCreditId (ID参照)
├── eventId: EventId (ID参照, nullable)
├── catalogNumber: CatalogNumber (VO, nullable)
├── tracks: List<Track> (集約内エンティティ)
│   └── Track
│       ├── TrackId (ID)
│       ├── trackNo: Integer
│       ├── title: TrackTitle (VO)
│       ├── artistCreditId: ArtistCreditId (ID参照, nullable)
│       ├── recordingDate: LocalDate (nullable)
│       ├── recordingPlace: String (nullable)
│       ├── duration: Duration (VO, nullable)
│       ├── isLive: Boolean (nullable)
│       ├── isrc: Isrc (VO, nullable)
│       └── tunes: List<TrackTune> (集約内エンティティ)
│           └── TrackTune
│               ├── seq: Integer
│               ├── tuneId: TuneId (ID参照, nullable)
│               ├── composerCreditOverride: Credit (VO, nullable)
│               ├── arrangerCreditOverride: Credit (VO, nullable)
│               └── linkUrl: Url (VO, nullable)
└── 監査カラム (created_at, created_by, updated_at, updated_by, version_no)
```

### 主要操作
- `Album.addTrack(Track)`: トラック追加（トラック番号の一意性チェック）
- `Album.removeTrack(TrackId)`: トラック削除
- `Album.reorderTracks(List<TrackId>)`: トラック順序変更
- `Track.addTune(TrackTune)`: チューン追加（seq の一意性チェック）
- `Track.removeTune(int seq)`: チューン削除
- `Track.reorderTunes(List<TuneId>)`: チューン順序変更

### 不変条件
- トラック番号は1から連番で重複なし
- 同一トラック内の seq は1から連番で重複なし
- アルバム全体のアーティストクレジットは必須
- トラック個別のアーティストクレジットは省略可能（省略時はアルバムのものを継承）

---

## AlbumArticle集約

### 責務
- アルバム記事・お品書き用テキストの管理
- 頒布条件・価格情報の管理
- 入手経路（販売チャネル）の管理
- プレゼンテーション層情報の一貫性保証

### 集約境界
```
AlbumArticle (集約ルート)
├── AlbumId (ID、Album集約への参照)
├── introLong: ArticleText (VO, nullable)
├── introShort: ArticleText (VO, nullable)
├── firstEventSpace: String (nullable)
├── labelTag: LabelTag (VO, nullable)
├── distribution: AlbumDistribution (集約内エンティティ, nullable)
│   ├── physicalPrice: Price (VO, nullable)
│   ├── downloadPrice: Price (VO, nullable)
│   ├── demoUrl: Url (VO, nullable)
│   └── note: String (nullable)
└── acquisitionChannels: List<AlbumAcquisitionChannel> (集約内エンティティ)
    └── AlbumAcquisitionChannel
        ├── AlbumAcquisitionChannelId (ID)
        ├── channelType: ChannelType (Enum)
        ├── name: String
        ├── url: Url (VO, nullable)
        └── note: String (nullable)
```

### 主要操作
- `AlbumArticle.updateIntro(introLong, introShort)`: 紹介文更新
- `AlbumArticle.setDistribution(AlbumDistribution)`: 頒布情報設定
- `AlbumArticle.addAcquisitionChannel(AlbumAcquisitionChannel)`: 入手経路追加
- `AlbumArticle.removeAcquisitionChannel(AlbumAcquisitionChannelId)`: 入手経路削除
- `AlbumArticle.updateLabelTag(LabelTag)`: ラベルタグ更新

### 不変条件
- AlbumId は必須（Album集約が存在する必要がある）
- 価格は非負
- ChannelType は定義済みの値のみ

---

## 将来の集約候補

### ArtistCredit集約（将来）
現在は小さいエンティティですが、将来的に以下の情報が追加される可能性があります：
- アーティスト詳細情報（プロフィール、活動期間）
- メンバー構成（複数アーティストの場合）
- ソーシャルメディアリンク
- ディスコグラフィー参照

この時点で集約ルート化を検討します。

### Event集約（将来）
現在は小さいエンティティですが、将来的に以下の情報が追加される可能性があります：
- イベントの詳細情報（開催時間、会場詳細）
- 出展サークル一覧
- イベントレポート
- 関連リンク

この時点で集約ルート化を検討します。

### Tune集約（将来）
現在はTrack内で参照される形ですが、将来的に以下の情報が追加される可能性があります：
- チューンの複数版管理（スタジオ版、ライブ版、アレンジ版）
- 楽譜・タブ譜の管理
- 音源ファイルへの参照
- 演奏ノート・解説

この時点で集約ルート化を検討し、Album集約との関係を再設計します。

---

## 実装方針

### コンストラクタアクセス制御とファクトリパターン

**重要原則**: すべてのドメインオブジェクトのコンストラクタは`private`とし、以下のパターンで生成を制御します。

#### パターン選択ガイド

| 状況 | 推奨パターン | 実装方法 |
|------|------------|---------|
| 単純な値の詰め込みのみ | Lombokファクトリ | `@AllArgsConstructor(staticName = "of", access = AccessLevel.PRIVATE)` |
| 型変換や簡単な加工 | Static Factory Method | `public static T create(...)` / `reconstruct(...)` |
| 外部依存が必要 | Factoryクラス | `@ApplicationScoped` + DIコンテナ管理 |
| 複雑なバリデーション | Factoryクラス | 専用ファクトリクラス |
| 複数の生成バリエーション | Factoryクラス | インターフェース + 実装クラス |

#### 1. Lombokファクトリ（値オブジェクト向け）

```java
/**
 * アルバムタイトル（値オブジェクト）
 */
@AllArgsConstructor(staticName = "of", access = AccessLevel.PRIVATE)
public record AlbumTitle(String value) implements ValueObject<AlbumTitle> {
    public AlbumTitle {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Title cannot be blank");
        }
        if (value.length() > 255) {
            throw new IllegalArgumentException("Title too long");
        }
    }
}

// 使用例
var title = AlbumTitle.of("My Album");
```

#### 2. Static Factory Method（エンティティ向け）

```java
/**
 * トラック（エンティティ）
 */
@Getter
@With(AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Track implements DomainEntity<Track, Track.Id> {
    private final Id id;
    private final TrackTitle title;
    private final Duration duration;

    /**
     * 新規トラック生成
     */
    public static Track create(TrackTitle title, Duration duration) {
        return new Track(Id.generate(), title, duration);
    }

    /**
     * 永続化層からの再構成
     */
    public static Track reconstruct(Id id, TrackTitle title, Duration duration) {
        return new Track(id, title, duration);
    }

    /**
     * タイトル変更（新しいインスタンスを返す）
     */
    public Track changeTitle(TrackTitle newTitle) {
        return withTitle(newTitle);
    }

    public record Id(String value) implements EntityId<Track> {
        public Id {
            if (!EntityId.isValidUuid(value)) {
                throw new IllegalArgumentException("Invalid Track ID");
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

#### 3. Factoryクラス（集約・複雑なロジック向け）

```java
// ファクトリインターフェース
public interface AlbumFactory extends Factory<Album, AlbumFactory.CreateParams> {

    /**
     * 新規アルバム生成（外部依存あり）
     */
    Uni<Album> create(CreateParams params);

    /**
     * 永続化層からの再構成（外部依存なし）
     */
    Album reconstruct(ReconstructParams params);

    record CreateParams(
        AlbumTitle title,
        LocalDate releaseDate,
        ArtistCredit artistCredit,
        EventInfo eventInfo,
        CatalogNumber catalogNumber
    ) implements Factory.Params {}

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
        // 外部依存を使った複雑なバリデーション
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
                    throw new IllegalArgumentException("Duplicate catalog number: " + catalogNumber.value());
                }
                return true;
            });
    }
}
```

### インターフェースと実装の配置（旧ルール - 段階的に廃止予定）

**注**: 以下のインターフェース分離アプローチは、プロジェクト初期に採用されましたが、現在は**コンストラクタをprivateにする方針**に移行中です。既存コードとの互換性のため、当面は両方のパターンが混在します。

すべてのドメインオブジェクト（値オブジェクト、エンティティ、集約ルート）は、インターフェースと実装を分離します。

#### 配置ルール

- **インターフェース**: パッケージ直下（例: `com.abservice.domain.model.aggregate.album.Album`）
- **実装クラス**: `internal` サブパッケージ（例: `com.abservice.domain.model.aggregate.album.internal.AlbumImpl`）
- **実装クラスのアクセス修飾子**: パッケージプライベート（`package-private`）
- **生成**: ファクトリ経由でのみ実装クラスをインスタンス化

#### 例: Album インターフェースと実装

```java
// com.abservice.domain.model.aggregate.album.Album (インターフェース)
public interface Album extends DomainObject {
    AlbumId id();
    Title title();
    LocalDate releaseDate();
    ArtistCreditId artistCreditId();
    List<Track> tracks();

    Album addTrack(Track track);
    Album removeTrack(TrackId trackId);
}

// com.abservice.domain.model.aggregate.album.internal.AlbumImpl (実装)
@Getter
@With(AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PACKAGE)  // パッケージプライベート
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
class AlbumImpl implements Album {
    @EqualsAndHashCode.Include
    private final AlbumId id;
    private final Title title;
    private final LocalDate releaseDate;
    private final ArtistCreditId artistCreditId;
    private final List<Track> tracks;

    @Override
    public Album addTrack(Track track) {
        // 不変条件チェック
        if (tracks.stream().anyMatch(t -> t.trackNo().equals(track.trackNo()))) {
            throw new IllegalArgumentException("Track number already exists");
        }
        var newTracks = new ArrayList<>(tracks);
        newTracks.add(track);
        return withTracks(List.copyOf(newTracks));
    }

    @Override
    public Album removeTrack(TrackId trackId) {
        var newTracks = tracks.stream()
            .filter(t -> !t.id().equals(trackId))
            .toList();
        return withTracks(newTracks);
    }
}
```

### 値オブジェクト（Value Object） - 新ルール

- **実装方法**: Java Records
- **コンストラクタ**: `private`（Lombokファクトリまたはstatic factory method使用）
- **特性**: 不変、等価性は全属性による比較
- **バリデーション**: コンストラクタで実施

```java
/**
 * アルバムタイトル（値オブジェクト）
 */
@AllArgsConstructor(staticName = "of", access = AccessLevel.PRIVATE)
public record AlbumTitle(String value) implements ValueObject<AlbumTitle> {
    public AlbumTitle {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Title cannot be blank");
        }
        if (value.length() > 255) {
            throw new IllegalArgumentException("Title too long");
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

### エンティティ（Entity） - 新ルール

- **実装方法**: Lombok `@With(AccessLevel.PRIVATE)` + 不変フィールド
- **コンストラクタ**: `private`（static factory method使用）
- **状態変更**: Witherパターン（新しいインスタンスを返す）
- **等価性**: IDのみで判定

```java
/**
 * トラック（エンティティ）
 */
@Getter
@With(AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Track implements DomainEntity<Track, Track.Id> {
    @EqualsAndHashCode.Include
    private final Id id;
    private final Integer trackNo;
    private final TrackTitle title;

    /**
     * 新規トラック生成
     */
    public static Track create(Integer trackNo, TrackTitle title) {
        return new Track(Id.generate(), trackNo, title);
    }

    /**
     * 永続化層からの再構成
     */
    public static Track reconstruct(Id id, Integer trackNo, TrackTitle title) {
        return new Track(id, trackNo, title);
    }

    /**
     * タイトル変更（新しいインスタンスを返す）
     */
    public Track changeTitle(TrackTitle newTitle) {
        return withTitle(newTitle);
    }

    @Override
    public boolean equivalentTo(Track other) {
        return this.id.equals(other.id);
    }

    public record Id(String value) implements EntityId<Track> {
        public Id {
            if (!EntityId.isValidUuid(value)) {
                throw new IllegalArgumentException("Invalid Track ID");
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

### 集約ルート（Aggregate Root） - 新ルール

- **実装方法**: Lombok `@With(AccessLevel.PRIVATE)` + 業務メソッド
- **コンストラクタ**: `private`（Factoryクラス使用を推奨）
- **不変条件チェック**: 状態変更メソッド内で実施
- **集約内エンティティ**: 不変コレクションで保持

```java
/**
 * アルバム集約ルート
 */
@Getter
@With(AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Album implements Aggregate<Album, Album.Id> {
    @EqualsAndHashCode.Include
    private final Id id;
    private final AlbumTitle title;
    private final LocalDate releaseDate;
    private final ArtistCredit artistCredit;
    private final EventInfo eventInfo;
    private final CatalogNumber catalogNumber;
    private final List<Track> tracks;

    /**
     * トラック追加（整合性を保ちながら新しいインスタンスを返す）
     */
    public Album addTrack(Track track) {
        // 不変条件チェック
        if (tracks.stream().anyMatch(t -> t.trackNo().equals(track.trackNo()))) {
            throw new IllegalArgumentException("Track number already exists");
        }
        var newTracks = new ArrayList<>(tracks);
        newTracks.add(track);
        return withTracks(List.copyOf(newTracks));
    }

    /**
     * トラック削除
     */
    public Album removeTrack(Track.Id trackId) {
        var newTracks = tracks.stream()
            .filter(t -> !t.id().equals(trackId))
            .toList();
        return withTracks(newTracks);
    }

    /**
     * タイトル変更
     */
    public Album changeTitle(AlbumTitle newTitle) {
        return withTitle(newTitle);
    }

    @Override
    public boolean equivalentTo(Album other) {
        return this.id.equals(other.id);
    }

    public record Id(String value) implements EntityId<Album> {
        public Id {
            if (!EntityId.isValidUuid(value)) {
                throw new IllegalArgumentException("Invalid Album ID");
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

---

### 値オブジェクト（Value Object） - 旧ルール（段階的廃止予定）

- **実装方法**: Java Records（インターフェースと実装を分離）
- **特性**: 不変、等価性は全属性による比較
- **バリデーション**: コンストラクタで実施
- **例**:
  ```java
  // インターフェース
  public interface Title {
      String value();
  }

  // 実装（internal パッケージ）
  record TitleImpl(String value) implements Title {
      public TitleImpl {
          if (value == null || value.isBlank()) {
              throw new IllegalArgumentException("Title cannot be blank");
          }
          if (value.length() > 255) {
              throw new IllegalArgumentException("Title is too long");
          }
      }
  }
  ```

### エンティティ（Entity） - 旧ルール（段階的廃止予定）

- **実装方法**: Lombok `@With(AccessLevel.PRIVATE)` + 不変フィールド
- **状態変更**: Witherパターン（新しいインスタンスを返す）
- **等価性**: IDのみで判定
- **アクセス修飾子**: パッケージプライベート（`package-private`）
- **例**:
  ```java
  // インターフェース
  public interface Track extends DomainObject {
      TrackId id();
      Integer trackNo();
      Title title();

      Track changeTitle(Title newTitle);
  }

  // 実装（internal パッケージ）
  @Getter
  @With(AccessLevel.PRIVATE)
  @AllArgsConstructor(access = AccessLevel.PACKAGE)
  @EqualsAndHashCode(onlyExplicitlyIncluded = true)
  class TrackImpl implements Track {
      @EqualsAndHashCode.Include
      private final TrackId id;
      private final Integer trackNo;
      private final Title title;

      @Override
      public Track changeTitle(Title newTitle) {
          return withTitle(newTitle);
      }
  }
  ```

### 集約ルート（Aggregate Root） - 旧ルール（段階的廃止予定）

- **実装方法**: Lombok `@With(AccessLevel.PRIVATE)` + 業務メソッド
- **不変条件チェック**: 状態変更メソッド内で実施
- **集約内エンティティ**: 不変コレクションで保持
- **アクセス修飾子**: パッケージプライベート（`package-private`）

---

### ID型（型安全な識別子）

- **実装方法**: Records
- **配置**: 各エンティティ・集約のネストクラス
- **例**: `Album.Id`, `Track.Id`
- **バリデーション**: コンストラクタで実施
- **生成方法**: static factory method

```java
public record Id(String value) implements EntityId<Album> {
    public Id {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Album ID cannot be blank");
        }
        if (!EntityId.isValidUuid(value)) {
            throw new IllegalArgumentException("Album ID must be a valid UUID: " + value);
        }
    }

    /**
     * UUIDv7を生成してAlbum.Idを作成
     */
    public static Id generate() {
        return new Id(EntityId.generateUuidV7());
    }

    /**
     * 文字列からAlbum.Idを生成
     */
    public static Id of(String value) {
        return new Id(value);
    }
}
```

### ファクトリパターン

実装クラスの生成は以下のパターンを使用します。

#### パターン1: Lombokファクトリ（値オブジェクト）

```java
@AllArgsConstructor(staticName = "of", access = AccessLevel.PRIVATE)
public record AlbumTitle(String value) implements ValueObject<AlbumTitle> {
    // バリデーションはコンストラクタで実施
}
```

#### パターン2: Static Factory Method（エンティティ）

```java
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Track implements DomainEntity<Track, Track.Id> {

    public static Track create(TrackTitle title, Duration duration) {
        return new Track(Id.generate(), title, duration);
    }

    public static Track reconstruct(Id id, TrackTitle title, Duration duration) {
        return new Track(id, title, duration);
    }
}
```

#### パターン3: Factoryクラス（集約）

```java
// ファクトリインターフェース
public interface AlbumFactory extends Factory<Album, AlbumFactory.CreateParams> {
    Uni<Album> create(CreateParams params);
    Album reconstruct(ReconstructParams params);

    record CreateParams(...) implements Factory.Params {}
    record ReconstructParams(...) implements Factory.Params {}
}

// ファクトリ実装
@ApplicationScoped
public class AlbumFactoryImpl implements AlbumFactory {
    private final AlbumRepository albumRepository;

    @Override
    public Uni<Album> create(CreateParams params) {
        // 外部依存を使った複雑なバリデーション
        return validateCatalogNumber(params.catalogNumber())
            .map(valid -> new Album(...));  // privateコンストラクタを呼び出し
    }

    @Override
    public Album reconstruct(ReconstructParams params) {
        return new Album(...);  // privateコンストラクタを呼び出し
    }
}
```

---

### TuneFactory の特殊性（旧ルール - 参考）

Tune は抽象インターフェースで、3つのサブタイプがあるため、ファクトリで適切な実装を返します。

```java
public interface TuneFactory {
    TraditionalTune createTraditionalTune(
        TuneId id,
        Title title,
        TuneType tuneType,
        MusicalKey defaultKey,
        Tempo defaultTempo,
        CreditName defaultComposerCredit,
        CreditName defaultArrangerCredit
    );

    OriginalTune createOriginalTune(
        TuneId id,
        Title title,
        TuneType tuneType,
        MusicalKey defaultKey,
        Tempo defaultTempo,
        CreditName defaultComposerCredit,
        CreditName defaultArrangerCredit
    );

    ArrangementTune createArrangementTune(
        TuneId id,
        Title title,
        TuneType tuneType,
        MusicalKey defaultKey,
        Tempo defaultTempo,
        CreditName defaultComposerCredit,
        CreditName defaultArrangerCredit,
        OriginalWorkInfo originalWorkInfo  // 必須
    );
}
```

---

## 設計上の留意点

### 1. 集約境界の明確化
- Album と AlbumArticle は別集約（異なるトランザクション境界）
- Album: コアドメイン情報（作品・録音）
- AlbumArticle: プレゼンテーション情報（記事・頒布・販売チャネル）

### 2. 集約間の参照
- ID参照のみ（オブジェクト参照は禁止）
- `AlbumArticle` は `AlbumId` で `Album` を参照
- `Track` は `ArtistCreditId`, `TuneId` で参照
- `TrackTune` は `TuneId` で参照

### 3. 集約の大きさ
- Album集約は Track, TrackTune を含むため中規模
- パフォーマンス上の問題が出た場合は、Track を別集約に分離することも検討
- 現時点では1アルバムのトラック数は限定的（通常10-20曲程度）なので問題なし

### 4. 将来の拡張性
- Tune が集約ルート化する場合、Track → Tune の参照関係は維持
- ArtistCredit が集約ルート化する場合も同様
- Event が集約ルート化する場合も同様

### 5. Tune の種類判定
- **推奨**: インターフェースの型による判定（`instanceof`）
- **非推奨**: `TuneKind` による判定
- 理由: 型安全性を活かし、コンパイル時に種類を区別できる

```java
// 推奨
if (tune instanceof ArrangementTune arrangement) {
    OriginalWorkInfo info = arrangement.originalWorkInfo();
    // ...
}

// 非推奨
if (tune.kind() == TuneKind.ARRANGEMENT) {
    // キャストが必要で型安全でない
}
```

---

## リポジトリ設計

### 集約ルートにのみリポジトリを提供

```java
interface AlbumRepository extends Repository<Album, Album.Id> {
    Uni<Album> findById(Album.Id id);
    Uni<List<Album>> findByArtistCreditId(ArtistCreditId artistCreditId);
    Uni<List<Album>> findByEventId(EventId eventId);
    Uni<Album> save(Album album);
    Uni<Void> delete(Album.Id id);
}

interface AlbumArticleRepository extends Repository<AlbumArticle, AlbumId> {
    Uni<AlbumArticle> findByAlbumId(AlbumId albumId);
    Uni<AlbumArticle> save(AlbumArticle article);
    Uni<Void> delete(AlbumId albumId);
}

// 将来的に集約化する場合
interface ArtistCreditRepository extends Repository<ArtistCredit, ArtistCredit.Id> {
    // ...
}

interface EventRepository extends Repository<Event, Event.Id> {
    // ...
}

interface TuneRepository extends Repository<Tune, Tune.Id> {
    // ...
}
```

### 集約内エンティティにはリポジトリを作らない
- ❌ `TrackRepository` は作成しない
- ❌ `TrackTuneRepository` は作成しない
- ❌ `AlbumDistributionRepository` は作成しない
- ❌ `AlbumAcquisitionChannelRepository` は作成しない

集約内エンティティは集約ルートを通じてアクセスします。

---

## 次のステップ

1. ✅ 設計ドキュメントの作成（インターフェース/実装分離、Tuneサブタイプ設計を含む）
2. ⏳ 共通の値オブジェクトとEnumの実装
3. ⏳ 将来の集約候補エンティティの実装（ArtistCredit, Event, Tune）
4. ⏳ Album集約の実装
5. ⏳ AlbumArticle集約の実装
6. ⏳ ファクトリ・リポジトリインターフェースの実装
7. ⏳ インフラストラクチャ層の実装
