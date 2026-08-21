-- Albumにカバー画像を追加
-- 配信URLではなくアセットキーを保持する（配信URLは照会時に配信設定から組み立てるため、
-- CDNのパス構成やドメインの違いに保存データが依存しない）

ALTER TABLE album ADD COLUMN IF NOT EXISTS cover_image_key VARCHAR(255);

COMMENT ON COLUMN album.cover_image_key IS 'カバー画像のアセットキー（NULL: カバー画像なし。配信URLは配信設定と組み合わせて生成する）';
