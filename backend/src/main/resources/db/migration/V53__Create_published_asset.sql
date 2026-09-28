-- 確定した画像アセットの実測値。試聴端末へ配布する表示素材の byteLength と SHA-256 を、確定時に保管先が計算した値で持つ。
-- 配信URLや公開状態は持たない。作品との関連は album.cover_image_key が参照するキーで結ぶ。
CREATE TABLE published_asset (
    asset_key VARCHAR(255) PRIMARY KEY,
    content_type VARCHAR(100) NOT NULL,
    byte_length BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT published_asset_key CHECK (asset_key ~ '^[A-Za-z0-9][A-Za-z0-9._-]*$'),
    CONSTRAINT published_asset_length CHECK (byte_length > 0),
    CONSTRAINT published_asset_digest CHECK (sha256 ~ '^[0-9a-f]{64}$')
);
COMMENT ON TABLE published_asset IS '確定した画像アセットの実測値。配布パッケージの表示素材の識別に使い、公開サイトの配信には関与しない';
COMMENT ON COLUMN published_asset.asset_key IS 'アセットキー（配信キー）。album.cover_image_key が参照する';
COMMENT ON COLUMN published_asset.content_type IS '実体から判定した Content-Type';
COMMENT ON COLUMN published_asset.byte_length IS '確定した実体のバイト数';
COMMENT ON COLUMN published_asset.sha256 IS '確定した実体のSHA-256（小文字hex）。保管先が確定時に計算した値';
COMMENT ON COLUMN published_asset.confirmed_at IS 'DB時計による記録時刻';
