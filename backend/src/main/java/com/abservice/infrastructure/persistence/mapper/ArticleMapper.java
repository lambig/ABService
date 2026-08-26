package com.abservice.infrastructure.persistence.mapper;

import static com.abservice.lib.Iterables.toList;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.AlbumArticle;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.entity.article.ArticleTag;
import com.abservice.domain.model.vo.article.AlbumReference;
import com.abservice.domain.model.vo.article.AlbumReferenceLostReason;
import com.abservice.domain.model.vo.article.ArticleTitle;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.model.vo.common.MarkupContent;
import com.abservice.domain.model.vo.common.MarkupFormat;
import com.abservice.infrastructure.persistence.entity.ArticleTableRecord;
import com.abservice.infrastructure.persistence.entity.ArticleTagTableRecord;
import com.abservice.infrastructure.persistence.entity.ArticleTagLinkTableRecord;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Article Mapper
 *
 * <p>
 * ArticleドメインモデルとArticleTableRecordの相互変換を担当します。
 * </p>
 */
public final class ArticleMapper {

    private ArticleMapper() {
    }

    /**
     * EntityからDomainモデルへ変換
     *
     * @param entity
     *            ArticleTableRecord
     * @return Article
     */
    public static Article toDomain(ArticleTableRecord entity) {
        return Article.reconstruct(
                new Article.Id(entity.getDomainId()),
                ArticleType.valueOf(entity.getArticleType()),
                toAlbumReference(entity),
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
     * ArticleTagLinkTableRecordのリストからArticleTagのリストへ変換
     *
     * @param links
     *            ArticleTagLinkTableRecordのリスト
     * @return ArticleTagのリスト（linksがnullの場合は空リスト）
     */
    public static List<ArticleTag> toTags(@Nullable List<ArticleTagLinkTableRecord> links) {
        return Optional.ofNullable(links)
                .map(toList(link -> toTag(link.getArticleTag())))
                .orElseGet(List::of);
    }

    /**
     * ArticleTagTableRecordからArticleTagへ変換
     *
     * @param entity
     *            ArticleTagTableRecord
     * @return ArticleTag
     */
    public static ArticleTag toTag(ArticleTagTableRecord entity) {
        return ArticleTag.reconstruct(
                ArticleTag.Id.of(entity.getDomainId()),
                entity.getName());
    }

    /**
     * ArticleTagからArticleTagTableRecordへ変換（新規タグの永続化用。articleTagLinkとの関連付けは呼び出し側の責務）
     *
     * @param tag
     *            ArticleTag
     * @return ArticleTagTableRecord
     */
    public static ArticleTagTableRecord toTagEntity(ArticleTag tag) {
        return new ArticleTagTableRecord()
                .setDomainId(tag.id().value())
                .setName(tag.getName());
    }

    private static AlbumReference toAlbumReference(ArticleTableRecord entity) {
        return lostAlbumReference(entity)
                .orElseGet(
                        () -> AlbumReference.of(
                                Optional.ofNullable(entity.getAlbumId())
                                        .map(Album.Id::new)
                                        .orElse(null)));
    }

    private static Optional<AlbumReference> lostAlbumReference(ArticleTableRecord entity) {
        return Optional.ofNullable(entity.getFormerAlbumId())
                .map(Album.Id::new)
                .flatMap(formerId -> toLost(entity, formerId));
    }

    private static Optional<AlbumReference> toLost(ArticleTableRecord entity, Album.Id formerAlbumId) {
        return Optional.ofNullable(entity.getAlbumReferenceLostAt())
                .map(BusinessDateTime::of)
                .map(
                        lostAt -> new AlbumReference.Lost(
                                formerAlbumId,
                                lostAt,
                                AlbumReferenceLostReason.valueOf(
                                        Objects.requireNonNull(entity.getAlbumReferenceLostReason()))));
    }

    private static @Nullable BusinessDateTime toBusinessDateTime(@Nullable Instant instant) {
        return Optional.ofNullable(instant)
                .map(BusinessDateTime::of)
                .orElse(null);
    }

    private static @Nullable MarkupContent createMarkupContent(@Nullable String body, @Nullable String bodyFormat) {
        return Optional.ofNullable(body)
                .map(b -> new MarkupContent(b, MarkupFormat.orDefault(bodyFormat)))
                .orElse(null);
    }

    /**
     * DomainモデルからEntityへ変換
     *
     * @param article
     *            Article
     * @return ArticleTableRecord
     */
    public static ArticleTableRecord toEntity(Article article) {
        final var body = article.body();
        final var reference = albumReferenceOf(article);
        return new ArticleTableRecord()
                .setDomainId(article.id().value())
                .setArticleType(article.articleType().name())
                .setAlbumId(
                        reference.flatMap(AlbumReference::activeAlbumId)
                                .map(Album.Id::value)
                                .orElse(null))
                .setFormerAlbumId(
                        reference.flatMap(AlbumReference::lost)
                                .map(lost -> lost.formerAlbumId().value())
                                .orElse(null))
                .setAlbumReferenceLostAt(
                        reference.flatMap(AlbumReference::lost)
                                .map(lost -> lost.lostAt().value())
                                .orElse(null))
                .setAlbumReferenceLostReason(
                        reference.flatMap(AlbumReference::lost)
                                .map(lost -> lost.reason().name())
                                .orElse(null))
                .setTitle(article.title().value())
                .setBody(
                        Optional.ofNullable(body)
                                .map(MarkupContent::content)
                                .orElse(null))
                .setBodyFormat(
                        Optional.ofNullable(body)
                                .map(b -> b.format().name())
                                .orElse(MarkupFormat.PLAIN_TEXT.name()))
                .setIntroShort(article.introShort())
                .setPublishedAt(toInstant(article.publishedAt()))
                .setUpdatedAtBusiness(toInstant(article.updatedAtBusiness()))
                .setIsPublic(article.publicFlag());
    }

    /*
     * NARROWING: アルバム参照を持てるのは AlbumArticle だけで、他の種別は参照の列を持たない（すべてnullで保存する）。
     */
    private static Optional<AlbumReference> albumReferenceOf(Article article) {
        return AlbumArticle.from(article)
                .map(AlbumArticle::albumReference);
    }

    private static @Nullable Instant toInstant(@Nullable BusinessDateTime businessDateTime) {
        return Optional.ofNullable(businessDateTime)
                .map(BusinessDateTime::value)
                .orElse(null);
    }
}
