-- track_tune にチューン名（テキスト）を追加する
-- v1.0 は Tune マスタとの同定を行わず、チューン名を人が書いた記述として持つ。
-- 同定（tune_id の解決）と、そのとき本列と Tune.title のどちらを表示するかは v1.2 で決める。
--
-- tune_id が NULL のまま本列だけを持つ行が v1.0 の通常の姿である。
-- NULL 許容なのは、MC・環境音のようにチューン名を持たない構成要素を許すため。

ALTER TABLE track_tune ADD COLUMN tune_title VARCHAR(255);

COMMENT ON COLUMN track_tune.tune_title IS 'このトラック上でのチューン名（人が書いた記述。tune_idとの同定は行わない）';
