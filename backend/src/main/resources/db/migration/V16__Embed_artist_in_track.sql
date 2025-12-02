-- Trackテーブルのartist_credit_id参照をValue Objectに変更

-- 既存データの退避
DO $$
BEGIN
    CREATE TEMPORARY TABLE IF NOT EXISTS temp_track_data AS
    SELECT 
        t.track_id,
        ac.display_name as artist_display_name,
        ac.sort_key as artist_sort_key
    FROM track t
    LEFT JOIN artist_credit ac ON t.artist_credit_id = ac.domain_id
    WHERE t.artist_credit_id IS NOT NULL;
END $$;

-- 外部キー制約を削除
ALTER TABLE track DROP CONSTRAINT IF EXISTS fk_track_artist_credit;

-- 既存のカラムを削除
ALTER TABLE track DROP COLUMN IF EXISTS artist_credit_id;

-- 新しいカラムを追加
ALTER TABLE track ADD COLUMN IF NOT EXISTS artist_display_name VARCHAR(255);
ALTER TABLE track ADD COLUMN IF NOT EXISTS artist_sort_key VARCHAR(255);

-- データを復元
UPDATE track t
SET 
    artist_display_name = tmp.artist_display_name,
    artist_sort_key = tmp.artist_sort_key
FROM temp_track_data tmp
WHERE t.track_id = tmp.track_id;

-- 古いインデックスを削除
DROP INDEX IF EXISTS idx_track_artist_credit;

-- インデックスを作成
CREATE INDEX IF NOT EXISTS idx_track_artist_display_name ON track(artist_display_name);

-- コメントを追加
COMMENT ON COLUMN track.artist_display_name IS 'トラックアーティスト表示名（NULLの場合はアルバムのアーティストを継承）';
COMMENT ON COLUMN track.artist_sort_key IS 'トラックアーティストソートキー';
