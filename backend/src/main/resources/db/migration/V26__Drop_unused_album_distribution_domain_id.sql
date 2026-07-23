-- AlbumDistributionはAlbumへの1:1従属値でありドメインモデルにIdを持たない。
-- V13で全テーブル一律にdomain_idを追加した際の対象外漏れ（track_tune/article_tag_linkと同種）のため削除する。

ALTER TABLE album_distribution DROP COLUMN domain_id;
