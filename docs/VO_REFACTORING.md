# ArtistCredit / Event の Value Object 化

> 現行ドメインモデルの正は `DOMAIN_MODEL_DESIGN.md`、スキーマの正は `DATABASE_DESIGN.md`、全体状況は [STATUS_AND_ROADMAP.md](STATUS_AND_ROADMAP.md)。
> 本書は「`ArtistCredit` とイベント頒布情報を独立集約ルートにせず、`Album` / `Track` に埋め込む Value Object とする」という設計判断とその根拠を記録する。

## 方針

`ArtistCredit` とイベント頒布情報（`EventReleasedAt`）は独立した集約ルートではなく、`Album` / `Track` に埋め込む Value Object として扱う。

## 現行の構造

### ArtistCredit VO — `com.abservice.domain.model.vo.common.ArtistCredit`

- 表示名（display name）とソートキー（sort key）のみを持つ単純な VO。
- `Album` では必須（アルバム全体の名義）。`Track` では nullable（null の場合は `Album` の名義を継承）。

### EventReleasedAt VO — `com.abservice.domain.model.vo.common.EventReleasedAt`

- コミケ / M3 / ライブなどのイベントでアルバムが頒布された情報を表す VO。
- 構成: イベント名 `EventName` ＋ 開催日・スペース番号の組み合わせリスト `List<EventDateAndSpace>`（複数日程参加に対応）＋ 会場 `place` ＋ 補足 `note`。
- `Album` では nullable（`eventReleasedAt` フィールド。頒布情報が不明な場合は空）。
- `EventDateAndSpace`（`vo.common`）は開催日 `date` とスペース番号 `spaceNumber` の組。

### 永続化

- `album` テーブルに ArtistCredit（`artist_display_name` 必須 / `artist_sort_key`）と、イベントの `event_name` / `event_place` / `event_note` を埋め込む。
- 複数日程の開催日・スペース番号は `album_event_date_space` テーブル（`album` に対する 1:N）で保持する。
- `track` テーブルは ArtistCredit カラム（nullable）を持つ。
- スキーマの詳細は `DATABASE_DESIGN.md` と Flyway マイグレーション（V15–V19 系）を参照。

### 検索

- `AlbumRepository` はアーティスト名・イベント名での検索を `findByArtistName(String)` / `findByEventName(String)` として提供する（`album` テーブルへの直接検索）。

## 根拠

`ArtistCredit` は表示名とソートキーのみの単純構造で独自ロジックを持たず、`Album` / `Track` から常に従属的に参照される。イベント頒布情報も `Album` に紐づく付加情報で、独立したライフサイクルを持つ必要性が低い。よって独立集約ルートより VO 埋め込みが適切:

1. **モデルの簡素化**: 不要な集約ルートを排する。
2. **トランザクション境界の明確化**: `Album` 集約内で完結し、一貫性管理が容易。
3. **パフォーマンス**: 外部キーによる JOIN が不要。
4. **コード量の削減**: 専用の Repository / DataSource / Mapper が不要。

## トレードオフ

1. **正規化**: 同一アーティスト名が複数アルバムに重複保存されるが、この規模では許容する。
2. **将来の拡張**: アーティストやイベントに複雑なロジックが必要になれば、再度集約ルートへ分離しうる。
