package com.abservice.infrastructure.persistence.repository;

import static com.abservice.lib.Iterables.toList;
import static java.util.function.Predicate.not;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.entity.article.ArticleTag;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.infrastructure.persistence.datasource.ArticleDataSource;
import com.abservice.infrastructure.persistence.datasource.ArticleTagDataSource;
import com.abservice.infrastructure.persistence.entity.ArticleTableRecord;
import com.abservice.infrastructure.persistence.entity.ArticleTagTableRecord;
import com.abservice.infrastructure.persistence.entity.ArticleTagLinkTableRecord;
import com.abservice.infrastructure.persistence.entity.ArticleTagLinkId;
import com.abservice.infrastructure.persistence.mapper.ArticleMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * ArticleRepository実装
 *
 * <p>
 * Panacheを使用した非同期リポジトリ実装。
 * </p>
 */
@ApplicationScoped
public class ArticleRepositoryImpl implements ArticleRepository {

    private final ArticleDataSource dataSource;
    private final ArticleTagDataSource tagDataSource;

    public ArticleRepositoryImpl(ArticleDataSource dataSource, ArticleTagDataSource tagDataSource) {
        this.dataSource = dataSource;
        this.tagDataSource = tagDataSource;
    }

    @Override
    public Uni<Article> save(Article aggregate) {
        return Optional.ofNullable(aggregate)
                .map(
                        a -> dataSource.findByDomainId(ArticleMapper.toEntity(a).getDomainId())
                                .flatMap(existingEntity -> upsertArticle(existingEntity, a))
                                .map(ArticleMapper::toDomain))
                .orElseGet(() -> Uni.createFrom().failure(new IllegalArgumentException("Article cannot be null")));
    }

    private Uni<ArticleTableRecord> upsertArticle(@Nullable ArticleTableRecord existingEntity, Article aggregate) {
        final var newValues = ArticleMapper.toEntity(aggregate);
        // 記事自体を先に確定（新規時はIDENTITY採番を解決）してからタグリンクを付与する。
        // 同一flush内で行うと、複合@MapsId（ArticleTagLinkTableRecord）の親IDが未解決のまま
        // カスケードされ IdentifierGenerationException となるため2段階に分ける。
        return dataSource.persistAndFlush(
                Optional.ofNullable(existingEntity)
                        .map(existing -> copyArticleScalarFields(existing, newValues))
                        .orElse(newValues))
                .flatMap(
                        saved -> ensureTagEntities(aggregate.getTags())
                                .map(tagEntities -> {
                                    reconcileTagLinks(
                                            saved,
                                            tagEntities,
                                            aggregate.getTags());
                                    return saved;
                                })
                                .flatMap(dataSource::persistAndFlush));
    }

    private static ArticleTableRecord copyArticleScalarFields(ArticleTableRecord target, ArticleTableRecord source) {
        target.setArticleType(source.getArticleType());
        target.setAlbumId(source.getAlbumId());
        target.setTitle(source.getTitle());
        target.setBody(source.getBody());
        target.setBodyFormat(source.getBodyFormat());
        target.setIntroShort(source.getIntroShort());
        target.setPublishedAt(source.getPublishedAt());
        target.setUpdatedAtBusiness(source.getUpdatedAtBusiness());
        target.setIsPublic(source.getIsPublic());
        return target;
    }

    private Uni<List<ArticleTagTableRecord>> ensureTagEntities(List<ArticleTag> tags) {
        return tagDataSource.findByDomainIds(
                tags.stream()
                        .map(t -> t.id().value())
                        .collect(Collectors.toSet()))
                .flatMap(existingTagEntities -> persistMissingTags(tags, existingTagEntities));
    }

    private Uni<List<ArticleTagTableRecord>> persistMissingTags(
            List<ArticleTag> tags,
            List<ArticleTagTableRecord> existingTagEntities) {
        final var existingByDomainId = existingTagEntities.stream()
                .collect(Collectors.toMap(ArticleTagTableRecord::getDomainId, Function.identity()));
        tags.forEach(
                t -> Optional.ofNullable(existingByDomainId.get(t.id().value()))
                        .ifPresent(entity -> entity.setName(t.getName())));
        final var newTagEntities = tags.stream()
                .filter(not(t -> existingByDomainId.containsKey(t.id().value())))
                .map(ArticleMapper::toTagEntity)
                .toList();
        return tagDataSource.persistAll(newTagEntities)
                .replaceWith(Stream.concat(existingTagEntities.stream(), newTagEntities.stream()).toList());
    }

