-- 「アルバム紹介記事」の二重実装のうち、Album 側にぶら下がる系統を撤去する
-- 紹介記事は Article（ALBUM 種別）が担い、アルバムへの参照は article_album_reference が持つ。
-- album_article が持っていた項目の行き先:
--   intro_long        -> article.body（マークアップ形式つき）
--   intro_short       -> article.intro_short（同名項目が既にある）
--   first_event_space -> 削除（album.event_space_number と同じ事実）
--   label_tag         -> 実装しない（下記）
--
-- 頒布情報（album_distribution）・入手経路（album_acquisition_channel）・label_tag は、
-- 本来（作品 × 発表）の組に属する情報であり、記事に持たせる形では1イベントで複数の作品を
-- 頒布するケースを表現できない。発表を第一級の概念にする設計（#201）とセットで作るため、
-- ここでは移送せず撤去する。
--
-- 保全すべきデータは存在しない（統合テストが clean-at-start でDBを毎回消去し、本番デプロイは未実施）。

DROP TABLE IF EXISTS album_acquisition_channel;
DROP TABLE IF EXISTS album_distribution;
DROP TABLE IF EXISTS album_article;
