-- ArticleTag: 記事タグマスタ
-- 記事のカテゴライズ・フィルタリング用タグ
CREATE TABLE article_tag (
    article_tag_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    version_no INTEGER NOT NULL DEFAULT 1
);

-- ArticleTagLink: 記事とタグの多対多リンク
CREATE TABLE article_tag_link (
    article_id BIGINT NOT NULL,
    article_tag_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    PRIMARY KEY (article_id, article_tag_id),
    CONSTRAINT fk_article_tag_link_article FOREIGN KEY (article_id)
        REFERENCES article(article_id) ON DELETE CASCADE,
    CONSTRAINT fk_article_tag_link_tag FOREIGN KEY (article_tag_id)
        REFERENCES article_tag(article_tag_id) ON DELETE CASCADE
);

-- インデックス
CREATE INDEX idx_article_tag_name ON article_tag(name);
CREATE INDEX idx_article_tag_link_article_id ON article_tag_link(article_id);
CREATE INDEX idx_article_tag_link_tag_id ON article_tag_link(article_tag_id);

-- コメント
COMMENT ON TABLE article_tag IS '記事タグマスタ: 記事のカテゴライズ・フィルタリング用';
COMMENT ON COLUMN article_tag.article_tag_id IS 'タグID（主キー）';
COMMENT ON COLUMN article_tag.name IS 'タグ名（ユニーク）';
COMMENT ON COLUMN article_tag.created_at IS '作成日時（監査列）';
COMMENT ON COLUMN article_tag.created_by IS '作成者（監査列）';
COMMENT ON COLUMN article_tag.updated_at IS '更新日時（監査列）';
COMMENT ON COLUMN article_tag.updated_by IS '更新者（監査列）';
COMMENT ON COLUMN article_tag.version_no IS 'バージョン番号（楽観的ロック用）';

COMMENT ON TABLE article_tag_link IS '記事タグリンク: 記事とタグの多対多関連';
COMMENT ON COLUMN article_tag_link.article_id IS '記事ID（外部キー）';
COMMENT ON COLUMN article_tag_link.article_tag_id IS 'タグID（外部キー）';
COMMENT ON COLUMN article_tag_link.created_at IS '作成日時（監査列）';
COMMENT ON COLUMN article_tag_link.created_by IS '作成者（監査列）';
