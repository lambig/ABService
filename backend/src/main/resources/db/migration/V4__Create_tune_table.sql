-- Tune: チューン（曲そのもの）
-- トラッド／オリジナル／アレンジを区別し、アレンジの場合は原曲情報をテキストで保持する
CREATE TABLE tune (
    tune_id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    tune_kind VARCHAR(20) NOT NULL CHECK (tune_kind IN ('TRAD', 'ORIGINAL', 'ARRANGEMENT')),
    default_composer_credit VARCHAR(255),
    default_arranger_credit VARCHAR(255),
    -- アレンジ元についてテキストで残す（原曲はDB上にエンティティは作らない）
    original_work_title VARCHAR(255),
    original_work_credit VARCHAR(255),
    -- 追加メタデータ
    tune_type VARCHAR(50),
    default_key VARCHAR(20),
    default_tempo INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    version_no INTEGER NOT NULL DEFAULT 1,
    -- アレンジの場合のみ原曲情報が入る想定（アプリケーション層でのバリデーションを推奨）
    CONSTRAINT chk_tune_arrangement_fields CHECK (
        (tune_kind != 'ARRANGEMENT') OR 
        (original_work_title IS NOT NULL)
    )
);

-- インデックス
CREATE INDEX idx_tune_title ON tune(title);
CREATE INDEX idx_tune_kind ON tune(tune_kind);
CREATE INDEX idx_tune_type ON tune(tune_type);

-- コメント
COMMENT ON TABLE tune IS 'チューン（曲）テーブル';
COMMENT ON COLUMN tune.tune_id IS 'チューンID（主キー）';
COMMENT ON COLUMN tune.title IS 'この版（アレンジ版を含む）のチューン名';
COMMENT ON COLUMN tune.tune_kind IS '曲種別: TRAD（トラッド）/ ORIGINAL（オリジナル）/ ARRANGEMENT（アレンジ）';
COMMENT ON COLUMN tune.default_composer_credit IS '作曲者・出典のデフォルト表記（例: "Trad.", "Taro Foo", "J. Doe / Arr. Someone"）';
COMMENT ON COLUMN tune.default_arranger_credit IS 'アレンジャー名義のデフォルト表記';
COMMENT ON COLUMN tune.original_work_title IS '原曲のタイトル（アレンジの場合のみ）';
COMMENT ON COLUMN tune.original_work_credit IS '原曲の作曲者・アーティストなどのクレジット（アレンジの場合のみ）';
COMMENT ON COLUMN tune.tune_type IS 'チューンタイプ（リール／ジグなど）';
COMMENT ON COLUMN tune.default_key IS '想定キー';
COMMENT ON COLUMN tune.default_tempo IS 'デフォルトテンポ（BPM）';
COMMENT ON COLUMN tune.created_at IS '作成日時';
COMMENT ON COLUMN tune.created_by IS '作成者';
COMMENT ON COLUMN tune.updated_at IS '更新日時';
COMMENT ON COLUMN tune.updated_by IS '更新者';
COMMENT ON COLUMN tune.version_no IS 'バージョン番号（楽観的ロック用）';
