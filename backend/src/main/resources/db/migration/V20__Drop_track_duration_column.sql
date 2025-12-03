-- Track テーブルから duration_msec カラムを削除
-- Duration 情報は不要となったため削除する

ALTER TABLE track DROP COLUMN IF EXISTS duration_msec;

-- コメント更新
COMMENT ON TABLE track IS 'トラック（録音単位）テーブル - Duration情報削除済み';
