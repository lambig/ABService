package com.abservice.infrastructure.persistence.mapper;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.entity.article.ArticleTag;
import com.abservice.domain.model.vo.article.ArticleTitle;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.article.MarkupContent;
import com.abservice.domain.model.vo.article.MarkupFormat;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.infrastructure.persistence.entity.ArticleEntity;
import com.abservice.infrastructure.persistence.entity.ArticleTagEntity;
import com.abservice.infrastructure.persistence.entity.ArticleTagLinkEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

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
        return Article.reconstruct(
                new Article.Id(entity.getDomainId()),
                ArticleType.valueOf(entity.getArticleType()),
                Optional.ofNullable(entity.getAlbumId())
                        .map(Album.Id::new)
                        .orElse(null),
                ArticleTitle.of(entity.getTitle()),
                createMarkupContent(entity.getBody(), entity.getBodyFormat()),
                entity.getIntroShort(),
                toBusinessDateTime(entity.getPublishedAt()),
                toBusinessDateTime(entity.getUpdatedAtBusiness()),
                Optional.ofNullable(entity.getIsPublic())
                        .orElse(false),
                toTags(entity.getArticleTagLinks()));
    }

    /**
     * ArticleTagLinkEntityのリストからArticleTagのリストへ変換
     *
     * @param links
     *            ArticleTagLinkEntityのリスト
     * @return ArticleTagのリスト（linksがnullの場合は空リスト）
     */
    public static List<ArticleTag> toTags(@Nullable List<ArticleTagLinkEntity> links) {
        return Optional.ofNullable(links)
                .map(list -> list.stream().map(link -> toTag(link.getArticleTag())).toList())
                .orElseGet(List::of);
    }

    /**
     * ArticleTagEntityからArticleTagへ変換
     *
     * @param entity
     *            ArticleTagEntity
     * @return ArticleTag
     */
    public static ArticleTag toTag(ArticleTagEntity entity) {
        return ArticleTag.reconstruct(
                ArticleTag.Id.of(entity.getDomainId()),
                entity.getName());
    }

    /**
     * ArticleTagからArticleTagEntityへ変換（新規タグの永続化用。articleTagLinkとの関連付けは呼び出し側の責務）
     *
     * @param tag
     *            ArticleTag
     * @return ArticleTagEntity
     */
    public static ArticleTagEntity toTagEntity(ArticleTag tag) {
        final var entity = new ArticleTagEntity();
        entity.setDomainId(tag.id().value());
        entity.setName(tag.getName());
        return entity;
    }

    private static @Nullable BusinessDateTime toBusinessDateTime(@Nullable Instant instant) {
        return Optional.ofNullable(instant)
                .map(BusinessDateTime::of)
                .orElse(null);
    }

    private static @Nullable MarkupContent createMarkupContent(@Nullable String body, @Nullable String bodyFormat) {
        return Optional.ofNullable(body)
                .map(
                        b -> new MarkupContent(
                                b,
                                Optional.ofNullable(bodyFormat)
                                        .map(MarkupFormat::valueOf)
                                        .orElse(MarkupFormat.PLAIN_TEXT)))
                .orElse(null);
    }

    /**
     * DomainモデルからEntityへ変換
     *
     * @param article
     *            Article
     * @return ArticleEntity
     */
    public static ArticleEntity toEntity(Article article) {
        final var articleEntity = new ArticleEntity();
        articleEntity.setDomainId(article.id().value());
        articleEntity.setArticleType(article.articleType().name());
        articleEntity.setAlbumId(
                Optional.ofNullable(article.albumId())
                        .map(Album.Id::value)
                        .orElse(null));
        articleEntity.setTitle(article.title().value());
        Optional.ofNullable(article.body()).ifPresentOrElse(body -> {
            articleEntity.setBody(body.content());
            articleEntity.setBodyFormat(body.format().name());
        }, () -> {
            articleEntity.setBody(null);
            articleEntity.setBodyFormat(MarkupFormat.PLAIN_TEXT.name());
        });
        articleEntity.setIntroShort(article.introShort());
        articleEntity.setPublishedAt(toInstant(article.publishedAt()));
        articleEntity.setUpdatedAtBusiness(toInstant(article.updatedAtBusiness()));
        articleEntity.setIsPublic(article.publicFlag());
        return articleEntity;
    }

    private static @Nullable Instant toInstant(@Nullable BusinessDateTime businessDateTime) {
        return Optional.ofNullable(businessDateTime)
                .map(BusinessDateTime::value)
                .orElse(null);
    }
}
