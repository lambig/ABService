package com.abservice.application.query.albumarticle;

import com.abservice.application.query.albumarticle.model.AlbumArticleView;
import com.abservice.infrastructure.persistence.entity.AlbumArticleTableRecord;

/**
 * アルバム記事エンティティから Read Model（{@link AlbumArticleView}）への変換
 *
 * <p>
 * CQRS の Read 側マッパー。{@code infrastructure.persistence.datasource} が返す
 * {@link AlbumArticleTableRecord} を照会結果 DTO へ平坦化します。ドメインモデルを経由しません。
 * </p>
 */
final class AlbumArticleViewMapper {

    private AlbumArticleViewMapper() {
    }

    /**
     * エンティティを Read Model へ変換します。
     *
     * @param entity
     *            アルバム記事エンティティ
     * @return アルバム記事の Read Model
     */
    static AlbumArticleView toView(AlbumArticleTableRecord entity) {
        return new AlbumArticleView(
                entity.getDomainId(),
                entity.getIntroLong(),
                entity.getIntroShort(),
                entity.getFirstEventSpace(),
                entity.getLabelTag());
    }
}
