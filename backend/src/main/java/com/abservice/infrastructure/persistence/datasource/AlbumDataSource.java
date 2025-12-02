package com.abservice.infrastructure.persistence.datasource;

import com.abservice.infrastructure.persistence.entity.AlbumEntity;
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
 * Panacheを使用したアルバムデータアクセス層。
 * アルバムとその関連エンティティの永続化を担当する。
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
        return persist(albumEntity)
                .onItem().transformToUni(savedAlbum -> sessionFactory.withSession(session -> {
                    // トラックをpersist
                    if (albumEntity.getTracks() != null && !albumEntity.getTracks().isEmpty()) {
                        albumEntity.getTracks().forEach(track -> track.setAlbum(savedAlbum));
                        return session.persistAll(albumEntity.getTracks().toArray())
                                .replaceWith(savedAlbum);
                    }
                    return Uni.createFrom().item(savedAlbum);
                }));
    }

    /**
     * IDでアルバムとその関連エンティティを取得
     *
     * @param id
     *            アルバムID
     * @return アルバムエンティティ（トラックを含む）
     */
    public Uni<AlbumEntity> findByIdWithTracks(Long id) {
        return sessionFactory.withSession(session -> session.createQuery(
                "SELECT DISTINCT a FROM AlbumEntity a " +
                        "LEFT JOIN FETCH a.tracks " +
                        "WHERE a.albumId = :id",
                AlbumEntity.class)
                .setParameter("id", id)
                .getSingleResultOrNull());
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
     * アーティストクレジットIDでアルバムを検索
     *
     * @param artistCreditId
     *            アーティストクレジットID
     * @return 該当するアルバムのリスト
     */
    public Uni<List<AlbumEntity>> findByArtistCreditId(Long artistCreditId) {
        return list("artistCreditId", artistCreditId);
    }

    /**
     * イベントIDでアルバムを検索
     *
     * @param eventId
     *            イベントID
     * @return 該当するアルバムのリスト
     */
    public Uni<List<AlbumEntity>> findByEventId(Long eventId) {
        return list("eventId", eventId);
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
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        return sessionFactory.withSession(session -> session.createQuery(
                "SELECT a FROM AlbumEntity a " +
                        "WHERE a.releaseDate >= :startDate AND a.releaseDate <= :endDate",
                AlbumEntity.class)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .getResultList());
    }

    /**
     * アルバムIDで削除
     *
     * @param id
     *            アルバムID
     * @return 削除された場合true
     */
    public Uni<Boolean> deleteByAlbumId(Long id) {
        return delete("albumId", id).onItem().transform(count -> count > 0);
    }

    /**
     * アルバムIDでアルバムが存在するか確認
     *
     * @param id
     *            アルバムID
     * @return 存在する場合true
     */
    public Uni<Boolean> existsByAlbumId(Long id) {
        return count("albumId", id).onItem().transform(count -> count > 0);
    }
}
