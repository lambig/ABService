package com.abservice.application.query.article;

import com.abservice.application.query.article.model.ArticleView;
import com.abservice.infrastructure.persistence.entity.ArticleTableRecord;
import java.util.Optional;

/**
 * 記事エンティティから Read Model（{@link ArticleView}）への変換
 *
 * <p>
 * CQRS の Read 側マッパー。{@code infrastructure.persistence.datasource} が返す
 * {@link ArticleTableRecord} を照会結果 DTO へ平坦化します。ドメインモデルを経由しません。
 * </p>
 */
final class ArticleViewMapper {

    private ArticleViewMapper() {
    }

    /**
     * エンティティを Read Model へ変換します。
     *
     * @param entity
     *            記事エンティティ
     * @return 記事の Read Model
     */
    static ArticleView toView(ArticleTableRecord entity) {
        return new ArticleView(
                entity.getDomainId(),
                entity.getArticleType(),
                entity.getAlbumId(),
                entity.getTitle(),
                entity.getBody(),
                entity.getBodyFormat(),
                entity.getIntroShort(),
                entity.getPublishedAt(),
                entity.getUpdatedAtBusiness(),
                Optional.ofNullable(entity.getIsPublic())
                        .orElse(false));
    }
}
