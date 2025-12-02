# データベース設計ドキュメント

## 概要

本アプリケーションは、アイリッシュ音楽のアルバム・トラック・チューン（曲）を管理するシステムです。
データベース設計は以下の2つのレイヤーに分離されています：

1. **コアドメインモデル**: 作品・録音・チューン構成を表す純粋なデータ
2. **プレゼンテーションモデル**: Web表示・お品書き・頒布情報など、表現に関するデータ

## 設計方針

### 全体方針

- 1枚のアルバムには複数のトラックが紐づく
- 1トラックには 0..n 個の「チューン（曲）」が順序付きで紐づく（セット曲対応）
- Tune は「トラッド／オリジナル／アレンジ」を区別し、アレンジの場合は原曲情報をテキストで持つ（原曲自体はDBに載せない）
- Track は録音単位であり、録音違いは別 Track 行として扱う
- トラックやチューンごとのクレジット（作曲者／アレンジャー）は、Tune 側のデフォルトを持ちつつ、TrackTune で上書きできる
- 外部サイトへのリンクは URL 文字列1本で扱う（多態な参照はしない）

### 監査カラム

すべてのテーブルに以下の監査カラムを含む：
- `created_at`: 作成日時
- `created_by`: 作成者
- `updated_at`: 更新日時
- `updated_by`: 更新者
- `version_no`: バージョン番号（楽観的ロック用）

---

## コアドメインモデル

### 1. ArtistCredit（アーティスト名義）

**役割**: 「X」「X feat. Y」「ユニット名」など、ジャケットに書かれる名義文字列を管理する。

#### カラム

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| artist_credit_id | BIGSERIAL | NOT NULL | 主キー |
| display_name | VARCHAR(255) | NOT NULL | 表記そのもの（例: "Foo Bar", "Foo Bar feat. Baz"） |
| sort_key | VARCHAR(255) | NULL | 並び替え用のキー |

#### 設計意図

- アーティスト名義は「表示される文字列そのもの」を管理
- 複数アーティストのコラボレーションも1つの名義として扱う
- アルバム単位・トラック単位で異なる名義を設定可能

---

### 2. Event（発表イベント）

**役割**: コミケ・M3・ライブなど、アルバムを発表したイベント。

#### カラム

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| event_id | BIGSERIAL | NOT NULL | 主キー |
| name | VARCHAR(255) | NOT NULL | イベント名 |
| date | DATE | NULL | 開催日 |
| place | VARCHAR(255) | NULL | 会場名 |
| note | TEXT | NULL | 補足情報 |

#### 設計意図

- アルバムの初出イベントを記録
- イベント情報が不明な場合はアルバム側で NULL を許容

---

### 3. Album（アルバム）

**役割**: 1つのアルバム（版違いは当面考えない）を表す。

#### カラム

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| album_id | BIGSERIAL | NOT NULL | 主キー |
| title | VARCHAR(255) | NOT NULL | アルバムタイトル |
| release_date | DATE | NULL | 発表年月日 |
| artist_credit_id | BIGINT | NOT NULL | アルバム全体のアーティスト名義（FK） |
| event_id | BIGINT | NULL | 発表イベント（FK） |
| catalog_number | VARCHAR(100) | NULL | カタログナンバー |

#### 外部キー

- `artist_credit_id` → `artist_credit.artist_credit_id`
- `event_id` → `event.event_id`

#### 設計意図

- アルバム全体のアーティスト名義を持つ
- トラック個別に名義が指定されていない場合、この名義を継承する
- 初出イベントはオプショナル（通販のみのリリース等も想定）

---

### 4. Tune（チューン：曲そのもの）

**役割**: セットを構成する個々の「チューン」（The Silver Spear など）を表す。
トラッド／オリジナル／アレンジを区別し、アレンジの場合は原曲情報をテキストで保持する。

#### カラム

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| tune_id | BIGSERIAL | NOT NULL | 主キー |
| title | VARCHAR(255) | NOT NULL | この版（アレンジ版を含む）のチューン名 |
| tune_kind | VARCHAR(20) | NOT NULL | 'TRAD' / 'ORIGINAL' / 'ARRANGEMENT' |
| default_composer_credit | VARCHAR(255) | NULL | 作曲者・出典のデフォルト表記 |
| default_arranger_credit | VARCHAR(255) | NULL | アレンジャー名義のデフォルト表記 |
| original_work_title | VARCHAR(255) | NULL | 原曲のタイトル（アレンジの場合） |
| original_work_credit | VARCHAR(255) | NULL | 原曲の作曲者・アーティスト等（アレンジの場合） |
| tune_type | VARCHAR(50) | NULL | リール／ジグなど |
| default_key | VARCHAR(20) | NULL | 想定キー |
| default_tempo | INTEGER | NULL | BPM など |

