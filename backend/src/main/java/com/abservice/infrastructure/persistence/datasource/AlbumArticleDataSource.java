package com.abservice.infrastructure.persistence.datasource;

import com.abservice.infrastructure.persistence.entity.AlbumArticleTableRecord;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
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
public class AlbumArticleDataSource implements PanacheRepositoryBase<AlbumArticleTableRecord, Long> {

    private static final String EAGER_SELECT = "SELECT DISTINCT aa FROM AlbumArticleTableRecord aa "
            + "LEFT JOIN FETCH aa.album a "
            + "LEFT JOIN FETCH a.albumDistribution "
            + "LEFT JOIN FETCH a.acquisitionChannels ";

    private final Mutiny.SessionFactory sessionFactory;

    public AlbumArticleDataSource(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * ドメインIDでアルバム記事を検索（自身のカラムのみ。頒布情報・入手経路はJOINしない）
     *
     * @param domainId
     *            アルバム記事のドメインID
     * @return 該当するアルバム記事（存在しない場合はnull）
     */
    public Uni<AlbumArticleTableRecord> findByDomainId(String domainId) {
        return find("domainId", domainId).firstResult();
    }

    /**
     * アルバムIDでアルバム記事を取得（頒布情報・入手経路を含む）
     *
     * @param domainId
     *            アルバムID
     * @return 該当するアルバム記事（存在しない場合はnull）
     */
    public Uni<AlbumArticleTableRecord> findByAlbumId(String domainId) {
        return sessionFactory.withSession(
                session -> session.createQuery(
                        EAGER_SELECT + "WHERE aa.domainId = :domainId",
                        AlbumArticleTableRecord.class).setParameter("domainId", domainId).getSingleResultOrNull());
    }

    /**
     * アルバム記事を全件取得（頒布情報・入手経路を含む）
     *
     * @return アルバム記事のリスト
     */
    public Uni<List<AlbumArticleTableRecord>> findAllEager() {
        return sessionFactory.withSession(
                session -> session.createQuery(EAGER_SELECT, AlbumArticleTableRecord.class)
                        .getResultList());
    }

    /**
     * 複数のドメインIDでアルバム記事を一括検索（頒布情報・入手経路を含む）
     *
     * @param domainIds
     *            アルバム記事のドメインID群
     * @return 該当するアルバム記事のリスト
     */
    public Uni<List<AlbumArticleTableRecord>> findByIds(Collection<String> domainIds) {
        return sessionFactory.withSession(
                session -> session.createQuery(
                        EAGER_SELECT + "WHERE aa.domainId IN :domainIds",
                        AlbumArticleTableRecord.class).setParameter("domainIds", domainIds).getResultList());
    }

    /**
     * ラベルタグでアルバム記事を検索（頒布情報・入手経路を含む）
     *
     * @param labelTag
     *            ラベルタグ
     * @return 該当するアルバム記事のリスト
     */
    public Uni<List<AlbumArticleTableRecord>> findByLabelTag(String labelTag) {
        return sessionFactory.withSession(
                session -> session.createQuery(
                        EAGER_SELECT + "WHERE aa.labelTag = :labelTag",
                        AlbumArticleTableRecord.class).setParameter("labelTag", labelTag).getResultList());
    }

    /**
     * 初出イベントスペースでアルバム記事を検索（部分一致、頒布情報・入手経路を含む）
     *
     * @param spaceKeyword
     *            イベントスペースキーワード
     * @return 該当するアルバム記事のリスト
     */
    public Uni<List<AlbumArticleTableRecord>> findByFirstEventSpaceContaining(String spaceKeyword) {
        return sessionFactory.withSession(
                session -> session
                        .createQuery(
                                EAGER_SELECT + "WHERE aa.firstEventSpace LIKE :keyword",
                                AlbumArticleTableRecord.class)
                        .setParameter("keyword", "%" + spaceKeyword + "%").getResultList());
    }

    /**
     * 頒布情報を持つアルバム記事を検索
     *
     * @return 頒布情報を持つアルバム記事のリスト
     */
    public Uni<List<AlbumArticleTableRecord>> findWithDistribution() {
        return sessionFactory.withSession(
                session -> session.createQuery(
                        EAGER_SELECT + "WHERE a.albumDistribution IS NOT NULL",
                        AlbumArticleTableRecord.class).getResultList());
    }

    /**
     * 入手経路を持つアルバム記事を検索
     *
     * @return 入手経路を持つアルバム記事のリスト
     */
    public Uni<List<AlbumArticleTableRecord>> findWithAcquisitionChannels() {
        return sessionFactory.withSession(
                session -> session.createQuery(
                        EAGER_SELECT + "WHERE SIZE(a.acquisitionChannels) > 0",
                        AlbumArticleTableRecord.class).getResultList());
    }

    /**
     * アルバムのドメインIDから、記事・頒布情報・入手経路を含むAlbumTableRecordを取得する。
     * save時にAlbumTableRecordの{@code cascade = ALL}を介して3種の子エンティティを
     * 統一的に反映するためのエントリポイント。
     *
     * @param albumDomainId
     *            アルバムのドメインID
     * @return AlbumTableRecord（存在しない場合はnull）
     */
    public Uni<AlbumTableRecord> findAlbumWithArticleRelationsByDomainId(String albumDomainId) {
        return sessionFactory.withSession(
                session -> session.createQuery(
                        "SELECT a FROM AlbumTableRecord a " + "LEFT JOIN FETCH a.albumArticle "
                                + "LEFT JOIN FETCH a.albumDistribution " + "LEFT JOIN FETCH a.acquisitionChannels "
                                + "WHERE a.domainId = :domainId",
                        AlbumTableRecord.class).setParameter("domainId", albumDomainId).getSingleResultOrNull());
    }

    /**
     * ページ指定でアルバム記事を検索（一覧表示用・頒布情報/入手経路は含まない）
     *
     * <p>
     * 一覧表示の Read
     * Model（{@link com.abservice.application.query.albumarticle.model.AlbumArticleView}）は
     * 頒布情報・入手経路を含まないフラットDTOのため、JOIN FETCHを伴わない単純なページングで問題ない （JOIN
     * FETCH併用時のページング崩れを回避できる）。件数・総ページ数は返された {@link PanacheQuery} 自身の
     * {@code count()}/{@code pageCount()} から取得する。
     * </p>
     *
     * @param page
     *            ページ番号（0始まり）
     * @param size
     *            1ページの件数
     * @return ページングクエリ
     */
    public PanacheQuery<AlbumArticleTableRecord> pagedQuery(int page, int size) {
        return findAll(Sort.by("albumId"))
                .page(Page.of(page, size));
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
     * 複数のアルバムIDでアルバム記事を一括削除
     *
     * @param domainIds
     *            アルバムID群
     * @return 完了シグナル
     */
    public Uni<Void> deleteByAlbumIds(Collection<String> domainIds) {
        return delete("domainId in ?1", domainIds).replaceWithVoid();
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
