-- アルバムへの参照を article 本体から従属テーブルへ移す
-- アルバムを参照できるのは ALBUM 種別の記事だけで、他の種別は参照という概念を持たない。
-- 本体テーブルに置くと、参照を持てない種別の行にも常にNULLの列が並ぶ。
--
-- former_album_id / album_reference_lost_at / album_reference_lost_reason は、
-- オブジェクトレジストリ（存在オラクル）の導入後に不要になる暫定の列である。専用の構造は与えず、
-- 本テーブルへ同居させるに留める。不要になった時点で本テーブルから列を落とす。
--
-- 参照を持たない ALBUM 記事は行を持たない（種別は article.article_type が決め、本テーブルの
-- 有無では決まらない）。

CREATE TABLE article_album_reference (
    article_id BIGINT PRIMARY KEY,
    album_id VARCHAR(255),
    former_album_id VARCHAR(255),
    album_reference_lost_at TIMESTAMPTZ,
    album_reference_lost_reason VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_service VARCHAR(255),
    updated_by_service VARCHAR(255),
    created_by_user VARCHAR(255),
    updated_by_user VARCHAR(255),
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_article_album_reference_article FOREIGN KEY (article_id)
        REFERENCES article(article_id) ON DELETE CASCADE
);

CREATE INDEX idx_article_album_reference_album ON article_album_reference(album_id);

COMMENT ON TABLE article_album_reference IS 'ALBUM種別の記事が持つアルバムへの参照（参照なしの記事は行を持たない）';
COMMENT ON COLUMN article_album_reference.article_id IS '記事ID（主キー兼外部キー。1記事につき0..1件）';
COMMENT ON COLUMN article_album_reference.album_id IS '参照先アルバムのドメインID（NULL: 参照が失効している）';
COMMENT ON COLUMN article_album_reference.former_album_id IS '失効した参照先アルバムのドメインID（NULL: 失効していない）';
COMMENT ON COLUMN article_album_reference.album_reference_lost_at IS 'アルバム参照が失効した日時（業務上の事実。監査列とは別概念）';
COMMENT ON COLUMN article_album_reference.album_reference_lost_reason IS '失効の理由コード（ALBUM_DELETED）。表示文言は持たず利用側が解釈する';
COMMENT ON COLUMN article_album_reference.created_at IS '作成日時';
COMMENT ON COLUMN article_album_reference.updated_at IS '更新日時';
COMMENT ON COLUMN article_album_reference.created_by_service IS '作成したサービス名';
COMMENT ON COLUMN article_album_reference.updated_by_service IS '更新したサービス名';
COMMENT ON COLUMN article_album_reference.created_by_user IS '作成したユーザーID';
COMMENT ON COLUMN article_album_reference.updated_by_user IS '更新したユーザーID';
COMMENT ON COLUMN article_album_reference.version IS 'バージョン番号（楽観ロック用）';

INSERT INTO article_album_reference (article_id, album_id, former_album_id, album_reference_lost_at, album_reference_lost_reason)
SELECT article_id, album_id, former_album_id, album_reference_lost_at, album_reference_lost_reason
FROM article
WHERE album_id IS NOT NULL OR former_album_id IS NOT NULL;

DROP INDEX IF EXISTS idx_article_album;
DROP INDEX IF EXISTS idx_article_album_id;

ALTER TABLE article DROP COLUMN IF EXISTS album_id;
ALTER TABLE article DROP COLUMN IF EXISTS former_album_id;
ALTER TABLE article DROP COLUMN IF EXISTS album_reference_lost_at;
ALTER TABLE article DROP COLUMN IF EXISTS album_reference_lost_reason;
