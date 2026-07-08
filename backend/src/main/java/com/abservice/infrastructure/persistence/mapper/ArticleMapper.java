package com.abservice.infrastructure.persistence.mapper;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.article.MarkupContent;
import com.abservice.domain.model.vo.article.MarkupFormat;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.infrastructure.persistence.entity.ArticleEntity;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

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
        return Optional.ofNullable(entity)
                .map(e -> Article.reconstruct(new Article.Id(e.getDomainId()), ArticleType.valueOf(e.getArticleType()),
                        Optional.ofNullable(e.getAlbumId()).map(Album.Id::new).orElse(null), e.getTitle(),
                        createMarkupContent(e.getBody(), e.getBodyFormat()), e.getIntroShort(),
                        toBusinessDateTime(e.getPublishedAt()), toBusinessDateTime(e.getUpdatedAtBusiness()),
                        Optional.ofNullable(e.getIsPublic()).orElse(false), Collections.emptyList()))
                .orElse(null);
    }

    private static BusinessDateTime toBusinessDateTime(Instant instant) {
        return Optional.ofNullable(instant).map(BusinessDateTime::of).orElse(null);
    }

    private static MarkupContent createMarkupContent(String body, String bodyFormat) {
        return switch (body) {
            case null -> null;
            default -> {
                final MarkupFormat format = Optional.ofNullable(bodyFormat).map(MarkupFormat::valueOf)
                        .orElse(MarkupFormat.PLAIN_TEXT);
                yield new MarkupContent(body, format);
            }
        };
    }

    /**
     * DomainモデルからEntityへ変換
     *
     * @param article
     *            Article
     * @return ArticleEntity
     */
    public static ArticleEntity toEntity(Article article) {
        return Optional.ofNullable(article).map(a -> {
            final var articleEntity = new ArticleEntity();
            articleEntity.setDomainId(a.id().value());
            articleEntity.setArticleType(a.articleType().name());
            articleEntity.setAlbumId(Optional.ofNullable(a.albumId()).map(Album.Id::value).orElse(null));
            articleEntity.setTitle(a.title());
            Optional.ofNullable(a.body()).ifPresentOrElse(body -> {
                articleEntity.setBody(body.content());
                articleEntity.setBodyFormat(body.format().name());
            }, () -> {
                articleEntity.setBody(null);
                articleEntity.setBodyFormat(MarkupFormat.PLAIN_TEXT.name());
            });
            articleEntity.setIntroShort(a.introShort());
            articleEntity.setPublishedAt(toInstant(a.publishedAt()));
            articleEntity.setUpdatedAtBusiness(toInstant(a.updatedAtBusiness()));
            articleEntity.setIsPublic(a.publicFlag());
            return articleEntity;
        }).orElse(null);
    }

    private static Instant toInstant(BusinessDateTime businessDateTime) {
        return Optional.ofNullable(businessDateTime).map(BusinessDateTime::value).orElse(null);
    }
}
