package com.abservice.infrastructure.persistence.datasource;

import com.abservice.infrastructure.persistence.entity.SiteContentTableRecord;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.List;

/**
 * SiteContent DataSource (DAO)
 *
 * <p>
 * Panacheを使用したサイト文言データアクセス層。
 * </p>
 */
@ApplicationScoped
public class SiteContentDataSource implements PanacheRepositoryBase<SiteContentTableRecord, Long> {

    /**
     * キーでサイト文言を検索
     *
     * @param contentKey
     *            どの文言かを指すキー
     * @return 該当するサイト文言（未存在の場合はnull）
     */
    public Uni<SiteContentTableRecord> findByContentKey(String contentKey) {
        return find("contentKey", contentKey).firstResult();
    }

    /**
     * ドメインIDでサイト文言を検索
     *
     * @param domainId
     *            ドメインID
     * @return 該当するサイト文言（未存在の場合はnull）
     */
    public Uni<SiteContentTableRecord> findByDomainId(String domainId) {
        return find("domainId", domainId).firstResult();
    }

    /**
     * 複数のドメインIDでサイト文言を一括検索
     *
     * @param domainIds
     *            ドメインID群
     * @return 該当するサイト文言のリスト
     */
    public Uni<List<SiteContentTableRecord>> findByIds(Collection<String> domainIds) {
        return list("domainId in ?1", domainIds);
    }

    /**
     * すべてのサイト文言をキーの昇順で取得
     *
     * <p>
     * 全件を返す。文言の件数は数十のままである見込みで、ページネーションを持たせる利点がない。並びを固定するのは
     * 管理画面の一覧が呼び出しごとに入れ替わらないようにするため。
     * </p>
     *
     * @return サイト文言のリスト（キーの昇順）
     */
    public Uni<List<SiteContentTableRecord>> listAllOrderByKey() {
        return listAll(Sort.by("contentKey"));
    }

    /**
     * ドメインIDで削除
     *
     * <p>
     * サイト文言の集約は子を持たないため、実体を読まずにDELETE文を発行してよい。子を持つ集約
     * （{@link AlbumDataSource#deleteByAlbumId}・{@link ArticleDataSource#deleteByArticleId}）は
     * 実体を読んでから消す。この集約に子が生えたら、そちらへ揃える。
     * </p>
     *
     * @param domainId
     *            ドメインID
     * @return 削除された場合true
     */
    public Uni<Boolean> deleteByDomainId(String domainId) {
        return delete("domainId", domainId).onItem().transform(count -> count > 0);
    }

    /**
     * 複数のドメインIDで一括削除
     *
     * @param domainIds
     *            ドメインID群
     * @return 完了シグナル
     */
    public Uni<Void> deleteByDomainIds(Collection<String> domainIds) {
        return delete("domainId in ?1", domainIds).replaceWithVoid();
    }

    /**
     * ドメインIDで存在確認
     *
     * @param domainId
     *            ドメインID
     * @return 存在する場合true
     */
    public Uni<Boolean> existsByDomainId(String domainId) {
        return count("domainId", domainId).onItem().transform(count -> count > 0);
    }
}
