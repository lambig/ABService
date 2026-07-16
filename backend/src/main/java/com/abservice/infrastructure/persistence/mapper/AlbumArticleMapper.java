package com.abservice.infrastructure.persistence.mapper;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.albumarticle.AlbumArticle;
import com.abservice.domain.model.vo.album.LabelTag;
import com.abservice.infrastructure.persistence.entity.AlbumArticleEntity;

import java.util.Collections;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

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
    public static @Nullable AlbumArticle toDomain(@Nullable AlbumArticleEntity entity) {
        // 頒布情報は簡略化のためnull、入手経路は簡略化のため空リスト
        return Optional.ofNullable(entity)
                .map(
                        e -> AlbumArticle.reconstruct(
                                new Album.Id(e.getDomainId()),
                                e.getIntroLong(),
                                e.getIntroShort(),
                                e.getFirstEventSpace(),
                                Optional.ofNullable(e.getLabelTag())
                                        .map(LabelTag::valueOf)
                                        .orElse(null),
                                null,
                                Collections.emptyList()))
                .orElse(null);
    }

    /**
     * DomainモデルからEntityへ変換
     *
     * @param albumArticle
     *            AlbumArticle
     * @return AlbumArticleEntity
     */
    public static AlbumArticleEntity toEntity(AlbumArticle albumArticle) {
        final var albumArticleEntity = new AlbumArticleEntity();
        albumArticleEntity.setDomainId(albumArticle.albumId().value());
        albumArticleEntity.setIntroLong(albumArticle.introLong());
        albumArticleEntity.setIntroShort(albumArticle.introShort());
        albumArticleEntity.setFirstEventSpace(albumArticle.firstEventSpace());
        albumArticleEntity.setLabelTag(
                Optional.ofNullable(albumArticle.labelTag())
                        .map(LabelTag::name)
                        .orElse(null));

        // 頒布情報と入手経路は簡略化のため省略

        return albumArticleEntity;
    }
}
