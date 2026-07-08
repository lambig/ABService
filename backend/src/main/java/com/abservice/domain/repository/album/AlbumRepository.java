package com.abservice.domain.repository.album;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.CatalogNumber;
import com.abservice.domain.repository.Repository;
import io.smallrye.mutiny.Uni;

import java.util.List;

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
    Uni<List<Album>> findByTitle(AlbumTitle title);

    /**
     * アーティスト名でアルバムを検索
     *
     * @param artistName
     *            アーティスト名
     * @return 該当するアルバムのリスト
     */
    Uni<List<Album>> findByArtistName(String artistName);

    /**
     * イベント名でアルバムを検索
     *
     * @param eventName
     *            イベント名
     * @return 該当するアルバムのリスト
     */
    Uni<List<Album>> findByEventName(String eventName);

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
    Uni<List<Album>> findByReleaseYear(int year);
}
