-- EventReleasedAt（初出情報）を単一日程に単純化したため、複数日程管理用の
-- album_event_date_spaceテーブルを廃止する。複数日程の配置管理はConfirmedEvent側
-- （EventToParticipate）が引き続き担う。未リリースのためデータ移行は不要。

DROP TABLE IF EXISTS album_event_date_space;

-- album.event_date / album.event_space_number は正式なカラムとして扱う
COMMENT ON COLUMN album.event_date IS 'イベント開催日（初出）';
COMMENT ON COLUMN album.event_space_number IS 'イベントスペース番号（初出。例：東A-01）';
