-- 試聴端末の資格情報。管理APIキーを端末へ渡さず、期限付きトークンのdigestだけを持つ。配布認可はこの行と別に判定する。
CREATE TABLE listening_device (
    device_id UUID PRIMARY KEY,
    label VARCHAR(100) NOT NULL,
    token_digest VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT listening_device_label CHECK (length(label) BETWEEN 1 AND 100),
    CONSTRAINT listening_device_digest CHECK (token_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT listening_device_expiry CHECK (expires_at > created_at),
    CONSTRAINT listening_device_revocation CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);
COMMENT ON TABLE listening_device IS '試聴端末の資格情報。準備時の認証にだけ使い、公開状態・配布内容は持たない';
COMMENT ON COLUMN listening_device.device_id IS '内部で発行する端末の不変ID';
COMMENT ON COLUMN listening_device.label IS '運用者が端末を見分けるための表示名';
COMMENT ON COLUMN listening_device.token_digest IS '端末トークンのSHA-256（小文字hex）。トークン本体は保存しない';
COMMENT ON COLUMN listening_device.created_at IS 'DB時計による発行時刻';
COMMENT ON COLUMN listening_device.expires_at IS 'トークンの有効期限。利用による延長はない';
COMMENT ON COLUMN listening_device.revoked_at IS '管理者が失効させた時刻。NULLは失効していない';
