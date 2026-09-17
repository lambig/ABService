-- 初出イベントに頒布サークル名を持たせる（#415）
-- 公開サイトの頒布情報は「日付・イベント名・スペース・サークル名」の並びで読む。サークル名は
-- そのイベントで作品を出した名で、作品の名義（artist_display_name）とは別に持つ——合同や委託では
-- 名義と違う名で出ることがあり、名義から導けない。
--
-- 他のイベント項目（event_place / event_space_number / event_note）と同じく album に埋め込む。
-- 初出は1点の事実で、イベントの項目を別表に分ける理由が無い（V17 で埋め込みに揃えた判断のまま）。

ALTER TABLE album ADD COLUMN event_circle_name VARCHAR(255);

COMMENT ON COLUMN album.event_circle_name IS '初出イベントの頒布サークル名（名義と違うことがある）';
