package com.abservice.infrastructure.persistence.datasource;

import com.abservice.infrastructure.persistence.entity.ArticleTableRecord;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.reactive.mutiny.Mutiny;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Article DataSource (DAO)
 *
 * <p>
 * Panacheを使用した記事データアクセス層。
 * </p>
 */
@ApplicationScoped
public class ArticleDataSource implements PanacheRepositoryBase<ArticleTableRecord, Long> {

    private static final String EAGER_SELECT = "SELECT DISTINCT a FROM ArticleTableRecord a "
            + "LEFT JOIN FETCH a.articleTagLinks link "
            + "LEFT JOIN FETCH link.articleTag ";

    private final Mutiny.SessionFactory sessionFactory;

    public ArticleDataSource(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * ドメインIDで記事を検索（タグを含む）
     *
     * @param domainId
     *            ドメインID
     * @return 該当する記事（存在しない場合はnull）
     */
    public Uni<ArticleTableRecord> findByDomainId(String domainId) {
        return sessionFactory.withSession(
                session -> session.createQuery(EAGER_SELECT + "WHERE a.domainId = :domainId", ArticleTableRecord.class)
                        .setParameter("domainId", domainId).getSingleResultOrNull());
    }

    /**
     * 記事を全件取得（タグを含む）
     *
     * @return 記事のリスト
     */
    public Uni<List<ArticleTableRecord>> findAllEager() {
        return sessionFactory.withSession(
                session -> session.createQuery(EAGER_SELECT, ArticleTableRecord.class)
                        .getResultList());
    }

    /**
     * 複数のドメインIDで記事を一括検索（タグを含む）
     *
     * @param domainIds
     *            記事のドメインID群
     * @return 該当する記事のリスト
     */
    public Uni<List<ArticleTableRecord>> findByIds(Collection<String> domainIds) {
        return sessionFactory.withSession(
                session -> session
                        .createQuery(EAGER_SELECT + "WHERE a.domainId IN :domainIds", ArticleTableRecord.class)
                        .setParameter("domainIds", domainIds).getResultList());
    }

    /**
     * 記事タイプで記事を検索（タグを含む）
     *
     * @param articleType
     *            記事タイプ
     * @return 該当する記事のリスト
     */
    public Uni<List<ArticleTableRecord>> findByArticleType(String articleType) {
        return sessionFactory.withSession(
                session -> session
                        .createQuery(EAGER_SELECT + "WHERE a.articleType = :articleType", ArticleTableRecord.class)
                        .setParameter("articleType", articleType).getResultList());
    }

    /**
     * アルバムIDで記事を検索（タグを含む）
     *
     * @param albumId
     *            アルバムID (domain_id)
     * @return 該当する記事（存在しない場合はnull）
     */
    public Uni<ArticleTableRecord> findByAlbumId(String albumId) {
        return sessionFactory.withSession(
                session -> session.createQuery(EAGER_SELECT + "WHERE a.albumId = :albumId", ArticleTableRecord.class)
                        .setParameter("albumId", albumId).getSingleResultOrNull());
    }

    /**
     * 公開フラグで記事を検索（タグを含む）
     *
     * @param publicFlag
     *            公開フラグ
     * @return 該当する記事のリスト
     */
    public Uni<List<ArticleTableRecord>> findByPublicFlag(boolean publicFlag) {
        return sessionFactory.withSession(
                session -> session
                        .createQuery(EAGER_SELECT + "WHERE a.isPublic = :publicFlag", ArticleTableRecord.class)
                        .setParameter("publicFlag", publicFlag).getResultList());
    }

    /**
     * 公開日の範囲で記事を検索（タグを含む）
     *
     * @param startDate
     *            開始日時
     * @param endDate
     *            終了日時
     * @return 該当する記事のリスト
     */
    public Uni<List<ArticleTableRecord>> findByPublishedAtBetween(Instant startDate, Instant endDate) {
        return sessionFactory.withSession(
                session -> session.createQuery(
                        EAGER_SELECT + "WHERE a.publishedAt >= :startDate AND a.publishedAt <= :endDate",
                        ArticleTableRecord.class).setParameter("startDate", startDate).setParameter("endDate", endDate)
                        .getResultList());
    }

    /**
     * タイトルで記事を検索（部分一致、タグを含む）
     *
     * @param titleKeyword
     *            タイトルキーワード
     * @return 該当する記事のリスト
     */
    public Uni<List<ArticleTableRecord>> findByTitleContaining(String titleKeyword) {
        return sessionFactory.withSession(
                session -> session
                        .createQuery(EAGER_SELECT + "WHERE a.title LIKE :keyword", ArticleTableRecord.class)
                        .setParameter("keyword", "%" + titleKeyword + "%").getResultList());
    }

    /**
     * 記事IDで削除
     *
     * @param id
     *            記事ID
     * @return 削除された場合true
     */
    public Uni<Boolean> deleteByArticleId(String domainId) {
        return delete("domainId", domainId).onItem().transform(count -> count > 0);
    }

    /**
     * 複数のドメインIDで記事を一括削除
     *
     * @param domainIds
     *            記事のドメインID群
     * @return 完了シグナル
     */
    public Uni<Void> deleteByArticleIds(Collection<String> domainIds) {
        return delete("domainId in ?1", domainIds).replaceWithVoid();
    }

    /**
     * 記事IDで記事が存在するか確認
     *
     * @param id
     *            記事ID
     * @return 存在する場合true
     */
    public Uni<Boolean> existsByArticleId(String domainId) {
        return count("domainId", domainId).onItem().transform(count -> count > 0);
    }
}
