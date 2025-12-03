-- TrackからISRC列を削除
ALTER TABLE track DROP COLUMN IF EXISTS isrc;

-- AlbumにISDN列を追加
ALTER TABLE album ADD COLUMN isdn VARCHAR(20);

-- コメント
COMMENT ON COLUMN album.isdn IS 'ISDN（国際標準同人誌番号、13桁）';
