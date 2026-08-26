-- 記事本文を NOT NULL にする
-- 本文は「無い」ことがあり得ない項目であり、空であることは認める。ドメインは MarkupContent.EMPTY を
-- 空として扱う Null Object を持つため、永続化側も NULL を持たない形へ揃える。
-- 「値が無い」ことを NULL で表す項目（published_at / updated_at_business）とは扱いを分ける。

UPDATE article SET body = '' WHERE body IS NULL;

ALTER TABLE article ALTER COLUMN body SET DEFAULT '';
ALTER TABLE article ALTER COLUMN body SET NOT NULL;

COMMENT ON COLUMN article.body IS '記事本文（空文字列は本文なし。NULLは持たない）';
