package com.abservice.infrastructure.persistence.mapper;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.albumarticle.AlbumArticle;
import com.abservice.domain.model.vo.album.LabelTag;
import com.abservice.infrastructure.persistence.entity.AlbumArticleEntity;

import java.util.Collections;

/**
 * AlbumArticle Mapper
 *
 * <p>
 * AlbumArticleドメインモデルとAlbumArticleEntityの相互変換を担当します。
 * </p>
 */
public final class AlbumArticleMapper {

    private AlbumArticleMapper() {
        // ユーティリティクラス
    }

    /**
     * EntityからDomainモデルへ変換
     *
     * @param entity
     *            AlbumArticleEntity
     * @return AlbumArticle
     */
    public static AlbumArticle toDomain(AlbumArticleEntity entity) {
        if (entity == null) {
            return null;
        }

        return AlbumArticle.reconstruct(new Album.Id(entity.getDomainId()), entity.getIntroLong(),
                entity.getIntroShort(), entity.getFirstEventSpace(),
                entity.getLabelTag() != null ? LabelTag.valueOf(entity.getLabelTag()) : null, null, // 頒布情報は簡略化のためnull
                Collections.emptyList() // 入手経路は簡略化のため空リスト
        );
    }

    /**
     * DomainモデルからEntityへ変換
     *
     * @param albumArticle
     *            AlbumArticle
     * @return AlbumArticleEntity
     */
    public static AlbumArticleEntity toEntity(AlbumArticle albumArticle) {
        if (albumArticle == null) {
            return null;
        }

        final var albumArticleEntity = new AlbumArticleEntity();
        albumArticleEntity.setDomainId(albumArticle.albumId().value());
        albumArticleEntity.setIntroLong(albumArticle.introLong());
        albumArticleEntity.setIntroShort(albumArticle.introShort());
        albumArticleEntity.setFirstEventSpace(albumArticle.firstEventSpace());
        albumArticleEntity.setLabelTag(albumArticle.labelTag() != null ? albumArticle.labelTag().name() : null);

        // 頒布情報と入手経路は簡略化のため省略

        return albumArticleEntity;
    }
}
