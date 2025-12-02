# Value Object化によるモデリング簡素化

## 変更概要

ArtistCreditとEventを独立した集約ルートからValue Object (VO)に変更しました。

## 理由

これらのエンティティは以下の特徴を持っており、独立した集約ルートとして扱うのは過剰でした：

### ArtistCredit
- 単純なデータ構造（表示名とソートキーのみ）
- 独自のビジネスロジックがほぼ存在しない
- Album/Trackから常に参照される従属的な存在

### Event
- 補足的な情報（イベント名、日付、場所、メモ）
- 独立したライフサイクルを持つ必要性が低い
- Albumに紐づく付加情報

## 変更内容

### 1. Value Objectの作成

- `ArtistCredit` (VO): `com.abservice.domain.model.vo.common.ArtistCredit`
- `EventInfo` (VO): `com.abservice.domain.model.vo.common.EventInfo`

### 2. ドメインモデルの変更

#### Album
```java
// 変更前
private final ArtistCredit.Id artistCreditId;
private final Event.Id eventId;

// 変更後
private final ArtistCredit artistCredit;  // VO
private final EventInfo eventInfo;         // VO (nullable)
```

#### Track
```java
// 変更前
private final ArtistCredit.Id artistCreditId;

// 変更後
private final ArtistCredit artistCredit;  // VO (nullable)
```

### 3. データベーススキーマの変更

マイグレーション:
- `V15__Embed_artist_and_event_in_album.sql`: AlbumテーブルにArtistCreditとEventInfoのカラムを追加
- `V16__Embed_artist_in_track.sql`: TrackテーブルにArtistCreditのカラムを追加
- `V17__Drop_artist_credit_and_event_tables.sql`: artist_creditとeventテーブルを削除

#### 変更後のテーブル構造

**album**
```sql
-- Artist Credit (埋め込み)
artist_display_name VARCHAR(255) NOT NULL
artist_sort_key VARCHAR(255)

-- Event Info (埋め込み)
event_name VARCHAR(255)
event_date DATE
event_place VARCHAR(255)
event_note TEXT
```

**track**
```sql
-- Artist Credit (埋め込み、nullable)
artist_display_name VARCHAR(255)
artist_sort_key VARCHAR(255)
```

### 4. 削除されたコンポーネント

#### ドメイン層
- `com.abservice.domain.model.aggregate.artistcredit.ArtistCredit` (集約)
- `com.abservice.domain.model.aggregate.event.Event` (集約)
- `com.abservice.domain.repository.artistcredit.ArtistCreditRepository`
- `com.abservice.domain.repository.event.EventRepository`

#### インフラストラクチャ層
- `ArtistCreditEntity`
- `EventEntity`
- `ArtistCreditDataSource`
- `EventDataSource`
- `ArtistCreditRepositoryImpl`
- `EventRepositoryImpl`
- `ArtistCreditMapper`
- `EventMapper`

### 5. Repositoryインターフェースの変更

```java
// AlbumRepository
// 変更前
Uni<List<Album>> findByArtistCreditId(ArtistCredit.Id artistCreditId);
Uni<List<Album>> findByEventId(Event.Id eventId);

// 変更後
Uni<List<Album>> findByArtistName(String artistName);
Uni<List<Album>> findByEventName(String eventName);
```

## メリット

1. **モデルの簡素化**: 不要な集約ルートを削除し、モデルがシンプルになりました
2. **トランザクション境界の明確化**: Album集約内で完結するため、データの一貫性管理が容易
3. **パフォーマンス向上**: 外部キーによるJOINが不要になり、クエリが高速化
4. **コードの削減**: Repository、DataSource、Mapper等の関連コードが不要に

## 注意点

1. **検索機能**: アーティスト名やイベント名での検索は引き続き可能ですが、これらはAlbumテーブルに対する直接検索となります
2. **データ正規化**: 同じアーティスト名が複数のアルバムに重複して保存されますが、この規模では問題ありません
3. **将来の拡張**: アーティストやイベントに複雑なロジックが必要になった場合は、再度集約ルートとして分離することも可能です

## マイグレーション手順

1. 既存データがある場合、マイグレーションスクリプトが自動的にデータを移行します
2. `V15`, `V16`, `V17`のマイグレーションを順番に実行
3. 既存のartist_creditとeventテーブルのデータは、対応するAlbum/Trackレコードに埋め込まれます
