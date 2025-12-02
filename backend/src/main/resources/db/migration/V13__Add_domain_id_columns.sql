-- ============================================================================
-- V13: Add domain_id columns to all tables
-- ============================================================================
--
-- 全テーブルにドメインID列（domain_id UUID）を追加する。
-- ドメインIDはビジネス識別子として使用され、データベースの主キー（BIGSERIAL）とは独立している。
--
-- 既存データに対しては、gen_random_uuid()でUUIDを生成する。
-- 本来はUUIDv7を使用すべきだが、PostgreSQLにはネイティブサポートがないため、
-- 新規レコードはアプリケーション層でUUIDv7を生成して設定する。
-- ============================================================================

-- 1. artist_credit テーブル
ALTER TABLE artist_credit
    ADD COLUMN domain_id UUID;

-- 既存データにUUIDを生成
UPDATE artist_credit
SET domain_id = gen_random_uuid()
WHERE domain_id IS NULL;

-- NOT NULL制約とUNIQUE制約を追加
ALTER TABLE artist_credit
    ALTER COLUMN domain_id SET NOT NULL,
    ADD CONSTRAINT uk_artist_credit_domain_id UNIQUE (domain_id);

-- インデックス作成
CREATE INDEX idx_artist_credit_domain_id ON artist_credit(domain_id);

-- コメント追加
COMMENT ON COLUMN artist_credit.domain_id IS 'ドメインID（UUIDv7形式、ビジネス識別子）';

-- 2. event テーブル
ALTER TABLE event
    ADD COLUMN domain_id UUID;

UPDATE event
SET domain_id = gen_random_uuid()
WHERE domain_id IS NULL;

ALTER TABLE event
    ALTER COLUMN domain_id SET NOT NULL,
    ADD CONSTRAINT uk_event_domain_id UNIQUE (domain_id);

CREATE INDEX idx_event_domain_id ON event(domain_id);

COMMENT ON COLUMN event.domain_id IS 'ドメインID（UUIDv7形式、ビジネス識別子）';

-- 3. album テーブル
ALTER TABLE album
    ADD COLUMN domain_id UUID;

UPDATE album
SET domain_id = gen_random_uuid()
WHERE domain_id IS NULL;

ALTER TABLE album
    ALTER COLUMN domain_id SET NOT NULL,
    ADD CONSTRAINT uk_album_domain_id UNIQUE (domain_id);

CREATE INDEX idx_album_domain_id ON album(domain_id);

COMMENT ON COLUMN album.domain_id IS 'ドメインID（UUIDv7形式、ビジネス識別子）';

-- 4. tune テーブル
ALTER TABLE tune
    ADD COLUMN domain_id UUID;

UPDATE tune
SET domain_id = gen_random_uuid()
WHERE domain_id IS NULL;

ALTER TABLE tune
    ALTER COLUMN domain_id SET NOT NULL,
    ADD CONSTRAINT uk_tune_domain_id UNIQUE (domain_id);

CREATE INDEX idx_tune_domain_id ON tune(domain_id);

COMMENT ON COLUMN tune.domain_id IS 'ドメインID（UUIDv7形式、ビジネス識別子）';

-- 5. track テーブル
ALTER TABLE track
    ADD COLUMN domain_id UUID;

UPDATE track
SET domain_id = gen_random_uuid()
WHERE domain_id IS NULL;

ALTER TABLE track
    ALTER COLUMN domain_id SET NOT NULL,
    ADD CONSTRAINT uk_track_domain_id UNIQUE (domain_id);

CREATE INDEX idx_track_domain_id ON track(domain_id);

COMMENT ON COLUMN track.domain_id IS 'ドメインID（UUIDv7形式、ビジネス識別子）';

-- 6. track_tune テーブル
-- 複合主キーテーブルのため、domain_idは追加しない（設計判断）

-- 7. album_article テーブル
ALTER TABLE album_article
    ADD COLUMN domain_id UUID;

UPDATE album_article
SET domain_id = gen_random_uuid()
WHERE domain_id IS NULL;

ALTER TABLE album_article
    ALTER COLUMN domain_id SET NOT NULL,
    ADD CONSTRAINT uk_album_article_domain_id UNIQUE (domain_id);

CREATE INDEX idx_album_article_domain_id ON album_article(domain_id);

COMMENT ON COLUMN album_article.domain_id IS 'ドメインID（UUIDv7形式、ビジネス識別子）';

-- 8. album_distribution テーブル
ALTER TABLE album_distribution
    ADD COLUMN domain_id UUID;

UPDATE album_distribution
SET domain_id = gen_random_uuid()
WHERE domain_id IS NULL;

ALTER TABLE album_distribution
    ALTER COLUMN domain_id SET NOT NULL,
    ADD CONSTRAINT uk_album_distribution_domain_id UNIQUE (domain_id);

CREATE INDEX idx_album_distribution_domain_id ON album_distribution(domain_id);

COMMENT ON COLUMN album_distribution.domain_id IS 'ドメインID（UUIDv7形式、ビジネス識別子）';

-- 9. album_acquisition_channel テーブル
ALTER TABLE album_acquisition_channel
    ADD COLUMN domain_id UUID;

UPDATE album_acquisition_channel
SET domain_id = gen_random_uuid()
WHERE domain_id IS NULL;

ALTER TABLE album_acquisition_channel
    ALTER COLUMN domain_id SET NOT NULL,
    ADD CONSTRAINT uk_album_acquisition_channel_domain_id UNIQUE (domain_id);

CREATE INDEX idx_album_acquisition_channel_domain_id ON album_acquisition_channel(domain_id);

COMMENT ON COLUMN album_acquisition_channel.domain_id IS 'ドメインID（UUIDv7形式、ビジネス識別子）';

-- 10. article テーブル
ALTER TABLE article
    ADD COLUMN domain_id UUID;

UPDATE article
SET domain_id = gen_random_uuid()
WHERE domain_id IS NULL;

ALTER TABLE article
    ALTER COLUMN domain_id SET NOT NULL,
    ADD CONSTRAINT uk_article_domain_id UNIQUE (domain_id);

CREATE INDEX idx_article_domain_id ON article(domain_id);

COMMENT ON COLUMN article.domain_id IS 'ドメインID（UUIDv7形式、ビジネス識別子）';

-- 11. article_tag テーブル
ALTER TABLE article_tag
    ADD COLUMN domain_id UUID;

UPDATE article_tag
SET domain_id = gen_random_uuid()
WHERE domain_id IS NULL;

ALTER TABLE article_tag
    ALTER COLUMN domain_id SET NOT NULL,
    ADD CONSTRAINT uk_article_tag_domain_id UNIQUE (domain_id);

CREATE INDEX idx_article_tag_domain_id ON article_tag(domain_id);

COMMENT ON COLUMN article_tag.domain_id IS 'ドメインID（UUIDv7形式、ビジネス識別子）';

-- 12. article_tag_link テーブル
-- 複合主キーテーブルのため、domain_idは追加しない（設計判断）
