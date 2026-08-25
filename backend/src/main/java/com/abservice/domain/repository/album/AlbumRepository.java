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
     * IDでアルバムを取得する（主張を伴わない取得）
     *
     * <p>
     * 業務コードはこの取得を使わない。アルバムを取得する側は、編集権か参照のいずれかを主張したうえで {@code AlbumAccessService}
     * を通す（ArchUnitが検査する）。基底インターフェースからの継承のままでは呼び出しが
     * どの集約のリポジトリに向いたものか静的に追えないため、ここで再宣言して検査可能にしている。
     * </p>
     *
     * @param id
     *            アルバムID
     * @return アルバム、存在しない場合はnull
     */
    @Override
    Uni<Album> findById(Album.Id id);

    /**
     * IDでアルバムを取得し、呼び出し元のトランザクションが終わるまで他のトランザクションの更新を待たせる
     *
     * <p>
     * 集約をまたぐ不変条件は、判定に使ったアルバムが判定から書き込みまでの間に動かないことを前提にする。この取得は
     * その前提を満たすもので、取得したアルバムは呼び出し元のコミットまで他のトランザクションから更新されない。 業務コードからは
     * {@code AlbumAccessService} を通して使う。
     * </p>
     *
     * @param id
     *            アルバムID
     * @return アルバム、存在しない場合はnull
     */
    Uni<Album> findByIdExclusively(Album.Id id);

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
