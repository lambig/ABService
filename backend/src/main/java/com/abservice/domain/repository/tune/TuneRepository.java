package com.abservice.domain.repository.tune;

import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.model.vo.tune.TuneKind;
import com.abservice.domain.model.vo.tune.TuneTitle;
import com.abservice.domain.repository.Repository;
import io.smallrye.mutiny.Uni;

/**
 * チューンリポジトリ
 *
 * <p>
 * Tune集約の永続化と取得を担当します。
 * </p>
 */
public interface TuneRepository extends Repository<Tune, Tune.Id> {

    /**
     * タイトルでチューンを検索
     *
     * @param title
     *            チューンタイトル
     * @return 該当するチューンのリスト
     */
    Uni<java.util.List<Tune>> findByTitle(TuneTitle title);

    /**
     * チューン種別でチューンを検索
     *
     * @param tuneKind
     *            チューン種別（トラッド、オリジナル、アレンジ）
     * @return 該当するチューンのリスト
     */
    Uni<java.util.List<Tune>> findByTuneKind(TuneKind tuneKind);

    /**
     * チューンタイプでチューンを検索
     *
     * @param tuneType
     *            チューンタイプ（リール、ジグなど）
     * @return 該当するチューンのリスト
     */
    Uni<java.util.List<Tune>> findByTuneType(String tuneType);

    /**
     * デフォルトキーでチューンを検索
     *
     * @param defaultKey
     *            デフォルトキー
     * @return 該当するチューンのリスト
     */
    Uni<java.util.List<Tune>> findByDefaultKey(String defaultKey);
}
