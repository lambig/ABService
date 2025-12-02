package com.abservice.infrastructure.persistence.datasource;

import com.abservice.infrastructure.persistence.entity.ArtistCreditEntity;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.List;

/**
 * ArtistCredit DataSource (DAO)
 *
 * <p>
 * Panacheを使用したアーティスト名義データアクセス層。
 * </p>
 */
@ApplicationScoped
public class ArtistCreditDataSource implements PanacheRepositoryBase<ArtistCreditEntity, Long> {

    private final Mutiny.SessionFactory sessionFactory;

    public ArtistCreditDataSource(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * 表記名でアーティスト名義を検索
     *
     * @param displayName
     *            表記名
     * @return 該当するアーティスト名義（存在しない場合はnull）
     */
    public Uni<ArtistCreditEntity> findByDisplayName(String displayName) {
        return find("displayName", displayName).firstResult();
    }

    /**
     * 表記名で部分一致検索
     *
     * @param nameKeyword
     *            表記名キーワード
     * @return 該当するアーティスト名義のリスト
     */
    public Uni<List<ArtistCreditEntity>> findByDisplayNameContaining(String nameKeyword) {
        return sessionFactory.withSession(session -> session.createQuery(
                "SELECT a FROM ArtistCreditEntity a WHERE a.displayName LIKE :keyword",
                ArtistCreditEntity.class)
                .setParameter("keyword", "%" + nameKeyword + "%")
                .getResultList());
    }

    /**
     * ソートキーでアーティスト名義を検索
     *
     * @param sortKey
     *            ソートキー
     * @return 該当するアーティスト名義のリスト
     */
    public Uni<List<ArtistCreditEntity>> findBySortKey(String sortKey) {
        return list("sortKey", sortKey);
    }

    /**
     * すべてのアーティスト名義をソートキー順で取得
     *
     * @return すべてのアーティスト名義のリスト（ソートキー順）
     */
    public Uni<List<ArtistCreditEntity>> findAllOrderBySortKey() {
        return sessionFactory.withSession(session -> session.createQuery(
                "SELECT a FROM ArtistCreditEntity a ORDER BY a.sortKey, a.displayName",
                ArtistCreditEntity.class)
                .getResultList());
    }

    /**
     * アーティスト名義IDで削除
     *
     * @param id
     *            アーティスト名義ID
     * @return 削除された場合true
     */
    public Uni<Boolean> deleteByArtistCreditId(Long id) {
        return delete("artistCreditId", id).onItem().transform(count -> count > 0);
    }

    /**
     * アーティスト名義IDでアーティスト名義が存在するか確認
     *
     * @param id
     *            アーティスト名義ID
     * @return 存在する場合true
     */
    public Uni<Boolean> existsByArtistCreditId(Long id) {
        return count("artistCreditId", id).onItem().transform(count -> count > 0);
    }
}
