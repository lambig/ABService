# ArtistCredit / Event の Value Object 化

> 現行ドメインモデルの正は `DOMAIN_MODEL_DESIGN.md`、スキーマの正は `DATABASE_DESIGN.md`、全体状況は [STATUS_AND_ROADMAP.md](STATUS_AND_ROADMAP.md)。
> 本書は「`ArtistCredit` とイベント頒布情報を独立集約ルートにせず、`Album` / `Track` に埋め込む Value Object とする」という設計判断とその根拠を記録する。

## 方針

`ArtistCredit` とイベント頒布情報（`EventReleasedAt`）は独立した集約ルートではなく、`Album` / `Track` に埋め込む Value Object として扱う。

> VO の構成・永続化スキーマの正は `DOMAIN_MODEL_DESIGN.md` / `DATABASE_DESIGN.md`。本書は「集約ルートにせず VO 埋め込みとする」判断とその根拠に絞る。

## 対象

- **ArtistCredit**（`vo.common`）: 表示名とソートキーのみの単純な名義 VO。`Album` では必須、`Track` では nullable（null なら `Album` の名義を継承）。
- **EventReleasedAt**（`vo.common`）: アルバムがイベント頒布された情報を表す VO。`Album` に nullable で埋め込む。

いずれも `Album` / `Track` に埋め込む Value Object とし、独立集約ルートにはしない。

## 根拠

`ArtistCredit` は表示名とソートキーのみの単純構造で独自ロジックを持たず、`Album` / `Track` から常に従属的に参照される。イベント頒布情報も `Album` に紐づく付加情報で、独立したライフサイクルを持つ必要性が低い。よって独立集約ルートより VO 埋め込みが適切:

1. **モデルの簡素化**: 不要な集約ルートを排する。
2. **トランザクション境界の明確化**: `Album` 集約内で完結し、一貫性管理が容易。
3. **パフォーマンス**: 外部キーによる JOIN が不要。
4. **コード量の削減**: 専用の Repository / DataSource / Mapper が不要。

## トレードオフ

1. **正規化**: 同一アーティスト名が複数アルバムに重複保存されるが、この規模では許容する。
2. **将来の拡張**: アーティストやイベントに複雑なロジックが必要になれば、再度集約ルートへ分離しうる。
