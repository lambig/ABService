-- ショート紹介文を NOT NULL・VARCHAR(120) にする
-- ショート紹介文は「無い」ことがあり得ない項目であり、空であることは認める。ドメインは IntroShort.EMPTY を
-- 空として扱う Null Object を持つため、永続化側も NULL を持たない形へ揃える（本文 body / V36 と同じ扱い）。
-- 長さの上限は VO 側の定数（IntroShort.MAX_LENGTH）と一致させる。title VARCHAR(500) と ArticleTitle の
-- 対応と同じで、上限を変えるときに触るのは VO の定数とこの列の型だけになる。

UPDATE article SET intro_short = '' WHERE intro_short IS NULL;

-- 型を狭める前に、上限を超える既存行を切り詰める（未リリースのため、失われるのは開発中に入れた値のみ）
UPDATE article SET intro_short = LEFT(intro_short, 120) WHERE LENGTH(intro_short) > 120;

ALTER TABLE article ALTER COLUMN intro_short TYPE VARCHAR(120);
ALTER TABLE article ALTER COLUMN intro_short SET DEFAULT '';
ALTER TABLE article ALTER COLUMN intro_short SET NOT NULL;

COMMENT ON COLUMN article.intro_short IS 'ショート紹介文（お品書きや一覧表示用。空文字列は紹介文なし。NULLは持たない）';
