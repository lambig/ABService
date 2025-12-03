-- V23: album_event_date_spaceテーブルの監査カラムを標準形式に修正

-- 既存の監査カラムを削除
ALTER TABLE album_event_date_space
    DROP COLUMN created_by,
    DROP COLUMN updated_by;

-- 標準的な監査カラムを追加
ALTER TABLE album_event_date_space
    ADD COLUMN created_by_service VARCHAR(255) NOT NULL DEFAULT 'system',
    ADD COLUMN created_by_user VARCHAR(255),
    ADD COLUMN updated_by_service VARCHAR(255) NOT NULL DEFAULT 'system',
    ADD COLUMN updated_by_user VARCHAR(255);

COMMENT ON COLUMN album_event_date_space.created_by_service IS '作成したサービス名';
COMMENT ON COLUMN album_event_date_space.created_by_user IS '作成したユーザーID';
COMMENT ON COLUMN album_event_date_space.updated_by_service IS '更新したサービス名';
COMMENT ON COLUMN album_event_date_space.updated_by_user IS '更新したユーザーID';
