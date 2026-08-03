package com.abservice.infrastructure.persistence.datasource;

import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import com.abservice.infrastructure.persistence.entity.TrackTableRecord;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.reactive.mutiny.Mutiny;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Album DataSource (DAO)
 *
 * <p>
 * Panacheを使用したアルバムデータアクセス層。 アルバムとその関連エンティティの永続化を担当する。
 * </p>
 */
@ApplicationScoped
public class AlbumDataSource implements PanacheRepositoryBase<AlbumTableRecord, Long> {

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
    public Uni<AlbumTableRecord> persistAlbumWithRelations(AlbumTableRecord albumEntity) {
        return persist(albumEntity).onItem()
                .transformToUni(
                        savedAlbum -> sessionFactory.withSession(
                                session -> hasTracks(albumEntity)
                                        ? persistTracks(
                                                session,
                                                savedAlbum,
                                                albumEntity.getTracks())
                                        : Uni.createFrom().item(savedAlbum)));
    }

    private static boolean hasTracks(AlbumTableRecord albumEntity) {
        return Optional.ofNullable(albumEntity.getTracks())
                .filter(Predicate.not(List::isEmpty))
                .isPresent();
    }

    private static Uni<AlbumTableRecord> persistTracks(
            Mutiny.Session session,
            AlbumTableRecord savedAlbum,
            List<TrackTableRecord> tracks) {
        return session
                .persistAll(tracks.stream().peek(track -> track.setAlbum(savedAlbum)).toArray())
                .replaceWith(savedAlbum);
    }

    /**
     * ドメインIDでアルバムを検索
     *
     * @param domainId
     *            アルバムのドメインID
     * @return 該当するアルバム（存在しない場合はnull）
     */
    public Uni<AlbumTableRecord> findByDomainId(String domainId) {
        return find("domainId", domainId).firstResult();
    }

    /**
     * IDでアルバムを検索（トラック含む）
     *
     * @param domainId
     *            アルバムのドメインID
     * @return アルバムエンティティ（トラックを含む）
     */
    public Uni<AlbumTableRecord> findByIdWithTracks(String domainId) {
        return sessionFactory.withSession(
                session -> session.createQuery(
                        "SELECT DISTINCT a FROM AlbumTableRecord a " + "LEFT JOIN FETCH a.tracks "
                                + "WHERE a.domainId = :domainId",
                        AlbumTableRecord.class).setParameter("domainId", domainId).getSingleResultOrNull());
    }

    /**
     * 複数のドメインIDでアルバムを一括検索（トラック含む）
     *
     * @param domainIds
     *            アルバムのドメインID群
     * @return 該当するアルバムエンティティのリスト（トラックを含む）
     */
    public Uni<List<AlbumTableRecord>> findByIdsWithTracks(Collection<String> domainIds) {
        return sessionFactory.withSession(
                session -> session.createQuery(
                        "SELECT DISTINCT a FROM AlbumTableRecord a " + "LEFT JOIN FETCH a.tracks "
                                + "WHERE a.domainId IN (:domainIds)",
                        AlbumTableRecord.class).setParameter("domainIds", domainIds).getResultList());
    }

    /**
     * タイトルでアルバムを検索
     *
     * @param title
     *            アルバムタイトル
     * @return 該当するアルバムのリスト
     */
    public Uni<List<AlbumTableRecord>> findByTitle(String title) {
        return list("title", title);
    }

    /**
     * アーティスト表示名でアルバムを検索
     *
     * @param artistDisplayName
     *            アーティスト表示名
     * @return 該当するアルバムのリスト
     */
    public Uni<List<AlbumTableRecord>> findByArtistDisplayName(String artistDisplayName) {
        return list("artistDisplayName", artistDisplayName);
    }

    /**
     * イベント名でアルバムを検索
     *
     * @param eventName
     *            イベント名
     * @return 該当するアルバムのリスト
     */
    public Uni<List<AlbumTableRecord>> findByEventName(String eventName) {
        return list("eventName", eventName);
    }

    /**
     * カタログナンバーでアルバムを検索
     *
     * @param catalogNumber
     *            カタログナンバー
     * @return 該当するアルバム（存在しない場合はnull）
     */
    public Uni<AlbumTableRecord> findByCatalogNumber(String catalogNumber) {
        return find("catalogNumber", catalogNumber).firstResult();
    }

    /**
     * リリース年でアルバムを検索
     *
     * @param year
     *            リリース年
     * @return 該当するアルバムのリスト
     */
    public Uni<List<AlbumTableRecord>> findByReleaseYear(int year) {
        final LocalDate startDate = LocalDate.of(
                year,
                1,
                1);
        final LocalDate endDate = LocalDate.of(
                year,
                12,
                31);

        return sessionFactory.withSession(
                session -> session
                        .createQuery(
                                "SELECT a FROM AlbumTableRecord a "
                                        + "WHERE a.releaseDate >= :startDate AND a.releaseDate <= :endDate",
                                AlbumTableRecord.class)
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
     * 複数のドメインIDでアルバムを一括削除
     *
     * @param domainIds
     *            アルバムのドメインID群
     * @return 完了シグナル
     */
    public Uni<Void> deleteByAlbumIds(Collection<String> domainIds) {
        return delete("domainId in ?1", domainIds).replaceWithVoid();
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
