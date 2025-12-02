-- AlbumAcquisitionChannel: 入手経路情報
-- 入手経路（委託ショップ、BOOTH、Bandcamp、自サイト通販など）を複数持つためのテーブル
-- 1 アルバムに対して複数の入手経路が存在可能
CREATE TABLE album_acquisition_channel (
    album_acquisition_id BIGSERIAL PRIMARY KEY,
    album_id BIGINT NOT NULL,
    channel_type VARCHAR(50) NOT NULL CHECK (channel_type IN ('EVENT', 'ONLINE_SHOP', 'DL_SITE', 'STREAMING', 'OTHER')),
    name VARCHAR(255) NOT NULL,
    url VARCHAR(500),
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    version_no INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT fk_album_acquisition_channel_album FOREIGN KEY (album_id)
        REFERENCES album(album_id) ON DELETE CASCADE
);

-- インデックス
CREATE INDEX idx_album_acquisition_channel_album ON album_acquisition_channel(album_id);
CREATE INDEX idx_album_acquisition_channel_type ON album_acquisition_channel(channel_type);

-- コメント
COMMENT ON TABLE album_acquisition_channel IS 'アルバム入手経路テーブル';
COMMENT ON COLUMN album_acquisition_channel.album_acquisition_id IS '入手経路ID（主キー）';
COMMENT ON COLUMN album_acquisition_channel.album_id IS 'アルバムID（外部キー）';
COMMENT ON COLUMN album_acquisition_channel.channel_type IS 'チャネルタイプ: EVENT（イベント）/ ONLINE_SHOP（オンラインショップ）/ DL_SITE（DLサイト）/ STREAMING（ストリーミング）/ OTHER（その他）';
COMMENT ON COLUMN album_acquisition_channel.name IS '表示用の名前（例: "自家通販", "BOOTH", "メロンブックス"）';
COMMENT ON COLUMN album_acquisition_channel.url IS 'その入手経路の詳細ページへのURL（イベント現地のみの場合はNULL）';
COMMENT ON COLUMN album_acquisition_channel.note IS '補足（「◯月末まで」等）';
COMMENT ON COLUMN album_acquisition_channel.created_at IS '作成日時';
COMMENT ON COLUMN album_acquisition_channel.created_by IS '作成者';
COMMENT ON COLUMN album_acquisition_channel.updated_at IS '更新日時';
COMMENT ON COLUMN album_acquisition_channel.updated_by IS '更新者';
COMMENT ON COLUMN album_acquisition_channel.version_no IS 'バージョン番号（楽観的ロック用）';
