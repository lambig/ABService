package com.abservice.infrastructure.persistence.datasource;

import com.abservice.infrastructure.persistence.entity.AlbumArticleEntity;
import com.abservice.infrastructure.persistence.entity.AlbumEntity;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.Collection;
import java.util.List;

/**
 * AlbumArticle DataSource (DAO)
 *
 * <p>
 * Panacheを使用したアルバム記事データアクセス層。
 * </p>
 */
@ApplicationScoped
public class AlbumArticleDataSource implements PanacheRepositoryBase<AlbumArticleEntity, Long> {

    private static final String EAGER_SELECT = "SELECT DISTINCT aa FROM AlbumArticleEntity aa "
            + "LEFT JOIN FETCH aa.album a "
            + "LEFT JOIN FETCH a.albumDistribution "
            + "LEFT JOIN FETCH a.acquisitionChannels ";

    private final Mutiny.SessionFactory sessionFactory;

    public AlbumArticleDataSource(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * アルバムIDでアルバム記事を取得（頒布情報・入手経路を含む）
     *
     * @param domainId
     *            アルバムID
     * @return 該当するアルバム記事（存在しない場合はnull）
     */
    public Uni<AlbumArticleEntity> findByAlbumId(String domainId) {
        return sessionFactory.withSession(
                session -> session.createQuery(
                        EAGER_SELECT + "WHERE aa.domainId = :domainId",
                        AlbumArticleEntity.class).setParameter("domainId", domainId).getSingleResultOrNull());
    }

    /**
     * アルバム記事を全件取得（頒布情報・入手経路を含む）
     *
     * @return アルバム記事のリスト
     */
    public Uni<List<AlbumArticleEntity>> findAllEager() {
        return sessionFactory.withSession(
                session -> session.createQuery(EAGER_SELECT, AlbumArticleEntity.class)
                        .getResultList());
    }

    /**
     * 複数のドメインIDでアルバム記事を一括検索（頒布情報・入手経路を含む）
     *
     * @param domainIds
     *            アルバム記事のドメインID群
     * @return 該当するアルバム記事のリスト
     */
    public Uni<List<AlbumArticleEntity>> findByIds(Collection<String> domainIds) {
        return sessionFactory.withSession(
                session -> session.createQuery(
                        EAGER_SELECT + "WHERE aa.domainId IN :domainIds",
                        AlbumArticleEntity.class).setParameter("domainIds", domainIds).getResultList());
    }

    /**
     * ラベルタグでアルバム記事を検索（頒布情報・入手経路を含む）
     *
     * @param labelTag
     *            ラベルタグ
     * @return 該当するアルバム記事のリスト
     */
    public Uni<List<AlbumArticleEntity>> findByLabelTag(String labelTag) {
        return sessionFactory.withSession(
                session -> session.createQuery(
                        EAGER_SELECT + "WHERE aa.labelTag = :labelTag",
                        AlbumArticleEntity.class).setParameter("labelTag", labelTag).getResultList());
    }

    /**
     * 初出イベントスペースでアルバム記事を検索（部分一致、頒布情報・入手経路を含む）
     *
     * @param spaceKeyword
     *            イベントスペースキーワード
     * @return 該当するアルバム記事のリスト
     */
    public Uni<List<AlbumArticleEntity>> findByFirstEventSpaceContaining(String spaceKeyword) {
        return sessionFactory.withSession(
                session -> session
                        .createQuery(
                                EAGER_SELECT + "WHERE aa.firstEventSpace LIKE :keyword",
                                AlbumArticleEntity.class)
                        .setParameter("keyword", "%" + spaceKeyword + "%").getResultList());
    }

    /**
     * 頒布情報を持つアルバム記事を検索
     *
     * @return 頒布情報を持つアルバム記事のリスト
     */
    public Uni<List<AlbumArticleEntity>> findWithDistribution() {
        return sessionFactory.withSession(
                session -> session.createQuery(
                        EAGER_SELECT + "WHERE a.albumDistribution IS NOT NULL",
                        AlbumArticleEntity.class).getResultList());
    }

    /**
     * 入手経路を持つアルバム記事を検索
     *
     * @return 入手経路を持つアルバム記事のリスト
     */
    public Uni<List<AlbumArticleEntity>> findWithAcquisitionChannels() {
        return sessionFactory.withSession(
                session -> session.createQuery(
                        EAGER_SELECT + "WHERE SIZE(a.acquisitionChannels) > 0",
                        AlbumArticleEntity.class).getResultList());
    }

    /**
     * アルバムのドメインIDから、記事・頒布情報・入手経路を含むAlbumEntityを取得する。
     * save時にAlbumEntityの{@code cascade = ALL}を介して3種の子エンティティを 統一的に反映するためのエントリポイント。
     *
     * @param albumDomainId
     *            アルバムのドメインID
     * @return AlbumEntity（存在しない場合はnull）
     */
    public Uni<AlbumEntity> findAlbumWithArticleRelationsByDomainId(String albumDomainId) {
        return sessionFactory.withSession(
                session -> session.createQuery(
                        "SELECT a FROM AlbumEntity a " + "LEFT JOIN FETCH a.albumArticle "
                                + "LEFT JOIN FETCH a.albumDistribution " + "LEFT JOIN FETCH a.acquisitionChannels "
                                + "WHERE a.domainId = :domainId",
                        AlbumEntity.class).setParameter("domainId", albumDomainId).getSingleResultOrNull());
    }

    /**
     * アルバムIDで削除
     *
     * @param albumId
     *            アルバムID
     * @return 削除された場合true
     */
    public Uni<Boolean> deleteByAlbumId(String domainId) {
        return delete("domainId", domainId).onItem().transform(deleted -> deleted > 0);
    }

    /**
     * アルバムIDでアルバム記事が存在するか確認
     *
     * @param albumId
     *            アルバムID
     * @return 存在する場合true
     */
    public Uni<Boolean> existsByAlbumId(String domainId) {
        return count("domainId", domainId).onItem().transform(count -> count > 0);
    }
}
