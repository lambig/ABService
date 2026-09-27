-- 試聴用の選択はAlbum本体・公開世代から独立する。曲音源の参照枠とは分離する。
CREATE TABLE album_crossfade (
    album_id BIGINT PRIMARY KEY REFERENCES album(album_id) ON DELETE CASCADE,
    audio_id UUID NOT NULL REFERENCES private_audio_registration(audio_id),
    revision INTEGER NOT NULL CHECK (revision > 0)
);
CREATE INDEX album_crossfade_audio ON album_crossfade (audio_id);
COMMENT ON TABLE album_crossfade IS '試聴用クロスフェードの選択。配布認可・公開状態ではない';
COMMENT ON COLUMN album_crossfade.album_id IS 'canonical Albumへの参照。Album削除時は選択だけを除去する';
COMMENT ON COLUMN album_crossfade.audio_id IS '関連付け時に確認した確定済みの不変音源ID';
COMMENT ON COLUMN album_crossfade.revision IS '関連付け固有の編集世代。未設定はAPI上0、設定後は単調増加';
