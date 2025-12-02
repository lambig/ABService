-- V14: 外部キー参照をLong→domain_id(UUID)に変更
-- Kotlin参照プロジェクトの設計に合わせ、別集約への参照はdomain_idで行う

-- 1. album テーブルの外部キー変更
ALTER TABLE album
    ADD COLUMN artist_credit_domain_id UUID,
    ADD COLUMN event_domain_id UUID;

-- 既存データの移行: 参照先のdomain_idを設定
UPDATE album a
SET artist_credit_domain_id = ac.domain_id
FROM artist_credit ac
WHERE a.artist_credit_id = ac.artist_credit_id;

UPDATE album a
SET event_domain_id = e.domain_id
FROM event e
WHERE a.event_id = e.event_id;

-- NOT NULL制約（artist_credit_domain_idのみ必須）
ALTER TABLE album
    ALTER COLUMN artist_credit_domain_id SET NOT NULL;

-- 旧カラム削除と外部キー制約削除
ALTER TABLE album
    DROP CONSTRAINT IF EXISTS fk_album_artist_credit,
    DROP CONSTRAINT IF EXISTS fk_album_event,
    DROP COLUMN artist_credit_id,
    DROP COLUMN event_id;

-- リネーム
ALTER TABLE album
    RENAME COLUMN artist_credit_domain_id TO artist_credit_id;
ALTER TABLE album
    RENAME COLUMN event_domain_id TO event_id;

-- インデックス再作成
CREATE INDEX idx_album_artist_credit ON album(artist_credit_id);
CREATE INDEX idx_album_event ON album(event_id);

-- 2. track テーブルの外部キー変更
ALTER TABLE track
    ADD COLUMN artist_credit_domain_id UUID;

-- 既存データの移行
UPDATE track t
SET artist_credit_domain_id = ac.domain_id
FROM artist_credit ac
WHERE t.artist_credit_id = ac.artist_credit_id;

-- 旧カラム削除
ALTER TABLE track
    DROP COLUMN artist_credit_id;

-- リネーム
ALTER TABLE track
    RENAME COLUMN artist_credit_domain_id TO artist_credit_id;

-- インデックス再作成
CREATE INDEX idx_track_artist_credit ON track(artist_credit_id);

-- 3. track_tune テーブルの外部キー変更
ALTER TABLE track_tune
    ADD COLUMN tune_domain_id UUID;

-- 既存データの移行
UPDATE track_tune tt
SET tune_domain_id = tu.domain_id
FROM tune tu
WHERE tt.tune_id = tu.tune_id;

-- NOT NULL制約
ALTER TABLE track_tune
    ALTER COLUMN tune_domain_id SET NOT NULL;

-- 旧カラム削除と外部キー制約削除
ALTER TABLE track_tune
    DROP CONSTRAINT IF EXISTS fk_track_tune_tune,
    DROP COLUMN tune_id;

-- リネーム
ALTER TABLE track_tune
    RENAME COLUMN tune_domain_id TO tune_id;

-- 4. article テーブルの外部キー変更
ALTER TABLE article
    ADD COLUMN album_domain_id UUID;

-- 既存データの移行
UPDATE article ar
SET album_domain_id = al.domain_id
FROM album al
WHERE ar.album_id = al.album_id;

-- 旧カラム削除と外部キー制約削除
ALTER TABLE article
    DROP CONSTRAINT IF EXISTS fk_article_album,
    DROP COLUMN album_id;

-- リネーム
ALTER TABLE article
    RENAME COLUMN album_domain_id TO album_id;

-- インデックス再作成
CREATE INDEX idx_article_album ON article(album_id);

-- コメント
COMMENT ON COLUMN album.artist_credit_id IS 'アーティストクレジット集約へのドメインID参照(UUID)';
COMMENT ON COLUMN album.event_id IS 'イベント集約へのドメインID参照(UUID)、nullable';
COMMENT ON COLUMN track.artist_credit_id IS 'アーティストクレジット集約へのドメインID参照(UUID)、nullable';
COMMENT ON COLUMN track_tune.tune_id IS '楽曲集約へのドメインID参照(UUID)';
COMMENT ON COLUMN article.album_id IS 'アルバム集約へのドメインID参照(UUID)、nullable';
