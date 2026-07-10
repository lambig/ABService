package com.abservice.infrastructure.persistence.datasource;

import com.abservice.infrastructure.persistence.entity.AlbumEntity;
import com.abservice.infrastructure.persistence.entity.TrackEntity;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.reactive.mutiny.Mutiny;

import java.time.LocalDate;
import java.util.List;

/**
 * Album DataSource (DAO)
 *
 * <p>
 * Panacheを使用したアルバムデータアクセス層。 アルバムとその関連エンティティの永続化を担当する。
 * </p>
 */
@ApplicationScoped
public class AlbumDataSource implements PanacheRepositoryBase<AlbumEntity, Long> {

    private final Mutiny.SessionFactory sessionFactory;

    public AlbumDataSource(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * アルバムとその関連エンティティを永続化
     *
     * @param albumEntity
     *            アルバムエンティティ
     * @return 永続化されたアルバムエンティティ
     */
    public Uni<AlbumEntity> persistAlbumWithRelations(AlbumEntity albumEntity) {
        return persist(albumEntity).onItem()
                .transformToUni(savedAlbum -> sessionFactory.withSession(session -> switch (albumEntity.getTracks()) {
                    // トラックをpersist
                    case null -> Uni.createFrom().item(savedAlbum);
                    case List<TrackEntity> tracks when tracks.isEmpty() -> Uni.createFrom().item(savedAlbum);
                    default -> {
                        albumEntity.getTracks().forEach(track -> track.setAlbum(savedAlbum));
                        yield session.persistAll(albumEntity.getTracks().toArray()).replaceWith(savedAlbum);
                    }
                }));
    }

    /**
     * IDでアルバムを検索（トラック含む）
     *
     * @param domainId
     *            アルバムのドメインID
     * @return アルバムエンティティ（トラックを含む）
     */
    public Uni<AlbumEntity> findByIdWithTracks(String domainId) {
        return sessionFactory.withSession(
                session -> session.createQuery(
                        "SELECT DISTINCT a FROM AlbumEntity a " + "LEFT JOIN FETCH a.tracks "
                                + "WHERE a.domainId = :domainId",
                        AlbumEntity.class).setParameter("domainId", domainId).getSingleResultOrNull());
    }

    /**
     * タイトルでアルバムを検索
     *
     * @param title
     *            アルバムタイトル
     * @return 該当するアルバムのリスト
     */
    public Uni<List<AlbumEntity>> findByTitle(String title) {
        return list("title", title);
    }

    /**
     * アーティスト表示名でアルバムを検索
     *
     * @param artistDisplayName
     *            アーティスト表示名
     * @return 該当するアルバムのリスト
     */
    public Uni<List<AlbumEntity>> findByArtistDisplayName(String artistDisplayName) {
        return list("artistDisplayName", artistDisplayName);
    }

    /**
     * イベント名でアルバムを検索
     *
     * @param eventName
     *            イベント名
     * @return 該当するアルバムのリスト
     */
    public Uni<List<AlbumEntity>> findByEventName(String eventName) {
        return list("eventName", eventName);
    }

    /**
     * カタログナンバーでアルバムを検索
     *
     * @param catalogNumber
     *            カタログナンバー
     * @return 該当するアルバム（存在しない場合はnull）
     */
    public Uni<AlbumEntity> findByCatalogNumber(String catalogNumber) {
        return find("catalogNumber", catalogNumber).firstResult();
    }

    /**
     * リリース年でアルバムを検索
     *
     * @param year
     *            リリース年
     * @return 該当するアルバムのリスト
     */
    public Uni<List<AlbumEntity>> findByReleaseYear(int year) {
        final LocalDate startDate = LocalDate.of(year, 1, 1);
        final LocalDate endDate = LocalDate.of(year, 12, 31);

        return sessionFactory.withSession(
                session -> session
                        .createQuery(
                                "SELECT a FROM AlbumEntity a "
                                        + "WHERE a.releaseDate >= :startDate AND a.releaseDate <= :endDate",
                                AlbumEntity.class)
                        .setParameter("startDate", startDate).setParameter("endDate", endDate).getResultList());
    }

    /**
     * アルバムIDで削除
     *
     * @param domainId
     *            アルバムドメインID
     * @return 削除された場合true
     */
    public Uni<Boolean> deleteByAlbumId(String domainId) {
        return delete("domainId", domainId).onItem().transform(count -> count > 0);
    }

    /**
     * アルバムIDでアルバムが存在するか確認
     *
     * @param domainId
     *            アルバムドメインID
     * @return 存在する場合true
     */
    public Uni<Boolean> existsByAlbumId(String domainId) {
        return count("domainId", domainId).onItem().transform(count -> count > 0);
    }
}
