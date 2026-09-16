-- 配布制御用の単一行。業務集約・監査レコードではない。
-- 更新と同じトランザクションで置換し、rollback・並行commitでも世代を取り違えない。
CREATE TABLE public_data_generation (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    generation UUID NOT NULL DEFAULT gen_random_uuid()
);
INSERT INTO public_data_generation (singleton) VALUES (TRUE);
COMMENT ON TABLE public_data_generation IS 'SSGの前後確認に使う保存済みデータ世代（配布完了の記録ではない）';
COMMENT ON COLUMN public_data_generation.singleton IS '単一行の識別子';
COMMENT ON COLUMN public_data_generation.generation IS '変更トランザクションとともに確定する不透明な世代トークン';

CREATE FUNCTION advance_public_data_generation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    UPDATE public_data_generation SET generation = gen_random_uuid() WHERE singleton;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Public data generation is missing';
    END IF;
    RETURN NULL;
END;
$$;

-- 下書きや結果0件の文も保守的に含める。世代は変更回数・公開件数ではない。
-- 親だけでは曲目・タグ・参照・外部音源の更新を取りこぼすため、公開Queryの全参照表を対象にする。
DO $$
DECLARE
    relation_name TEXT;
BEGIN
    FOREACH relation_name IN ARRAY ARRAY[
        'album', 'track', 'track_tune', 'tune', 'album_external_audio',
        'article', 'article_album_reference', 'article_tag', 'article_tag_link', 'site_content'
    ] LOOP
        EXECUTE format(
            'CREATE TRIGGER public_data_changed BEFORE INSERT OR UPDATE OR DELETE OR TRUNCATE ON %I '
            'FOR EACH STATEMENT EXECUTE FUNCTION advance_public_data_generation()', relation_name);
    END LOOP;
END;
$$;
