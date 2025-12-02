package com.abservice.infrastructure.persistence.datasource;

import com.abservice.infrastructure.persistence.entity.AlbumArticleEntity;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.reactive.mutiny.Mutiny;

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

    private final Mutiny.SessionFactory sessionFactory;

    public AlbumArticleDataSource(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * アルバムIDでアルバム記事を取得
     *
     * @param albumId
     *            アルバムID
     * @return 該当するアルバム記事（存在しない場合はnull）
     */
    public Uni<AlbumArticleEntity> findByAlbumId(Long albumId) {
        return findById(albumId);
    }

    /**
     * ラベルタグでアルバム記事を検索
     *
     * @param labelTag
     *            ラベルタグ
     * @return 該当するアルバム記事のリスト
     */
    public Uni<List<AlbumArticleEntity>> findByLabelTag(String labelTag) {
        return list("labelTag", labelTag);
    }

    /**
     * 初出イベントスペースでアルバム記事を検索（部分一致）
     *
     * @param spaceKeyword
     *            イベントスペースキーワード
     * @return 該当するアルバム記事のリスト
     */
    public Uni<List<AlbumArticleEntity>> findByFirstEventSpaceContaining(String spaceKeyword) {
        return sessionFactory.withSession(session -> session.createQuery(
                "SELECT aa FROM AlbumArticleEntity aa WHERE aa.firstEventSpace LIKE :keyword",
                AlbumArticleEntity.class)
                .setParameter("keyword", "%" + spaceKeyword + "%")
                .getResultList());
    }

    /**
     * 頒布情報を持つアルバム記事を検索
     *
     * @return 頒布情報を持つアルバム記事のリスト
     */
    public Uni<List<AlbumArticleEntity>> findWithDistribution() {
        return sessionFactory.withSession(session -> session.createQuery(
                "SELECT DISTINCT aa FROM AlbumArticleEntity aa " +
                        "LEFT JOIN FETCH aa.album a " +
                        "LEFT JOIN FETCH a.albumDistribution " +
                        "WHERE a.albumDistribution IS NOT NULL",
                AlbumArticleEntity.class)
                .getResultList());
    }

    /**
     * 入手経路を持つアルバム記事を検索
     *
     * @return 入手経路を持つアルバム記事のリスト
     */
    public Uni<List<AlbumArticleEntity>> findWithAcquisitionChannels() {
        return sessionFactory.withSession(session -> session.createQuery(
                "SELECT DISTINCT aa FROM AlbumArticleEntity aa " +
                        "LEFT JOIN FETCH aa.album a " +
                        "LEFT JOIN FETCH a.acquisitionChannels " +
                        "WHERE SIZE(a.acquisitionChannels) > 0",
                AlbumArticleEntity.class)
                .getResultList());
    }

    /**
     * アルバムIDで削除
     *
     * @param albumId
     *            アルバムID
     * @return 削除された場合true
     */
    public Uni<Boolean> deleteByAlbumId(Long albumId) {
        return deleteById(albumId).onItem().transform(deleted -> deleted);
    }

    /**
     * アルバムIDでアルバム記事が存在するか確認
     *
     * @param albumId
     *            アルバムID
     * @return 存在する場合true
     */
    public Uni<Boolean> existsByAlbumId(Long albumId) {
        return count("albumId", albumId).onItem().transform(count -> count > 0);
    }
}
