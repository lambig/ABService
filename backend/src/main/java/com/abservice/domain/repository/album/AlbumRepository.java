package com.abservice.domain.repository.album;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.artistcredit.ArtistCredit;
import com.abservice.domain.model.aggregate.event.Event;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.CatalogNumber;
import com.abservice.domain.repository.Repository;
import io.smallrye.mutiny.Uni;

/**
 * アルバムリポジトリ
 *
 * <p>
 * Album集約の永続化と取得を担当します。
 * </p>
 */
public interface AlbumRepository extends Repository<Album, Album.Id> {

    /**
     * アルバムタイトルでアルバムを検索
     *
     * @param title
     *            アルバムタイトル
     * @return 該当するアルバムのリスト
     */
    Uni<java.util.List<Album>> findByTitle(AlbumTitle title);

    /**
     * アーティストクレジットIDでアルバムを検索
     *
     * @param artistCreditId
     *            アーティストクレジットID
     * @return 該当するアルバムのリスト
     */
    Uni<java.util.List<Album>> findByArtistCreditId(ArtistCredit.Id artistCreditId);

    /**
     * イベントIDでアルバムを検索
     *
     * @param eventId
     *            イベントID
     * @return 該当するアルバムのリスト
     */
    Uni<java.util.List<Album>> findByEventId(Event.Id eventId);

    /**
     * カタログナンバーでアルバムを検索
     *
     * @param catalogNumber
     *            カタログナンバー
     * @return 該当するアルバム、存在しない場合はnull
     */
    Uni<Album> findByCatalogNumber(CatalogNumber catalogNumber);

    /**
     * リリース年でアルバムを検索
     *
     * @param year
     *            リリース年
     * @return 該当するアルバムのリスト
     */
    Uni<java.util.List<Album>> findByReleaseYear(int year);
}
