package com.abservice.domain.repository.albumarticle;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.albumarticle.AlbumArticle;
import com.abservice.domain.model.vo.album.LabelTag;
import com.abservice.domain.repository.Repository;
import io.smallrye.mutiny.Uni;

import java.util.List;

/**
 * アルバム記事リポジトリ
 *
 * <p>
 * AlbumArticle集約の永続化と取得を担当します。
 * </p>
 * <p>
 * 注意: AlbumArticle集約のIDはAlbum.Idです（1対1関係）
 * </p>
 */
public interface AlbumArticleRepository extends Repository<AlbumArticle, Album.Id> {

    /**
     * アルバムIDでアルバム記事を取得
     *
     * @param albumId
     *            アルバムID
     * @return 該当するアルバム記事、存在しない場合はnull
     */
    Uni<AlbumArticle> findByAlbumId(Album.Id albumId);

    /**
     * ラベルタグでアルバム記事を検索
     *
     * @param labelTag
     *            ラベルタグ
     * @return 該当するアルバム記事のリスト
     */
    Uni<List<AlbumArticle>> findByLabelTag(LabelTag labelTag);

    /**
     * 初出イベントスペースでアルバム記事を検索（部分一致）
     *
     * @param spaceKeyword
     *            イベントスペースキーワード
     * @return 該当するアルバム記事のリスト
     */
    Uni<List<AlbumArticle>> findByFirstEventSpaceContaining(String spaceKeyword);

    /**
     * 頒布情報を持つアルバム記事を検索
     *
     * @return 頒布情報を持つアルバム記事のリスト
     */
    Uni<List<AlbumArticle>> findWithDistribution();

    /**
     * 入手経路を持つアルバム記事を検索
     *
     * @return 入手経路を持つアルバム記事のリスト
     */
    Uni<List<AlbumArticle>> findWithAcquisitionChannels();
}
