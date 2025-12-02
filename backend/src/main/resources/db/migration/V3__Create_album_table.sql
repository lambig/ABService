-- Album: アルバム情報
-- 1つのアルバム（版違いは当面考えない）を表す
CREATE TABLE album (
    album_id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    release_date DATE,
    artist_credit_id BIGINT NOT NULL,
    event_id BIGINT,
    catalog_number VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    version_no INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT fk_album_artist_credit FOREIGN KEY (artist_credit_id) 
        REFERENCES artist_credit(artist_credit_id),
    CONSTRAINT fk_album_event FOREIGN KEY (event_id) 
        REFERENCES event(event_id)
);

-- インデックス
CREATE INDEX idx_album_title ON album(title);
CREATE INDEX idx_album_release_date ON album(release_date);
CREATE INDEX idx_album_artist_credit ON album(artist_credit_id);
CREATE INDEX idx_album_event ON album(event_id);

-- コメント
COMMENT ON TABLE album IS 'アルバムテーブル';
COMMENT ON COLUMN album.album_id IS 'アルバムID（主キー）';
COMMENT ON COLUMN album.title IS 'アルバムタイトル';
COMMENT ON COLUMN album.release_date IS '発表年月日';
COMMENT ON COLUMN album.artist_credit_id IS 'アルバム全体のアーティスト名義（外部キー）';
COMMENT ON COLUMN album.event_id IS '発表イベント（外部キー、なければNULL）';
COMMENT ON COLUMN album.catalog_number IS 'カタログナンバー';
COMMENT ON COLUMN album.created_at IS '作成日時';
COMMENT ON COLUMN album.created_by IS '作成者';
COMMENT ON COLUMN album.updated_at IS '更新日時';
COMMENT ON COLUMN album.updated_by IS '更新者';
COMMENT ON COLUMN album.version_no IS 'バージョン番号（楽観的ロック用）';
