-- Article: 記事テーブル
-- ブログ記事、アルバム紹介記事、お品書き掲載記事など、公開情報を管理
-- アルバム記事の場合は album_id で参照（片方向関連）
CREATE TABLE article (
    article_id BIGSERIAL PRIMARY KEY,
    article_type VARCHAR(50) NOT NULL CHECK (article_type IN ('ALBUM', 'NOTE', 'NEWS', 'EVENT', 'OTHER')),
    album_id BIGINT,
    title VARCHAR(500) NOT NULL,
    body TEXT,
    intro_short TEXT,
    published_at TIMESTAMP,
    updated_at_business TIMESTAMP,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    version_no INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT fk_article_album FOREIGN KEY (album_id)
        REFERENCES album(album_id) ON DELETE SET NULL
);

-- インデックス
CREATE INDEX idx_article_type ON article(article_type);
CREATE INDEX idx_article_album_id ON article(album_id);
CREATE INDEX idx_article_published_at ON article(published_at);
CREATE INDEX idx_article_is_public ON article(is_public);
CREATE INDEX idx_article_type_is_public ON article(article_type, is_public);

-- コメント
COMMENT ON TABLE article IS '記事テーブル: ブログ記事、アルバム紹介記事など公開情報を管理';
COMMENT ON COLUMN article.article_id IS '記事ID（主キー）';
COMMENT ON COLUMN article.article_type IS '記事種別: ALBUM（アルバム記事）/ NOTE（通常記事）/ NEWS（ニュース）/ EVENT（イベント情報）/ OTHER（その他）';
COMMENT ON COLUMN article.album_id IS 'アルバムID（外部キー、アルバム記事の場合のみ設定）';
COMMENT ON COLUMN article.title IS '記事タイトル';
COMMENT ON COLUMN article.body IS '記事本文';
COMMENT ON COLUMN article.intro_short IS 'ショート紹介文（お品書きや一覧表示用）';
COMMENT ON COLUMN article.published_at IS '公開日時（業務的な掲載日）';
COMMENT ON COLUMN article.updated_at_business IS '更新日時（業務的な修正日、監査列とは別概念）';
COMMENT ON COLUMN article.is_public IS '公開フラグ（TRUE: 公開、FALSE: 非公開/下書き）';
COMMENT ON COLUMN article.created_at IS '作成日時（監査列）';
COMMENT ON COLUMN article.created_by IS '作成者（監査列）';
COMMENT ON COLUMN article.updated_at IS '更新日時（監査列）';
COMMENT ON COLUMN article.updated_by IS '更新者（監査列）';
COMMENT ON COLUMN article.version_no IS 'バージョン番号（楽観的ロック用）';
