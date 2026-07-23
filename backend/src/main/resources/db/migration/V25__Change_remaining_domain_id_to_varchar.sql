-- V22でのdomain_id VARCHAR化（Hibernate Reactive Panacheとの互換性のため）から漏れていた
-- album_acquisition_channel / article_tag を同じ手法で変換する。

ALTER TABLE album_acquisition_channel ALTER COLUMN domain_id TYPE VARCHAR(255) USING domain_id::VARCHAR;

ALTER TABLE article_tag ALTER COLUMN domain_id TYPE VARCHAR(255) USING domain_id::VARCHAR;
