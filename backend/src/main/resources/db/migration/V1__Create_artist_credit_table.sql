-- ArtistCredit: アーティスト名義を管理
-- 「X」「X feat. Y」「ユニット名」など、ジャケットに書かれる名義文字列を管理する
CREATE TABLE artist_credit (
    artist_credit_id BIGSERIAL PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    sort_key VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    version_no INTEGER NOT NULL DEFAULT 1
);

-- インデックス
CREATE INDEX idx_artist_credit_display_name ON artist_credit(display_name);
CREATE INDEX idx_artist_credit_sort_key ON artist_credit(sort_key);

-- コメント
COMMENT ON TABLE artist_credit IS 'アーティスト名義テーブル';
COMMENT ON COLUMN artist_credit.artist_credit_id IS 'アーティスト名義ID（主キー）';
COMMENT ON COLUMN artist_credit.display_name IS '表示名（例: "Foo Bar", "Foo Bar feat. Baz"）';
COMMENT ON COLUMN artist_credit.sort_key IS '並び替え用キー';
COMMENT ON COLUMN artist_credit.created_at IS '作成日時';
COMMENT ON COLUMN artist_credit.created_by IS '作成者';
COMMENT ON COLUMN artist_credit.updated_at IS '更新日時';
COMMENT ON COLUMN artist_credit.updated_by IS '更新者';
COMMENT ON COLUMN artist_credit.version_no IS 'バージョン番号（楽観的ロック用）';
