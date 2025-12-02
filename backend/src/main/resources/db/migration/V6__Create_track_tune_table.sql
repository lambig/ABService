-- TrackTune: トラック内のチューン構成
-- 1トラックの中に含まれる 0..n 個のチューンと、その順番・クレジット・リンクを表す
CREATE TABLE track_tune (
    track_id BIGINT NOT NULL,
    seq INTEGER NOT NULL,
    tune_id BIGINT,
    -- クレジット上書き（NULLの場合はTune側のデフォルトを使う）
    composer_credit_override VARCHAR(255),
    arranger_credit_override VARCHAR(255),
    -- 外部サイトへのリンク（URL文字列1本で扱う）
    link_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    version_no INTEGER NOT NULL DEFAULT 1,
    -- 複合主キー: トラック内での登場順を一意に管理
    PRIMARY KEY (track_id, seq),
    CONSTRAINT fk_track_tune_track FOREIGN KEY (track_id) 
        REFERENCES track(track_id) ON DELETE CASCADE,
    CONSTRAINT fk_track_tune_tune FOREIGN KEY (tune_id) 
        REFERENCES tune(tune_id)
);

-- インデックス
CREATE INDEX idx_track_tune_tune ON track_tune(tune_id);

-- コメント
COMMENT ON TABLE track_tune IS 'トラック内のチューン構成テーブル（中間テーブル）';
COMMENT ON COLUMN track_tune.track_id IS 'トラックID（外部キー、複合主キーの一部）';
COMMENT ON COLUMN track_tune.seq IS 'トラック内での登場順（1, 2, 3, ...）（複合主キーの一部）';
COMMENT ON COLUMN track_tune.tune_id IS 'チューンID（外部キー、未登録のチューンはNULL）';
COMMENT ON COLUMN track_tune.composer_credit_override IS 'このトラック上での作曲者表記の上書き（NULLの場合はTune.default_composer_creditを使う）';
COMMENT ON COLUMN track_tune.arranger_credit_override IS 'このトラック上でのアレンジャー表記の上書き（NULLの場合はTune.default_arranger_creditを使う）';
COMMENT ON COLUMN track_tune.link_url IS 'このチューン（またはその版）に関するページへの任意のURL';
COMMENT ON COLUMN track_tune.created_at IS '作成日時';
COMMENT ON COLUMN track_tune.created_by IS '作成者';
COMMENT ON COLUMN track_tune.updated_at IS '更新日時';
COMMENT ON COLUMN track_tune.updated_by IS '更新者';
COMMENT ON COLUMN track_tune.version_no IS 'バージョン番号（楽観的ロック用）';
