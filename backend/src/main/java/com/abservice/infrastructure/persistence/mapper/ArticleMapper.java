package com.abservice.infrastructure.persistence.mapper;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.infrastructure.persistence.entity.ArticleEntity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

/**
 * Article Mapper
 *
 * <p>
 * ArticleドメインモデルとArticleEntityの相互変換を担当します。
 * </p>
 */
public final class ArticleMapper {

    private ArticleMapper() {
        // ユーティリティクラス
    }

    /**
     * EntityからDomainモデルへ変換
     *
     * @param entity
     *            ArticleEntity
     * @return Article
     */
    public static Article toDomain(ArticleEntity entity) {
        if (entity == null) {
            return null;
        }

        return new Article(new Article.Id(entity.getDomainId()), ArticleType.valueOf(entity.getArticleType()),
                entity.getAlbumId() != null ? new Album.Id(entity.getAlbumId()) : null, entity.getTitle(),
                entity.getBody(), entity.getIntroShort(),
                entity.getPublishedAt() != null
                        ? LocalDateTime.ofInstant(entity.getPublishedAt(), ZoneOffset.UTC)
                        : null,
                entity.getUpdatedAtBusiness() != null
                        ? LocalDateTime.ofInstant(entity.getUpdatedAtBusiness(), ZoneOffset.UTC)
                        : null,
                entity.getIsPublic() != null ? entity.getIsPublic() : false, Collections.emptyList()); // タグは簡略化のため空リスト
    }

    /**
     * DomainモデルからEntityへ変換
     *
     * @param article
     *            Article
     * @return ArticleEntity
     */
    public static ArticleEntity toEntity(Article article) {
        if (article == null) {
            return null;
        }

        var articleEntity = new ArticleEntity();
        articleEntity.setDomainId(article.id().value());
        articleEntity.setArticleType(article.articleType().name());
        articleEntity.setAlbumId(article.albumId() != null ? article.albumId().value() : null);
        articleEntity.setTitle(article.title());
        articleEntity.setBody(article.body());
        articleEntity.setIntroShort(article.introShort());
        articleEntity
                .setPublishedAt(article.publishedAt() != null ? article.publishedAt().toInstant(ZoneOffset.UTC) : null);
        articleEntity.setUpdatedAtBusiness(
                article.updatedAtBusiness() != null ? article.updatedAtBusiness().toInstant(ZoneOffset.UTC) : null);
        articleEntity.setIsPublic(article.publicFlag());

        // タグリンクは簡略化のため省略

        return articleEntity;
    }
}
