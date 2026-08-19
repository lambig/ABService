-- Albumに公開情報を追加
-- 真偽値フラグではなく、公開日時カラムの有無（NULL=下書き、非NULL=公開中）で公開状態を表現する

-- published_atカラムを追加
ALTER TABLE album ADD COLUMN IF NOT EXISTS published_at TIMESTAMP;

-- インデックスを作成
CREATE INDEX IF NOT EXISTS idx_album_published_at ON album(published_at);

-- コメントを追加
COMMENT ON COLUMN album.published_at IS '公開日時（NULL: 下書き、非NULL: 公開中。値そのものが最初に公開した日時）';
