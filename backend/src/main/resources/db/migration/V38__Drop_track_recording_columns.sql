-- Track から録音を前提とした列を削除する
-- このプロジェクトに録音という前提は存在しない（作品はオーディオファイルのエクスポートが前提で、
-- ライブも現時点で発生する余地がない）。同じ migration で入った duration_msec / isrc も
-- 同じ性質の残存項目として V20 / V21 で削除済みである。
--
-- Track は「アルバムを構成するトラック」であり、同じチューン構成でも別 Track になり得るのは
-- 録音違いではなく revise（改訂）のためである。

DROP INDEX IF EXISTS idx_track_recording_date;

ALTER TABLE track DROP COLUMN IF EXISTS recording_date;
ALTER TABLE track DROP COLUMN IF EXISTS recording_place;
ALTER TABLE track DROP COLUMN IF EXISTS is_live;

COMMENT ON TABLE track IS 'トラック（アルバムを構成するトラック）テーブル';
