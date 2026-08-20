-- track_tune.tune_idのNOT NULL制約を除去する
-- ドメインモデル（TrackTune.tuneId）はMC・環境音などtune未登録のチューンをnullとして許容するが、
-- V14でBIGINT→ドメインID(UUID)参照へ変更した際に誤ってNOT NULLを付与していた
-- （旧BIGINT列は当初からNULL許容だった。V6__Create_track_tune_table.sql参照）。
ALTER TABLE track_tune ALTER COLUMN tune_id DROP NOT NULL;
