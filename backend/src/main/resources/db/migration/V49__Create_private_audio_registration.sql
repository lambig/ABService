-- 音源登録の技術的な処理状態。公開データ世代やAlbumの公開可否を変更しない。
CREATE TABLE private_audio_registration (
    audio_id UUID PRIMARY KEY,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL,
    byte_length BIGINT,
    sha256 VARCHAR(64),
    sample_rate INTEGER,
    channels INTEGER,
    bits_per_sample INTEGER,
    total_samples BIGINT,
    CONSTRAINT private_audio_expiry CHECK (expires_at > created_at),
    CONSTRAINT private_audio_state CHECK (state IN ('PENDING', 'INSPECTED', 'CONFIRMED', 'EXPIRED')),
    CONSTRAINT private_audio_metadata CHECK (
        (state IN ('PENDING', 'EXPIRED')
            AND num_nonnulls(byte_length, sha256, sample_rate, channels, bits_per_sample, total_samples) = 0)
        OR
        (state IN ('INSPECTED', 'CONFIRMED')
            AND num_nonnulls(byte_length, sha256, sample_rate, channels, bits_per_sample, total_samples) = 6
            AND byte_length BETWEEN 1 AND 268435456
            AND sha256 ~ '^[0-9a-f]{64}$'
            AND sample_rate BETWEEN 8000 AND 96000
            AND channels IN (1, 2)
            AND bits_per_sample IN (16, 24)
            AND total_samples BETWEEN 1 AND sample_rate::bigint * 7200)
    )
);
CREATE INDEX private_audio_pending_expiry ON private_audio_registration (expires_at, audio_id)
    WHERE state = 'PENDING';
COMMENT ON TABLE private_audio_registration IS '非公開音源の登録・検査・確定状態（配布認可や作品との関連付けは別）';
COMMENT ON COLUMN private_audio_registration.audio_id IS '内部で発行する保存実体と共通の不変ID';
COMMENT ON COLUMN private_audio_registration.state IS 'PENDING→INSPECTED→CONFIRMED、検査前の期限切れのみEXPIRED';
COMMENT ON COLUMN private_audio_registration.created_at IS 'DB時計による予約時刻';
COMMENT ON COLUMN private_audio_registration.expires_at IS '検査前の予約期限。INSPECTEDの復旧期限や実体削除時刻ではない';
COMMENT ON COLUMN private_audio_registration.byte_length IS '検査スナップショットの実測バイト数';
COMMENT ON COLUMN private_audio_registration.sha256 IS '検査スナップショット全体のSHA-256（小文字hex）';
COMMENT ON COLUMN private_audio_registration.sample_rate IS '検査済みサンプルレート（Hz）';
COMMENT ON COLUMN private_audio_registration.channels IS '検査済みチャンネル数';
COMMENT ON COLUMN private_audio_registration.bits_per_sample IS '検査済み量子化ビット数';
COMMENT ON COLUMN private_audio_registration.total_samples IS '検査済みチャンネル当たりのサンプル数';
