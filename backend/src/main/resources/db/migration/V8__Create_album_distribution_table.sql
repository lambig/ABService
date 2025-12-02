-- AlbumDistribution: 頒布条件／価格情報
-- 頒価／DL価格／デモリンクなど、作品側の頒布状態を管理
-- 1 アルバムにつき 0 or 1 レコード
CREATE TABLE album_distribution (
    album_id BIGINT PRIMARY KEY,
    physical_price INTEGER,
    download_price INTEGER,
    demo_url VARCHAR(500),
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    version_no INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT fk_album_distribution_album FOREIGN KEY (album_id)
        REFERENCES album(album_id) ON DELETE CASCADE,
    -- 価格が負にならないことを保証
    CONSTRAINT chk_album_distribution_physical_price CHECK (physical_price IS NULL OR physical_price >= 0),
    CONSTRAINT chk_album_distribution_download_price CHECK (download_price IS NULL OR download_price >= 0)
);

-- コメント
COMMENT ON TABLE album_distribution IS 'アルバム頒布条件・価格情報テーブル';
COMMENT ON COLUMN album_distribution.album_id IS 'アルバムID（主キー、外部キー）';
COMMENT ON COLUMN album_distribution.physical_price IS 'CDなどの物理頒価（税込円）';
COMMENT ON COLUMN album_distribution.download_price IS 'DL版価格（円）';
COMMENT ON COLUMN album_distribution.demo_url IS 'デモ音源へのリンク（SoundCloud, YouTube, 自サイトなど）';
COMMENT ON COLUMN album_distribution.note IS '補足メモ（「イベント頒布のみ」「DLは◯◯経由」など）';
COMMENT ON COLUMN album_distribution.created_at IS '作成日時';
COMMENT ON COLUMN album_distribution.created_by IS '作成者';
COMMENT ON COLUMN album_distribution.updated_at IS '更新日時';
COMMENT ON COLUMN album_distribution.updated_by IS '更新者';
COMMENT ON COLUMN album_distribution.version_no IS 'バージョン番号（楽観的ロック用）';
