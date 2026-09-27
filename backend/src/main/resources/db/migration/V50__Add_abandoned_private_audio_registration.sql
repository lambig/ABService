ALTER TABLE private_audio_registration DROP CONSTRAINT private_audio_state;
ALTER TABLE private_audio_registration ADD CONSTRAINT private_audio_state
    CHECK (state IN ('PENDING', 'INSPECTED', 'CONFIRMED', 'EXPIRED', 'ABANDONED'));
ALTER TABLE private_audio_registration DROP CONSTRAINT private_audio_metadata;
ALTER TABLE private_audio_registration ADD CONSTRAINT private_audio_metadata CHECK (
    (state IN ('PENDING', 'EXPIRED')
        AND num_nonnulls(byte_length, sha256, sample_rate, channels, bits_per_sample, total_samples) = 0)
    OR
    (state IN ('INSPECTED', 'CONFIRMED', 'ABANDONED')
        AND num_nonnulls(byte_length, sha256, sample_rate, channels, bits_per_sample, total_samples) = 6
        AND byte_length BETWEEN 1 AND 268435456
        AND sha256 ~ '^[0-9a-f]{64}$'
        AND sample_rate BETWEEN 8000 AND 96000
        AND channels IN (1, 2)
        AND bits_per_sample IN (16, 24)
        AND total_samples BETWEEN 1 AND sample_rate::bigint * 7200)
);
CREATE INDEX private_audio_inspected_expiry ON private_audio_registration (expires_at, audio_id)
    WHERE state = 'INSPECTED';
COMMENT ON COLUMN private_audio_registration.state IS
    'PENDING→INSPECTED→CONFIRMED、検査前の失効はEXPIRED、保存未確認で受付終了はABANDONED（実体照合による明示復旧可）';
