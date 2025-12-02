-- ArtistCreditとEventの情報をAlbumテーブルに埋め込む
-- これらのエンティティをValue Objectとして扱う

-- 既存データの退避（もしデータがあれば）
DO $$
BEGIN
    -- 一時テーブルを作成してデータを退避
    CREATE TEMPORARY TABLE IF NOT EXISTS temp_album_data AS
    SELECT 
        a.album_id,
        a.domain_id,
        COALESCE(ac.display_name, 'Unknown Artist') as artist_display_name,
        ac.sort_key as artist_sort_key,
        e.name as event_name,
        e.date as event_date,
        e.place as event_place,
        e.note as event_note
    FROM album a
    LEFT JOIN artist_credit ac ON a.artist_credit_id = ac.domain_id
    LEFT JOIN event e ON a.event_id = e.domain_id;
END $$;

-- 外部キー制約を削除（存在しない可能性もあるのでIF EXISTSを使用）
ALTER TABLE album DROP CONSTRAINT IF EXISTS fk_album_artist_credit;
ALTER TABLE album DROP CONSTRAINT IF EXISTS fk_album_event;

-- 既存のカラムを削除
ALTER TABLE album DROP COLUMN IF EXISTS artist_credit_id;
ALTER TABLE album DROP COLUMN IF EXISTS event_id;

-- 新しいカラムを追加（ArtistCredit情報）
ALTER TABLE album ADD COLUMN IF NOT EXISTS artist_display_name VARCHAR(255);
ALTER TABLE album ADD COLUMN IF NOT EXISTS artist_sort_key VARCHAR(255);

-- 新しいカラムを追加（Event情報）
ALTER TABLE album ADD COLUMN IF NOT EXISTS event_name VARCHAR(255);
ALTER TABLE album ADD COLUMN IF NOT EXISTS event_date DATE;
ALTER TABLE album ADD COLUMN IF NOT EXISTS event_place VARCHAR(255);
ALTER TABLE album ADD COLUMN IF NOT EXISTS event_note TEXT;

-- データを復元
UPDATE album a
SET 
    artist_display_name = COALESCE(t.artist_display_name, 'Unknown Artist'),
    artist_sort_key = t.artist_sort_key,
    event_name = t.event_name,
    event_date = t.event_date,
    event_place = t.event_place,
    event_note = t.event_note
FROM temp_album_data t
WHERE a.album_id = t.album_id;

-- NOT NULL制約を追加
ALTER TABLE album ALTER COLUMN artist_display_name SET NOT NULL;

-- 古いインデックスを削除（存在する場合）
DROP INDEX IF EXISTS idx_album_artist_credit;
DROP INDEX IF EXISTS idx_album_event;

-- インデックスを作成
CREATE INDEX IF NOT EXISTS idx_album_artist_display_name ON album(artist_display_name);
CREATE INDEX IF NOT EXISTS idx_album_artist_sort_key ON album(artist_sort_key);
CREATE INDEX IF NOT EXISTS idx_album_event_name ON album(event_name);
CREATE INDEX IF NOT EXISTS idx_album_event_date ON album(event_date);

-- コメントを追加
COMMENT ON COLUMN album.artist_display_name IS 'アーティスト表示名';
COMMENT ON COLUMN album.artist_sort_key IS 'アーティストソートキー';
COMMENT ON COLUMN album.event_name IS 'イベント名';
COMMENT ON COLUMN album.event_date IS 'イベント開催日';
COMMENT ON COLUMN album.event_place IS 'イベント会場';
COMMENT ON COLUMN album.event_note IS 'イベント補足情報';