#### 制約

- `tune_kind` は 'TRAD', 'ORIGINAL', 'ARRANGEMENT' のいずれか
- `tune_kind = 'ARRANGEMENT'` の場合、`original_work_title` が必須

#### 設計意図

- **トラッド（TRAD）**: 伝統曲。`default_composer_credit` に "Trad." など
- **オリジナル（ORIGINAL）**: 完全オリジナル曲
- **アレンジ（ARRANGEMENT）**: 既存曲のアレンジ版
  - 原曲情報は `original_work_title` / `original_work_credit` にテキストで保持
  - **原曲自体は DB エンティティとしては管理しない**（the session にも自前DBにも存在しない前提）
- クレジットはデフォルトを Tune 側に持ち、録音ごとに変える場合は TrackTune 側で上書き

---

### 5. Track（トラック：録音単位）

**役割**: アルバム上の「1トラックの録音」を表す。録音違い（スタジオ版／ライブ版など）は別 Track 行として扱う。

#### カラム

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| track_id | BIGSERIAL | NOT NULL | 主キー |
| album_id | BIGINT | NOT NULL | アルバムID（FK） |
| track_no | INTEGER | NOT NULL | アルバム内のトラック番号 |
| title | VARCHAR(255) | NOT NULL | トラックのタイトル（セット全体の名前でもよい） |
| artist_credit_id | BIGINT | NULL | トラック個別のアーティスト名義（FK） |
| recording_date | DATE | NULL | 録音日 |
| recording_place | VARCHAR(255) | NULL | 録音場所 |
| duration_msec | INTEGER | NULL | 再生時間（ミリ秒） |
| is_live | BOOLEAN | NULL | ライブ録音フラグ |
| isrc | VARCHAR(20) | NULL | ISRC（国際標準レコーディングコード） |

#### 外部キー

- `album_id` → `album.album_id`
- `artist_credit_id` → `artist_credit.artist_credit_id`

#### ユニーク制約

- `(album_id, track_no)`: アルバム内でのトラック番号は一意

#### 設計意図

- **録音単位で Track を分ける**: 同じセットのスタジオ版とライブ版は別 Track
- `artist_credit_id` が NULL の場合は Album の `artist_credit_id` を継承
- セット構成は TrackTune で表現（同じチューン列なら TrackTune の行がほぼ複製される形）

---

### 6. TrackTune（トラック内のチューン構成）

**役割**: 1トラックの中に含まれる 0..n 個のチューンと、その順番・クレジット・リンクを表す。

#### カラム

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| track_id | BIGINT | NOT NULL | トラックID（FK、複合PKの一部） |
| seq | INTEGER | NOT NULL | トラック内での登場順（1, 2, 3, ...）（複合PKの一部） |
| tune_id | BIGINT | NULL | チューンID（FK） |
| composer_credit_override | VARCHAR(255) | NULL | このトラック上での作曲者表記の上書き |
| arranger_credit_override | VARCHAR(255) | NULL | このトラック上でのアレンジャー表記の上書き |
| link_url | VARCHAR(500) | NULL | このチューン（またはその版）に関するページへの任意のURL |

#### 主キー

- `(track_id, seq)`: 複合主キー

#### 外部キー

- `track_id` → `track.track_id` (ON DELETE CASCADE)
- `tune_id` → `tune.tune_id`

#### 設計意図

- **セット曲対応**: 1トラックに複数のチューンを順序付きで配置
- **チューンがないケース**: MC、環境音などの場合、TrackTune 行が 0件でもよい
- **クレジット上書き**: NULL の場合は Tune 側のデフォルトを使用、値があれば上書き
- **外部リンク**: the session、自サイト、その他サイトへの URL を文字列で保持（多態参照はしない）
- **録音違いの表現**: 同じセット構成の別録音は、別 Track に対して同じ seq と tune_id の組を複製

---

## プレゼンテーションモデル

### 7. Article（記事）

**役割**: ブログ記事、アルバム紹介記事、お品書き掲載記事など、「公開情報」そのものを管理。

