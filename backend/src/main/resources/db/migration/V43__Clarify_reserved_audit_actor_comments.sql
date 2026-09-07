-- actor 4列は将来の識別情報のための予約列。固定APIキーはactorを識別しない。
-- データ・列定義は変更せず、現行の未特定（NULL）契約をコメントに反映する。
-- 適用済みmigrationのチェックサムを維持するため、追加migrationで更新する。

COMMENT ON COLUMN album.created_by_service IS '作成actorのサービス予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN album.updated_by_service IS '更新actorのサービス予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN album.created_by_user IS '作成actorのユーザー予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN album.updated_by_user IS '更新actorのユーザー予約列（現状は常に未特定＝NULL）';

COMMENT ON COLUMN tune.created_by_service IS '作成actorのサービス予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN tune.updated_by_service IS '更新actorのサービス予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN tune.created_by_user IS '作成actorのユーザー予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN tune.updated_by_user IS '更新actorのユーザー予約列（現状は常に未特定＝NULL）';

COMMENT ON COLUMN track.created_by_service IS '作成actorのサービス予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN track.updated_by_service IS '更新actorのサービス予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN track.created_by_user IS '作成actorのユーザー予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN track.updated_by_user IS '更新actorのユーザー予約列（現状は常に未特定＝NULL）';

COMMENT ON COLUMN track_tune.created_by_service IS '作成actorのサービス予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN track_tune.updated_by_service IS '更新actorのサービス予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN track_tune.created_by_user IS '作成actorのユーザー予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN track_tune.updated_by_user IS '更新actorのユーザー予約列（現状は常に未特定＝NULL）';

COMMENT ON COLUMN article.created_by_service IS '作成actorのサービス予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN article.updated_by_service IS '更新actorのサービス予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN article.created_by_user IS '作成actorのユーザー予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN article.updated_by_user IS '更新actorのユーザー予約列（現状は常に未特定＝NULL）';

COMMENT ON COLUMN article_tag.created_by_service IS '作成actorのサービス予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN article_tag.updated_by_service IS '更新actorのサービス予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN article_tag.created_by_user IS '作成actorのユーザー予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN article_tag.updated_by_user IS '更新actorのユーザー予約列（現状は常に未特定＝NULL）';

COMMENT ON COLUMN article_tag_link.created_by_service IS '作成actorのサービス予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN article_tag_link.updated_by_service IS '更新actorのサービス予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN article_tag_link.created_by_user IS '作成actorのユーザー予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN article_tag_link.updated_by_user IS '更新actorのユーザー予約列（現状は常に未特定＝NULL）';

COMMENT ON COLUMN album_external_audio.created_by_service IS '作成actorのサービス予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN album_external_audio.updated_by_service IS '更新actorのサービス予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN album_external_audio.created_by_user IS '作成actorのユーザー予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN album_external_audio.updated_by_user IS '更新actorのユーザー予約列（現状は常に未特定＝NULL）';

COMMENT ON COLUMN article_album_reference.created_by_service IS '作成actorのサービス予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN article_album_reference.updated_by_service IS '更新actorのサービス予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN article_album_reference.created_by_user IS '作成actorのユーザー予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN article_album_reference.updated_by_user IS '更新actorのユーザー予約列（現状は常に未特定＝NULL）';

COMMENT ON COLUMN site_content.created_by_service IS '作成actorのサービス予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN site_content.updated_by_service IS '更新actorのサービス予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN site_content.created_by_user IS '作成actorのユーザー予約列（現状は常に未特定＝NULL）';
COMMENT ON COLUMN site_content.updated_by_user IS '更新actorのユーザー予約列（現状は常に未特定＝NULL）';
