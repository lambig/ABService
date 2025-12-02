-- AlbumArticle: アルバム記事・お品書き用メタ情報
-- Web のアルバム紹介記事・お品書き掲載で使うテキスト／ラベル情報
-- 1 アルバムにつき 0 or 1 記事（記事がないアルバムも許容）
CREATE TABLE album_article (
    album_id BIGINT PRIMARY KEY,
    intro_long TEXT,
    intro_short TEXT,
    first_event_space VARCHAR(100),
    label_tag VARCHAR(50) CHECK (label_tag IN ('NEW', 'BEST_OF', 'COMPILATION', 'COLLAB', 'OTHER')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    version_no INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT fk_album_article_album FOREIGN KEY (album_id) 
        REFERENCES album(album_id) ON DELETE CASCADE
);

-- インデックス
CREATE INDEX idx_album_article_label_tag ON album_article(label_tag);

-- コメント
COMMENT ON TABLE album_article IS 'アルバム記事・お品書き用メタ情報テーブル';
COMMENT ON COLUMN album_article.album_id IS 'アルバムID（主キー、外部キー）';
COMMENT ON COLUMN album_article.intro_long IS '記事本文としての紹介コメント（ロング）';
COMMENT ON COLUMN album_article.intro_short IS 'お品書き用のショートコメント';
COMMENT ON COLUMN album_article.first_event_space IS '初出イベントのスペース（例: "東X-00b"）';
COMMENT ON COLUMN album_article.label_tag IS 'お品書き用ラベル: NEW（新譜）/ BEST_OF（総集編）/ COMPILATION（コンピレーション）/ COLLAB（コラボ）/ OTHER（その他）';
COMMENT ON COLUMN album_article.created_at IS '作成日時';
COMMENT ON COLUMN album_article.created_by IS '作成者';
COMMENT ON COLUMN album_article.updated_at IS '更新日時';
COMMENT ON COLUMN album_article.updated_by IS '更新者';
COMMENT ON COLUMN album_article.version_no IS 'バージョン番号（楽観的ロック用）';