#### カラム

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| article_id | BIGSERIAL | NOT NULL | 主キー |
| article_type | VARCHAR(50) | NOT NULL | 記事種別 |
| album_id | BIGINT | NULL | アルバムID（FK、アルバム記事の場合のみ） |
| title | VARCHAR(500) | NOT NULL | 記事タイトル |
| body | TEXT | NULL | 記事本文 |
| intro_short | TEXT | NULL | ショート紹介文（お品書きや一覧表示用） |
| published_at | TIMESTAMP | NULL | 公開日時（業務的な掲載日） |
| updated_at_business | TIMESTAMP | NULL | 更新日時（業務的な修正日、監査列とは別概念） |
| is_public | BOOLEAN | NOT NULL | 公開フラグ（TRUE: 公開、FALSE: 非公開/下書き） |

#### 外部キー

- `album_id` → `album.album_id` (ON DELETE SET NULL)

#### 制約

- `article_type` は 'ALBUM', 'NOTE', 'NEWS', 'EVENT', 'OTHER' のいずれか

#### 設計意図

- **汎用的な記事管理**: アルバム記事以外の通常記事、ニュース、イベント情報にも対応
- **片方向関連**: Article → Album の参照のみ（アルバムから記事への逆参照は不要）
- **業務日時の管理**:
  - `published_at`: 「いつ公開したか」の業務意味
  - `updated_at_business`: 「いつ修正したか」の業務意味
  - 監査列（`created_at`, `updated_at`）とは別概念
- **公開制御**: `is_public` フラグで下書き状態を管理
- **お品書き対応**: `intro_short` でお品書き用の短縮版を保持

---

### 8. ArticleTag（記事タグ）

**役割**: 記事のカテゴライズ・フィルタリング用タグ。

#### カラム

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| article_tag_id | BIGSERIAL | NOT NULL | 主キー |
| name | VARCHAR(100) | NOT NULL | タグ名（ユニーク） |

#### 制約

- `name` は UNIQUE

#### 設計意図

- **タグマスタ**: タグ名の一元管理
- **再利用性**: 複数の記事で同じタグを使い回せる

---

### 9. ArticleTagLink（記事タグリンク）

**役割**: 記事とタグの多対多関連を管理。

#### カラム

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| article_id | BIGINT | NOT NULL | 記事ID（FK） |
| article_tag_id | BIGINT | NOT NULL | タグID（FK） |

#### 外部キー

- `article_id` → `article.article_id` (ON DELETE CASCADE)
- `article_tag_id` → `article_tag.article_tag_id` (ON DELETE CASCADE)

#### 制約

- PRIMARY KEY (`article_id`, `article_tag_id`)

#### 設計意図

- **多対多関連**: 1記事に複数タグ、1タグは複数記事に関連付け可能
- **フィルタリング**: タグベースの記事検索・絞り込みに使用

---

### 10. AlbumArticle（アルバム記事・お品書き用メタ）【廃止予定】

> **注意**: このテーブルは Article テーブルへの統合により廃止予定です。
> 既存データは Article テーブルへマイグレーションされます。

**役割**: Web のアルバム紹介記事・お品書き掲載で使うテキスト／ラベル情報。

#### カラム

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| album_id | BIGINT | NOT NULL | 主キー、アルバムID（FK） |
| intro_long | TEXT | NULL | 記事本文としての紹介コメント（ロング） |
| intro_short | TEXT | NULL | お品書き用のショートコメント |
| first_event_space | VARCHAR(100) | NULL | 初出イベントのスペース（例: "東X-00b"） |
| label_tag | VARCHAR(50) | NULL | お品書き用ラベル |

#### 外部キー

- `album_id` → `album.album_id` (ON DELETE CASCADE)

#### 制約

- `label_tag` は 'NEW', 'BEST_OF', 'COMPILATION', 'COLLAB', 'OTHER' のいずれか

#### 設計意図

- **1アルバムにつき 0 or 1 記事**: 記事がないアルバムも許容
- **お品書き情報の対応**:
  - 紹介コメント(ショート) → `intro_short`
  - 曲数 → Track テーブルの `album_id` ごとの COUNT で算出（DBクエリ）
  - ラベル → `label_tag`（新譜・総集編・コラボなど）
- **初出イベントの扱い**:
  - イベント自体は `Album.event_id` に紐づく
  - スペース情報はここにテキストで保持

---

### 11. AlbumDistribution（頒布条件／価格）

**役割**: 頒価／DL価格／デモリンクなど、作品側の頒布状態を管理。

#### カラム

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| album_id | BIGINT | NOT NULL | 主キー、アルバムID（FK） |
| physical_price | INTEGER | NULL | CDなどの物理頒価（税込円） |
| download_price | INTEGER | NULL | DL版価格（円） |
| demo_url | VARCHAR(500) | NULL | デモ音源へのリンク |
| note | TEXT | NULL | 補足メモ |

#### 外部キー

- `album_id` → `album.album_id` (ON DELETE CASCADE)

#### 制約

