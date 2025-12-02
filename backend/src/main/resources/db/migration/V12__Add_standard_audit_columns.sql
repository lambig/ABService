-- ============================================================================
-- V12: Add standard audit columns to all tables
-- ============================================================================
-- 
-- 共通監査列を全テーブルに追加・修正する。
-- 
-- 既存の列:
--   - created_at: レコード作成日時（既存）
--   - updated_at: レコード最終更新日時（既存）
--   - created_by: 作成者（削除）
--   - updated_by: 更新者（削除）
--   - version_no: バージョン番号（versionにリネーム）
--
-- 追加する列:
--   - created_by_service: 作成時のアプリケーションサービス名
--   - updated_by_service: 更新時のアプリケーションサービス名
--   - created_by_user: 作成者ユーザーID（外部サービスのユーザーID）
--   - updated_by_user: 更新者ユーザーID（外部サービスのユーザーID）
--   - version: 楽観ロック用バージョン番号（version_noからリネーム）
--
-- ルール:
--   - 今後作成するすべてのテーブルにもこれらの列を含めること
--   - created_at/updated_atはDBのDEFAULT値に任せる（アプリケーションで明示的に設定しない）
-- ============================================================================

-- 1. artist_credit テーブル
ALTER TABLE artist_credit
    DROP COLUMN created_by,
    DROP COLUMN updated_by,
    ADD COLUMN created_by_service VARCHAR(255),
    ADD COLUMN updated_by_service VARCHAR(255),
    ADD COLUMN created_by_user VARCHAR(255),
    ADD COLUMN updated_by_user VARCHAR(255);

ALTER TABLE artist_credit
    RENAME COLUMN version_no TO version;

-- 2. event テーブル
ALTER TABLE event
    DROP COLUMN created_by,
    DROP COLUMN updated_by,
    ADD COLUMN created_by_service VARCHAR(255),
    ADD COLUMN updated_by_service VARCHAR(255),
    ADD COLUMN created_by_user VARCHAR(255),
    ADD COLUMN updated_by_user VARCHAR(255);

ALTER TABLE event
    RENAME COLUMN version_no TO version;

-- 3. album テーブル
ALTER TABLE album
    DROP COLUMN created_by,
    DROP COLUMN updated_by,
    ADD COLUMN created_by_service VARCHAR(255),
    ADD COLUMN updated_by_service VARCHAR(255),
    ADD COLUMN created_by_user VARCHAR(255),
    ADD COLUMN updated_by_user VARCHAR(255);

ALTER TABLE album
    RENAME COLUMN version_no TO version;

-- 4. tune テーブル
ALTER TABLE tune
    DROP COLUMN created_by,
    DROP COLUMN updated_by,
    ADD COLUMN created_by_service VARCHAR(255),
    ADD COLUMN updated_by_service VARCHAR(255),
    ADD COLUMN created_by_user VARCHAR(255),
    ADD COLUMN updated_by_user VARCHAR(255);

ALTER TABLE tune
    RENAME COLUMN version_no TO version;

-- 5. track テーブル
ALTER TABLE track
    DROP COLUMN created_by,
    DROP COLUMN updated_by,
    ADD COLUMN created_by_service VARCHAR(255),
    ADD COLUMN updated_by_service VARCHAR(255),
    ADD COLUMN created_by_user VARCHAR(255),
    ADD COLUMN updated_by_user VARCHAR(255);

ALTER TABLE track
    RENAME COLUMN version_no TO version;

-- 6. track_tune テーブル
ALTER TABLE track_tune
    DROP COLUMN created_by,
    DROP COLUMN updated_by,
    ADD COLUMN created_by_service VARCHAR(255),
    ADD COLUMN updated_by_service VARCHAR(255),
    ADD COLUMN created_by_user VARCHAR(255),
    ADD COLUMN updated_by_user VARCHAR(255);

ALTER TABLE track_tune
    RENAME COLUMN version_no TO version;

-- 7. album_article テーブル
ALTER TABLE album_article
    DROP COLUMN created_by,
    DROP COLUMN updated_by,
    ADD COLUMN created_by_service VARCHAR(255),
    ADD COLUMN updated_by_service VARCHAR(255),
    ADD COLUMN created_by_user VARCHAR(255),
    ADD COLUMN updated_by_user VARCHAR(255);

ALTER TABLE album_article
    RENAME COLUMN version_no TO version;

-- 8. album_distribution テーブル
ALTER TABLE album_distribution
    DROP COLUMN created_by,
    DROP COLUMN updated_by,
    ADD COLUMN created_by_service VARCHAR(255),
    ADD COLUMN updated_by_service VARCHAR(255),
    ADD COLUMN created_by_user VARCHAR(255),
    ADD COLUMN updated_by_user VARCHAR(255);

ALTER TABLE album_distribution
    RENAME COLUMN version_no TO version;

-- 9. album_acquisition_channel テーブル
ALTER TABLE album_acquisition_channel
    DROP COLUMN created_by,
    DROP COLUMN updated_by,
    ADD COLUMN created_by_service VARCHAR(255),
    ADD COLUMN updated_by_service VARCHAR(255),
    ADD COLUMN created_by_user VARCHAR(255),
    ADD COLUMN updated_by_user VARCHAR(255);

ALTER TABLE album_acquisition_channel
    RENAME COLUMN version_no TO version;

-- 10. article テーブル
ALTER TABLE article
    DROP COLUMN created_by,
    DROP COLUMN updated_by,
    ADD COLUMN created_by_service VARCHAR(255),
    ADD COLUMN updated_by_service VARCHAR(255),
    ADD COLUMN created_by_user VARCHAR(255),
    ADD COLUMN updated_by_user VARCHAR(255);

ALTER TABLE article
    RENAME COLUMN version_no TO version;

