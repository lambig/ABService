-- マークアップ形式から HTML を落とす
-- 入力形式は Markdown（およびプレーンテキスト）に一本化する。描画側は生HTMLをパースする経路を持たないため、
-- HTML を選べる状態が契約に残っていると「公開ページで実行可能なものが描画される余地」が閉じない。
-- 判断は docs/DECISIONS.md 24。

-- V24（article.body_format）と V34（album.description_format）は CHECK に名前を付けておらず、
-- PostgreSQL の自動命名（<table>_<column>_check）に依存する。IF EXISTS を付けるのは、名前が想定と
-- 異なっても失敗させないため。その場合は旧制約が残るが、後段で追加する制約が HTML を禁じるので結果は変わらない。
ALTER TABLE article DROP CONSTRAINT IF EXISTS article_body_format_check;
ALTER TABLE article ADD CONSTRAINT article_body_format_check
CHECK (body_format IN ('PLAIN_TEXT', 'MARKDOWN'));

ALTER TABLE album DROP CONSTRAINT IF EXISTS album_description_format_check;
ALTER TABLE album ADD CONSTRAINT album_description_format_check
CHECK (description_format IN ('PLAIN_TEXT', 'MARKDOWN'));

COMMENT ON COLUMN article.body_format IS '本文のマークアップ形式: PLAIN_TEXT（プレーンテキスト）/ MARKDOWN（Markdown形式）';
COMMENT ON COLUMN album.description_format IS '概要説明のマークアップ形式: PLAIN_TEXT（プレーンテキスト）/ MARKDOWN（Markdown形式）';
