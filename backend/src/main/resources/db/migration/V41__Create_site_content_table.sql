-- サイトの文言（サイト名・説明・トップの紹介文など）を作成
-- フロントエンドのソースへ直書きせずデータ側に置くことで、リポジトリからサイトの内容が推測されない状態にする。
-- キーは列挙で閉じず自由文字列とする（文言が今後増える前提。増やすのをデータ投入だけで済ませる）。
-- 形式の強制はドメイン層の SiteContentKey が行う（小文字英数字のドット区切り、2セグメント以上）。
-- 初期データは投入しない。seed に書くと文言がリポジトリに残り、目的が半分損なわれる。

CREATE TABLE site_content (
    site_content_id BIGSERIAL PRIMARY KEY,
    domain_id VARCHAR(255) NOT NULL,
    content_key VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    content_format VARCHAR(20) NOT NULL DEFAULT 'PLAIN_TEXT'
        CHECK (content_format IN ('PLAIN_TEXT', 'MARKDOWN')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_service VARCHAR(255),
    updated_by_service VARCHAR(255),
    created_by_user VARCHAR(255),
    updated_by_user VARCHAR(255),
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_site_content_domain_id UNIQUE (domain_id),
    CONSTRAINT uk_site_content_key UNIQUE (content_key)
);

COMMENT ON TABLE site_content IS 'サイトの文言（キーで引く散文。フロントのソースへ直書きしない）';
COMMENT ON COLUMN site_content.site_content_id IS '主キー';
COMMENT ON COLUMN site_content.domain_id IS 'ドメインID（UUIDv7形式、ビジネス識別子）';
COMMENT ON COLUMN site_content.content_key IS 'どの文言かを指すキー（例: site.name / home.introduction）';
COMMENT ON COLUMN site_content.content IS '文言の本文';
COMMENT ON COLUMN site_content.content_format IS '文言のマークアップ形式: PLAIN_TEXT（プレーンテキスト）/ MARKDOWN（Markdown形式）';
COMMENT ON COLUMN site_content.created_at IS '作成日時';
COMMENT ON COLUMN site_content.updated_at IS '更新日時';
COMMENT ON COLUMN site_content.created_by_service IS '作成したサービス名';
COMMENT ON COLUMN site_content.updated_by_service IS '更新したサービス名';
COMMENT ON COLUMN site_content.created_by_user IS '作成したユーザーID';
COMMENT ON COLUMN site_content.updated_by_user IS '更新したユーザーID';
COMMENT ON COLUMN site_content.version IS 'バージョン番号（楽観ロック用）';
