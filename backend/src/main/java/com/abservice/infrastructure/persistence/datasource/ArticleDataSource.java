package com.abservice.infrastructure.persistence.datasource;

import com.abservice.infrastructure.persistence.entity.ArticleEntity;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.reactive.mutiny.Mutiny;

import java.time.Instant;
import java.util.List;

/**
 * Article DataSource (DAO)
 *
 * <p>
 * Panacheを使用した記事データアクセス層。
 * </p>
 */
@ApplicationScoped
public class ArticleDataSource implements PanacheRepositoryBase<ArticleEntity, Long> {

    private final Mutiny.SessionFactory sessionFactory;

    public ArticleDataSource(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * 記事タイプで記事を検索
     *
     * @param articleType
     *            記事タイプ
     * @return 該当する記事のリスト
     */
    public Uni<List<ArticleEntity>> findByArticleType(String articleType) {
        return list("articleType", articleType);
    }

    /**
     * アルバムIDで記事を検索
     *
     * @param albumId
     *            アルバムID (domain_id)
     * @return 該当する記事（存在しない場合はnull）
     */
    public Uni<ArticleEntity> findByAlbumId(String albumId) {
        return find("albumId", albumId).firstResult();
    }

    /**
     * 公開フラグで記事を検索
     *
     * @param publicFlag
     *            公開フラグ
     * @return 該当する記事のリスト
     */
    public Uni<List<ArticleEntity>> findByPublicFlag(boolean publicFlag) {
        return list("isPublic", publicFlag);
    }

    /**
     * 公開日の範囲で記事を検索
     *
     * @param startDate
     *            開始日時
     * @param endDate
     *            終了日時
     * @return 該当する記事のリスト
     */
    public Uni<List<ArticleEntity>> findByPublishedAtBetween(Instant startDate, Instant endDate) {
        return sessionFactory.withSession(
                session -> session.createQuery(
                        "SELECT a FROM ArticleEntity a WHERE a.publishedAt >= :startDate AND a.publishedAt <= :endDate",
                        ArticleEntity.class).setParameter("startDate", startDate).setParameter("endDate", endDate)
                        .getResultList());
    }

    /**
     * タイトルで記事を検索（部分一致）
     *
     * @param titleKeyword
     *            タイトルキーワード
     * @return 該当する記事のリスト
     */
    public Uni<List<ArticleEntity>> findByTitleContaining(String titleKeyword) {
        return sessionFactory.withSession(
                session -> session
                        .createQuery("SELECT a FROM ArticleEntity a WHERE a.title LIKE :keyword", ArticleEntity.class)
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