-- 11. article_tag テーブル
ALTER TABLE article_tag
    DROP COLUMN created_by,
    DROP COLUMN updated_by,
    ADD COLUMN created_by_service VARCHAR(255),
    ADD COLUMN updated_by_service VARCHAR(255),
    ADD COLUMN created_by_user VARCHAR(255),
    ADD COLUMN updated_by_user VARCHAR(255);

ALTER TABLE article_tag
    RENAME COLUMN version_no TO version;

-- 12. article_tag_link テーブル
ALTER TABLE article_tag_link
    DROP COLUMN created_by,
    ADD COLUMN created_by_service VARCHAR(255),
    ADD COLUMN updated_by_service VARCHAR(255),
    ADD COLUMN created_by_user VARCHAR(255),
    ADD COLUMN updated_by_user VARCHAR(255),
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN version INTEGER NOT NULL DEFAULT 0;

-- コメント追加
COMMENT ON COLUMN artist_credit.created_by_service IS '作成時のアプリケーションサービス名';
COMMENT ON COLUMN artist_credit.updated_by_service IS '更新時のアプリケーションサービス名';
COMMENT ON COLUMN artist_credit.created_by_user IS '作成者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN artist_credit.updated_by_user IS '更新者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN artist_credit.version IS 'バージョン番号（楽観的ロック用）';

COMMENT ON COLUMN event.created_by_service IS '作成時のアプリケーションサービス名';
COMMENT ON COLUMN event.updated_by_service IS '更新時のアプリケーションサービス名';
COMMENT ON COLUMN event.created_by_user IS '作成者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN event.updated_by_user IS '更新者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN event.version IS 'バージョン番号（楽観的ロック用）';

COMMENT ON COLUMN album.created_by_service IS '作成時のアプリケーションサービス名';
COMMENT ON COLUMN album.updated_by_service IS '更新時のアプリケーションサービス名';
COMMENT ON COLUMN album.created_by_user IS '作成者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN album.updated_by_user IS '更新者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN album.version IS 'バージョン番号（楽観的ロック用）';

COMMENT ON COLUMN tune.created_by_service IS '作成時のアプリケーションサービス名';
COMMENT ON COLUMN tune.updated_by_service IS '更新時のアプリケーションサービス名';
COMMENT ON COLUMN tune.created_by_user IS '作成者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN tune.updated_by_user IS '更新者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN tune.version IS 'バージョン番号（楽観的ロック用）';

COMMENT ON COLUMN track.created_by_service IS '作成時のアプリケーションサービス名';
COMMENT ON COLUMN track.updated_by_service IS '更新時のアプリケーションサービス名';
COMMENT ON COLUMN track.created_by_user IS '作成者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN track.updated_by_user IS '更新者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN track.version IS 'バージョン番号（楽観的ロック用）';

COMMENT ON COLUMN track_tune.created_by_service IS '作成時のアプリケーションサービス名';
COMMENT ON COLUMN track_tune.updated_by_service IS '更新時のアプリケーションサービス名';
COMMENT ON COLUMN track_tune.created_by_user IS '作成者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN track_tune.updated_by_user IS '更新者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN track_tune.version IS 'バージョン番号（楽観的ロック用）';

COMMENT ON COLUMN album_article.created_by_service IS '作成時のアプリケーションサービス名';
COMMENT ON COLUMN album_article.updated_by_service IS '更新時のアプリケーションサービス名';
COMMENT ON COLUMN album_article.created_by_user IS '作成者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN album_article.updated_by_user IS '更新者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN album_article.version IS 'バージョン番号（楽観的ロック用）';

COMMENT ON COLUMN album_distribution.created_by_service IS '作成時のアプリケーションサービス名';
COMMENT ON COLUMN album_distribution.updated_by_service IS '更新時のアプリケーションサービス名';
COMMENT ON COLUMN album_distribution.created_by_user IS '作成者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN album_distribution.updated_by_user IS '更新者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN album_distribution.version IS 'バージョン番号（楽観的ロック用）';

COMMENT ON COLUMN album_acquisition_channel.created_by_service IS '作成時のアプリケーションサービス名';
COMMENT ON COLUMN album_acquisition_channel.updated_by_service IS '更新時のアプリケーションサービス名';
COMMENT ON COLUMN album_acquisition_channel.created_by_user IS '作成者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN album_acquisition_channel.updated_by_user IS '更新者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN album_acquisition_channel.version IS 'バージョン番号（楽観的ロック用）';

COMMENT ON COLUMN article.created_by_service IS '作成時のアプリケーションサービス名';
COMMENT ON COLUMN article.updated_by_service IS '更新時のアプリケーションサービス名';
COMMENT ON COLUMN article.created_by_user IS '作成者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN article.updated_by_user IS '更新者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN article.version IS 'バージョン番号（楽観的ロック用）';

COMMENT ON COLUMN article_tag.created_by_service IS '作成時のアプリケーションサービス名';
COMMENT ON COLUMN article_tag.updated_by_service IS '更新時のアプリケーションサービス名';
COMMENT ON COLUMN article_tag.created_by_user IS '作成者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN article_tag.updated_by_user IS '更新者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN article_tag.version IS 'バージョン番号（楽観的ロック用）';

COMMENT ON COLUMN article_tag_link.created_by_service IS '作成時のアプリケーションサービス名';
COMMENT ON COLUMN article_tag_link.updated_by_service IS '更新時のアプリケーションサービス名';
COMMENT ON COLUMN article_tag_link.created_by_user IS '作成者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN article_tag_link.updated_by_user IS '更新者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN article_tag_link.updated_at IS '更新日時（監査列）';
COMMENT ON COLUMN article_tag_link.version IS 'バージョン番号（楽観的ロック用）';
