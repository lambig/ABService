-- 記事のアルバム参照が失効した経緯を保持する
-- Albumは物理削除するため、参照していた記事は album_id を失う。参照先が消えた経緯を
-- 機械可読な形で1件だけ残し、管理画面が参照の張り直しを判断できるようにする。
-- 参照は「なし / 有効 / 失効」の3状態で、失効時のみ下記3列に値が入る。

ALTER TABLE article ADD COLUMN IF NOT EXISTS former_album_id VARCHAR(255);
ALTER TABLE article ADD COLUMN IF NOT EXISTS album_reference_lost_at TIMESTAMPTZ;
ALTER TABLE article ADD COLUMN IF NOT EXISTS album_reference_lost_reason VARCHAR(50);

COMMENT ON COLUMN article.former_album_id IS '失効した参照先アルバムのドメインID（NULL: 失効していない）';
COMMENT ON COLUMN article.album_reference_lost_at IS 'アルバム参照が失効した日時（業務上の事実。監査列とは別概念）';
COMMENT ON COLUMN article.album_reference_lost_reason IS '失効の理由コード（ALBUM_DELETED）。表示文言は持たず利用側が解釈する';