- `physical_price` と `download_price` は非負（NULL または >= 0）

#### 設計意図

- **1アルバムにつき 0 or 1 レコード**
- **価格情報の一元管理**: 物理版とDL版の価格を別々に管理
- **デモリンク**: SoundCloud, YouTube, 自サイトなどのURLを保持
- **補足メモ**: 「イベント頒布のみ」「DLは◯◯経由」などの自由記述

---

### 12. AlbumAcquisitionChannel（入手経路）

**役割**: 入手経路（委託ショップ、BOOTH、Bandcamp、自サイト通販など）を複数持つためのテーブル。

#### カラム

| カラム名 | 型 | NULL | 説明 |
|---------|-----|------|------|
| album_acquisition_id | BIGSERIAL | NOT NULL | 主キー |
| album_id | BIGINT | NOT NULL | アルバムID（FK） |
| channel_type | VARCHAR(50) | NOT NULL | チャネルタイプ |
| name | VARCHAR(255) | NOT NULL | 表示用の名前 |
| url | VARCHAR(500) | NULL | 詳細ページへのURL |
| note | TEXT | NULL | 補足 |

#### 外部キー

- `album_id` → `album.album_id` (ON DELETE CASCADE)

#### 制約

- `channel_type` は 'EVENT', 'ONLINE_SHOP', 'DL_SITE', 'STREAMING', 'OTHER' のいずれか

#### 設計意図

- **1アルバムに複数の入手経路を持てる**: 委託先が複数ある場合に対応
- **チャネルタイプ別の管理**:
  - EVENT: イベント現地
  - ONLINE_SHOP: オンラインショップ（例: メロンブックス）
  - DL_SITE: ダウンロードサイト（例: BOOTH）
  - STREAMING: ストリーミングサービス
  - OTHER: その他
- **役割の分離**:
  - `AlbumDistribution`: 作品側の価格状態
  - `AlbumAcquisitionChannel`: どこでどう入手できるかの販売チャネル情報

---

## ユースケースごとのデータ取得

### アルバム紹介記事ページ

- **コア情報**: Album, ArtistCredit, Event, Track, TrackTune
- **記事情報**: Article（`article_type = 'ALBUM'` かつ `album_id` が一致）
- **記事テキスト**: Article.body
- **ショート紹介**: Article.intro_short
- **初出イベント・スペース**: Event + AlbumArticle.first_event_space（※廃止予定）
- **頒価・DL価格**: AlbumDistribution
- **入手経路リンク**: AlbumAcquisitionChannel
- **デモリンク**: AlbumDistribution.demo_url
- **記事タグ**: ArticleTagLink → ArticleTag

### お品書き用カード

- **タイトル**: Article.title または Album.title
- **紹介コメント(ショート)**: Article.intro_short
- **頒価**: AlbumDistribution.physical_price
- **曲数**: `SELECT COUNT(*) FROM track WHERE album_id = ?`
- **ラベル**: ArticleTag 経由で表現（NEW、COLLAB など）
- **公開日**: Article.published_at

### 通常記事一覧

- **記事リスト**: Article（`article_type != 'ALBUM'` または `album_id IS NULL`）
- **フィルタリング**: ArticleTagLink + ArticleTag でタグ検索
- **公開記事のみ**: `is_public = TRUE`

---

## マイグレーション構成

| ファイル | テーブル | 説明 |
|---------|---------|------|
| V1__Create_artist_credit_table.sql | artist_credit | アーティスト名義 |
| V2__Create_event_table.sql | event | 発表イベント |
| V3__Create_album_table.sql | album | アルバム |
| V4__Create_tune_table.sql | tune | チューン（曲） |
| V5__Create_track_table.sql | track | トラック（録音） |
| V6__Create_track_tune_table.sql | track_tune | トラック内チューン構成 |
| V7__Create_album_article_table.sql | album_article | アルバム記事【廃止予定】 |
| V8__Create_album_distribution_table.sql | album_distribution | 頒布条件・価格 |
| V9__Create_album_acquisition_channel_table.sql | album_acquisition_channel | 入手経路 |
| V10__Create_article_table.sql | article | 記事（汎用） |
| V11__Create_article_tag_tables.sql | article_tag, article_tag_link | 記事タグ |
| V10__Create_article_table.sql | article | 記事（汎用） |
| V11__Create_article_tag_tables.sql | article_tag, article_tag_link | 記事タグとリンク |

---

## 今後の拡張予定

- アーティスト情報の詳細管理（別テーブル化）
- リリース版違い（リマスター版など）の管理
- プレイリスト機能
- ユーザーレビュー・評価機能
