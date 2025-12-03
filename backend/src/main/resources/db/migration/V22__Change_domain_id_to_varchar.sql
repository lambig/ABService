-- V15: domain_idカラムの型をUUIDからVARCHARに変更
-- Hibernate Reactive Panacheとの互換性のため

-- Albums
ALTER TABLE album ALTER COLUMN domain_id TYPE VARCHAR(255) USING domain_id::VARCHAR;

-- Tracks
ALTER TABLE track ALTER COLUMN domain_id TYPE VARCHAR(255) USING domain_id::VARCHAR;

-- Tunes
ALTER TABLE tune ALTER COLUMN domain_id TYPE VARCHAR(255) USING domain_id::VARCHAR;

-- Articles
ALTER TABLE article ALTER COLUMN domain_id TYPE VARCHAR(255) USING domain_id::VARCHAR;
ALTER TABLE article ALTER COLUMN album_id TYPE VARCHAR(255) USING album_id::VARCHAR;

-- Album Articles
ALTER TABLE album_article ALTER COLUMN domain_id TYPE VARCHAR(255) USING domain_id::VARCHAR;

-- Track Tunes
ALTER TABLE track_tune ALTER COLUMN tune_id TYPE VARCHAR(255) USING tune_id::VARCHAR;