    private static void reconcileTagLinks(
            ArticleTableRecord article,
            List<ArticleTagTableRecord> allTagEntities,
            List<ArticleTag> desiredTags) {
        final var desiredIds = desiredTags.stream()
                .map(t -> t.id().value())
                .collect(Collectors.toSet());

        article.getArticleTagLinks().removeIf(not(link -> desiredIds.contains(link.getArticleTag().getDomainId())));

        final var linkedIds = article.getArticleTagLinks().stream()
                .map(link -> link.getArticleTag().getDomainId())
                .collect(Collectors.toSet());
        final var tagEntityByDomainId = allTagEntities.stream()
                .collect(Collectors.toMap(ArticleTagTableRecord::getDomainId, Function.identity()));

        desiredTags.stream()
                .filter(not(t -> linkedIds.contains(t.id().value())))
                .forEach(t -> {
                    final var tagEntity = Objects.requireNonNull(tagEntityByDomainId.get(t.id().value()));
                    final var link = new ArticleTagLinkTableRecord();
                    link.setId(new ArticleTagLinkId(article.getArticleId(), tagEntity.getArticleTagId()));
                    link.setArticle(article);
                    link.setArticleTag(tagEntity);
                    article.getArticleTagLinks().add(link);
                });
    }

    @Override
    public Uni<List<Article>> saveAll(Iterable<Article> aggregates) {
        return Multi.createFrom().iterable(aggregates)
                .onItem().transformToUniAndConcatenate(this::save)
                .collect().asList();
    }

    @Override
    public Uni<Article> findById(Article.Id id) {
        return Optional.ofNullable(id)
                .map(List::of)
                .map(this::findAllById)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(articles -> articles.stream().findFirst().orElse(null));
    }

    @Override
    public Uni<List<Article>> findAllById(Iterable<Article.Id> ids) {
        return Optional.ofNullable(ids)
                .map(toList(Article.Id::value))
                .map(dataSource::findByIds)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(toList(ArticleMapper::toDomain));
    }

    @Override
    public Uni<List<Article>> findAll() {
        return dataSource.findAllEager()
                .map(toList(ArticleMapper::toDomain));
    }

    @Override
    public Uni<Void> delete(Article aggregate) {
        return Optional.ofNullable(aggregate)
                .map(a -> deleteById(a.id()))
                .orElseGet(() -> Uni.createFrom().voidItem());
    }

    @Override
    public Uni<Void> deleteAll(Iterable<Article> aggregates) {
        return Optional.ofNullable(aggregates)
                .map(toList(Article::id))
                .map(this::deleteAllById)
                .orElseGet(() -> Uni.createFrom().voidItem());
    }

    @Override
    public Uni<Void> deleteById(Article.Id id) {
        return Optional.ofNullable(id)
                .map(i -> dataSource.deleteByArticleId(i.value()).replaceWithVoid())
                .orElseGet(() -> Uni.createFrom().voidItem());
    }

    @Override
    public Uni<Void> deleteAllById(Iterable<Article.Id> ids) {
        return Optional.ofNullable(ids)
                .map(toList(Article.Id::value))
                .map(dataSource::deleteByArticleIds)
                .orElseGet(() -> Uni.createFrom().voidItem());
    }

    @Override
    public Uni<Boolean> existsById(Article.Id id) {
        return Optional.ofNullable(id)
                .map(i -> dataSource.existsByArticleId(i.value()))
                .orElseGet(() -> Uni.createFrom().item(false));
    }

    @Override
    public Uni<Long> count() {
        return dataSource.count();
    }

    // カスタムメソッド

    @Override
    public Uni<List<Article>> findByArticleType(ArticleType articleType) {
        return Optional.ofNullable(articleType)
                .map(ArticleType::name)
                .map(dataSource::findByArticleType)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(toList(ArticleMapper::toDomain));
    }

    @Override
    public Uni<Article> findByAlbumId(Album.Id albumId) {
        return Optional.ofNullable(albumId)
                .map(Album.Id::value)
                .map(dataSource::findByAlbumId)
                .orElseGet(() -> Uni.createFrom().nullItem())
                .onItem().ifNotNull().transform(ArticleMapper::toDomain);
    }

    @Override
    public Uni<List<Article>> findByPublicFlag(boolean publicFlag) {
        return dataSource.findByPublicFlag(publicFlag)
                .map(toList(ArticleMapper::toDomain));
    }

    @Override
    public Uni<List<Article>> findByPublishedAtBetween(BusinessDateTime startDate, BusinessDateTime endDate) {
        return Stream.of(startDate, endDate)
                .anyMatch(Objects::isNull)
                        ? Uni.createFrom().item(List.of())
                        : dataSource.findByPublishedAtBetween(startDate.value(), endDate.value())
                                .map(toList(ArticleMapper::toDomain));
    }

    @Override
    public Uni<List<Article>> findByTitleContaining(String titleKeyword) {
        return Optional.ofNullable(titleKeyword)
                .map(dataSource::findByTitleContaining)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(toList(ArticleMapper::toDomain));
    }
}
