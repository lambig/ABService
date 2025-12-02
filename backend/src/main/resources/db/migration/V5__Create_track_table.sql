-- Track: トラック（録音単位）
-- アルバム上の1トラックの録音を表す
-- 録音違い（スタジオ版／ライブ版など）は別Track行として扱う
CREATE TABLE track (
    track_id BIGSERIAL PRIMARY KEY,
    album_id BIGINT NOT NULL,
    track_no INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    artist_credit_id BIGINT,
    -- 録音メタデータ
    recording_date DATE,
    recording_place VARCHAR(255),
    duration_msec INTEGER,
    is_live BOOLEAN,
    isrc VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    version_no INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT fk_track_album FOREIGN KEY (album_id)
        REFERENCES album(album_id),
    CONSTRAINT fk_track_artist_credit FOREIGN KEY (artist_credit_id)
        REFERENCES artist_credit(artist_credit_id),
    -- アルバム内でのトラック番号は一意
    CONSTRAINT uq_track_album_track_no UNIQUE (album_id, track_no)
);

-- インデックス
CREATE INDEX idx_track_album ON track(album_id);
CREATE INDEX idx_track_title ON track(title);
CREATE INDEX idx_track_artist_credit ON track(artist_credit_id);
CREATE INDEX idx_track_recording_date ON track(recording_date);

-- コメント
COMMENT ON TABLE track IS 'トラック（録音単位）テーブル';
COMMENT ON COLUMN track.track_id IS 'トラックID（主キー）';
COMMENT ON COLUMN track.album_id IS 'アルバムID（外部キー）';
COMMENT ON COLUMN track.track_no IS 'アルバム内のトラック番号';
COMMENT ON COLUMN track.title IS 'トラックのタイトル（セット全体の名前でもよい）';
COMMENT ON COLUMN track.artist_credit_id IS 'トラック個別のアーティスト名義（NULLの場合はAlbumのartist_credit_idを継承）';
COMMENT ON COLUMN track.recording_date IS '録音日';
COMMENT ON COLUMN track.recording_place IS '録音場所';
COMMENT ON COLUMN track.duration_msec IS '再生時間（ミリ秒）';
COMMENT ON COLUMN track.is_live IS 'ライブ録音フラグ';
COMMENT ON COLUMN track.isrc IS 'ISRC（国際標準レコーディングコード）';
COMMENT ON COLUMN track.created_at IS '作成日時';
COMMENT ON COLUMN track.created_by IS '作成者';
COMMENT ON COLUMN track.updated_at IS '更新日時';
COMMENT ON COLUMN track.updated_by IS '更新者';
COMMENT ON COLUMN track.version_no IS 'バージョン番号（楽観的ロック用）';
