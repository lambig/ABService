-- Event: 発表イベント（コミケ・M3・ライブなど）
CREATE TABLE event (
    event_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    date DATE,
    place VARCHAR(255),
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    version_no INTEGER NOT NULL DEFAULT 1
);

-- インデックス
CREATE INDEX idx_event_name ON event(name);
CREATE INDEX idx_event_date ON event(date);

-- コメント
COMMENT ON TABLE event IS '発表イベントテーブル';
COMMENT ON COLUMN event.event_id IS 'イベントID（主キー）';
COMMENT ON COLUMN event.name IS 'イベント名';
COMMENT ON COLUMN event.date IS '開催日';
COMMENT ON COLUMN event.place IS '会場名';
COMMENT ON COLUMN event.note IS '補足情報';
COMMENT ON COLUMN event.created_at IS '作成日時';
COMMENT ON COLUMN event.created_by IS '作成者';
COMMENT ON COLUMN event.updated_at IS '更新日時';
COMMENT ON COLUMN event.updated_by IS '更新者';
COMMENT ON COLUMN event.version_no IS 'バージョン番号（楽観的ロック用）';
