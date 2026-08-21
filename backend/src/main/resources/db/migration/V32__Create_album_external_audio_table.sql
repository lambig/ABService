-- Albumの外部音源（試聴用の外部サービス埋め込み）を作成
-- 音源実体は自前ホストせず外部サービスに委ねるため、保持するのは埋め込み元URLと表示順のみ
-- 1アルバムに複数（N件）持てる。トラック単位の紐付けは持たない

CREATE TABLE album_external_audio (
    album_external_audio_id BIGSERIAL PRIMARY KEY,
    domain_id VARCHAR(255) NOT NULL,
    album_id BIGINT NOT NULL,
    display_order INTEGER NOT NULL,
    url VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_service VARCHAR(255),
    updated_by_service VARCHAR(255),
    created_by_user VARCHAR(255),
    updated_by_user VARCHAR(255),
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_album_external_audio_domain_id UNIQUE (domain_id),
    CONSTRAINT fk_album_external_audio_album FOREIGN KEY (album_id)
        REFERENCES album(album_id) ON DELETE CASCADE,
    -- 表示順の入れ替えは行ごとのUPDATEで中間状態が重複するため、コミット時検証にする（trackのtrack_noと同じ理由）
    CONSTRAINT uk_album_external_audio_display_order UNIQUE (album_id, display_order)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX idx_album_external_audio_album ON album_external_audio(album_id);

COMMENT ON TABLE album_external_audio IS 'アルバムの外部音源（外部サービスの埋め込み元URL）';
COMMENT ON COLUMN album_external_audio.album_external_audio_id IS '主キー';
COMMENT ON COLUMN album_external_audio.domain_id IS 'ドメインID（UUIDv7形式、ビジネス識別子）';
COMMENT ON COLUMN album_external_audio.album_id IS 'アルバムID（外部キー）';
COMMENT ON COLUMN album_external_audio.display_order IS 'アルバム内での表示順（1, 2, 3, ...）';
COMMENT ON COLUMN album_external_audio.url IS '外部音源の埋め込み元URL（許可ホストはドメイン層のExternalAudioUrlが強制する）';
COMMENT ON COLUMN album_external_audio.created_at IS '作成日時';
COMMENT ON COLUMN album_external_audio.updated_at IS '更新日時';
COMMENT ON COLUMN album_external_audio.created_by_service IS '作成したサービス名';
COMMENT ON COLUMN album_external_audio.updated_by_service IS '更新したサービス名';
COMMENT ON COLUMN album_external_audio.created_by_user IS '作成したユーザーID';
COMMENT ON COLUMN album_external_audio.updated_by_user IS '更新したユーザーID';
COMMENT ON COLUMN album_external_audio.version IS 'バージョン番号（楽観ロック用）';
