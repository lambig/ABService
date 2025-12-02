-- イベント情報にスペース番号を追加
-- PresentationOpportunity（発表機会）としてイベント情報を扱うため、スペース番号を追加

-- event_space_numberカラムを追加
ALTER TABLE album ADD COLUMN IF NOT EXISTS event_space_number VARCHAR(50);

-- インデックスを作成
CREATE INDEX IF NOT EXISTS idx_album_event_space_number ON album(event_space_number);

-- コメントを追加
COMMENT ON COLUMN album.event_space_number IS 'イベントスペース番号（例：東A-01）';
