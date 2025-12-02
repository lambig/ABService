-- ArtistCreditとEventテーブルを削除
-- これらはValue Objectとして扱われるため、専用のテーブルは不要

-- テーブルを削除（CASCADEで関連する外部キーも削除）
DROP TABLE IF EXISTS artist_credit CASCADE;
DROP TABLE IF EXISTS event CASCADE;
