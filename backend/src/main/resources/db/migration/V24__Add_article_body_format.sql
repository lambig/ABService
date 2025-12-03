-- Add body_format column to article table
-- マークアップ形式を保持するカラムを追加
ALTER TABLE article
ADD COLUMN body_format VARCHAR(20) NOT NULL DEFAULT 'PLAIN_TEXT'
CHECK (body_format IN ('PLAIN_TEXT', 'MARKDOWN', 'HTML'));

-- コメント
COMMENT ON COLUMN article.body_format IS '本文のマークアップ形式: PLAIN_TEXT（プレーンテキスト）/ MARKDOWN（Markdown形式）/ HTML（HTML形式）';
