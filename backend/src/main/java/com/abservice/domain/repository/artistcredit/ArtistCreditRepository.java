package com.abservice.domain.repository.artistcredit;

import com.abservice.domain.model.aggregate.artistcredit.ArtistCredit;
import com.abservice.domain.model.vo.common.ArtistCreditName;
import com.abservice.domain.repository.Repository;
import io.smallrye.mutiny.Uni;

/**
 * アーティスト名義リポジトリ
 *
 * <p>
 * ArtistCredit集約の永続化と取得を担当します。
 * </p>
 */
public interface ArtistCreditRepository extends Repository<ArtistCredit, ArtistCredit.Id> {

    /**
     * 表記名でアーティスト名義を検索
     *
     * @param displayName
     *            表記名
     * @return 該当するアーティスト名義、存在しない場合はnull
     */
    Uni<ArtistCredit> findByDisplayName(ArtistCreditName displayName);

    /**
     * 表記名で部分一致検索
     *
     * @param nameKeyword
     *            表記名キーワード
     * @return 該当するアーティスト名義のリスト
     */
    Uni<java.util.List<ArtistCredit>> findByDisplayNameContaining(String nameKeyword);

    /**
     * ソートキーでアーティスト名義を検索
     *
     * @param sortKey
     *            ソートキー
     * @return 該当するアーティスト名義のリスト
     */
    Uni<java.util.List<ArtistCredit>> findBySortKey(String sortKey);

    /**
     * すべてのアーティスト名義をソートキー順で取得
     *
     * @return すべてのアーティスト名義のリスト（ソートキー順）
     */
    Uni<java.util.List<ArtistCredit>> findAllOrderBySortKey();
}
