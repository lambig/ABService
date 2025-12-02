-- アルバムのイベント日付・スペース情報テーブルを作成
-- 複数日程参加に対応するため、日付とスペース番号の組み合わせを別テーブル化

-- album_event_date_spaceテーブルを作成
CREATE TABLE IF NOT EXISTS album_event_date_space (
    album_event_date_space_id BIGSERIAL PRIMARY KEY,
    album_id BIGINT NOT NULL,
    event_date DATE NOT NULL,
    space_number VARCHAR(50),
    
    -- 監査カラム
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    
    -- 外部キー制約
    CONSTRAINT fk_album_event_date_space_album FOREIGN KEY (album_id)
        REFERENCES album(album_id) ON DELETE CASCADE,
    
    -- 同一アルバム・同一日付でのスペース番号の一意性を保証
    CONSTRAINT uk_album_event_date UNIQUE (album_id, event_date)
);

-- インデックスを作成
CREATE INDEX idx_album_event_date_space_album_id ON album_event_date_space(album_id);
CREATE INDEX idx_album_event_date_space_event_date ON album_event_date_space(event_date);
CREATE INDEX idx_album_event_date_space_space_number ON album_event_date_space(space_number);

-- 既存データの移行：event_dateとevent_space_numberがある場合は移行
INSERT INTO album_event_date_space (album_id, event_date, space_number, created_by, updated_by)
SELECT 
    album_id,
    event_date,
    event_space_number,
    'migration',
    'migration'
FROM album
WHERE event_date IS NOT NULL;

-- 古いカラムは後方互換性のため一旦残す
-- event_date と event_space_number は削除せず、非推奨として扱う
COMMENT ON COLUMN album.event_date IS '(非推奨) イベント開催日 - album_event_date_spaceテーブルを使用してください';
COMMENT ON COLUMN album.event_space_number IS '(非推奨) イベントスペース番号 - album_event_date_spaceテーブルを使用してください';

-- コメントを追加
COMMENT ON TABLE album_event_date_space IS 'アルバムのイベント日付・スペース情報（複数日参加対応）';
COMMENT ON COLUMN album_event_date_space.album_event_date_space_id IS 'プライマリキー';
COMMENT ON COLUMN album_event_date_space.album_id IS 'アルバムID（外部キー）';
COMMENT ON COLUMN album_event_date_space.event_date IS 'イベント開催日';
COMMENT ON COLUMN album_event_date_space.space_number IS 'スペース番号（例：東A-01）';
