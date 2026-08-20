-- トラック番号の一意制約を遅延検証（DEFERRABLE INITIALLY DEFERRED）に変更する
-- 順序変更（reorderTracks）は既存トラックのtrack_noを行ごとに更新するため、
-- トランザクション内の中間状態で一時的に他行のtrack_noと重複する（スワップ）。
-- 即時検証（デフォルト）のままではこの中間状態で違反となるため、コミット時検証へ変更する。
ALTER TABLE track DROP CONSTRAINT uq_track_album_track_no;
ALTER TABLE track ADD CONSTRAINT uq_track_album_track_no UNIQUE (album_id, track_no) DEFERRABLE INITIALLY DEFERRED;
