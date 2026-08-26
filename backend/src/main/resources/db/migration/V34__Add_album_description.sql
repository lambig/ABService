-- アルバムに概要説明を追加する
-- 作品そのものを説明するストック情報。頒布の告知や制作の経緯といった時点の記述は記事側が持つ。
-- 記事本文（article.body / article.body_format）と同じく、テキストとマークアップ形式の組で保持する。

ALTER TABLE album ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE album ADD COLUMN IF NOT EXISTS description_format VARCHAR(20) NOT NULL DEFAULT 'PLAIN_TEXT'
CHECK (description_format IN ('PLAIN_TEXT', 'MARKDOWN', 'HTML'));

COMMENT ON COLUMN album.description IS '作品の概要説明（NULL・空文字は説明なし）';
COMMENT ON COLUMN album.description_format IS '概要説明のマークアップ形式: PLAIN_TEXT（プレーンテキスト）/ MARKDOWN（Markdown形式）/ HTML（HTML形式）';
