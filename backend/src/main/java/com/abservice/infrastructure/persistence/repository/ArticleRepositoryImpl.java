package com.abservice.infrastructure.persistence.repository;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.infrastructure.persistence.datasource.ArticleDataSource;
import com.abservice.infrastructure.persistence.mapper.ArticleMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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

    public ArticleRepositoryImpl(ArticleDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Uni<Article> save(Article aggregate) {
        return Optional.ofNullable(aggregate)
                .map(a -> {
                    final var entity = ArticleMapper.toEntity(a);

                    return dataSource.existsByArticleId(entity.getDomainId()).flatMap(
                            exists -> exists
                                    ? dataSource.find("domainId", entity.getDomainId()).firstResult()
                                            .flatMap(existingEntity -> {
                                                existingEntity.setArticleType(entity.getArticleType());
                                                existingEntity.setAlbumId(entity.getAlbumId());
                                                existingEntity.setTitle(entity.getTitle());
                                                existingEntity.setBody(entity.getBody());
                                                existingEntity.setIntroShort(entity.getIntroShort());
                                                existingEntity.setPublishedAt(entity.getPublishedAt());
                                                existingEntity.setUpdatedAtBusiness(entity.getUpdatedAtBusiness());
                                                existingEntity.setIsPublic(entity.getIsPublic());
                                                return dataSource.persistAndFlush(existingEntity);
                                            })
                                    : dataSource.persistAndFlush(entity))
                            .map(ArticleMapper::toDomain);
                })
                .orElseGet(() -> Uni.createFrom().failure(new IllegalArgumentException("Article cannot be null")));
    }

    @Override
    public Uni<List<Article>> saveAll(Iterable<Article> aggregates) {
        return Optional.ofNullable(aggregates)
                .map(
                        a -> Uni.join()
                                .all(
                                        StreamSupport.stream(a.spliterator(), false)
                                                .map(this::save)
                                                .toList())
                                .andFailFast())
                .orElseGet(() -> Uni.createFrom().failure(new IllegalArgumentException("Aggregates cannot be null")));
    }

    @Override
    public Uni<Article> findById(Article.Id id) {
        return Optional.ofNullable(id)
                .map(i -> dataSource.find("domainId", i.value()).firstResult().map(ArticleMapper::toDomain))
                .orElseGet(() -> Uni.createFrom().nullItem());
    }

    @Override
    public Uni<List<Article>> findAllById(Iterable<Article.Id> ids) {
        return Optional.ofNullable(ids)
                .map(
                        i -> Uni.join()
                                .all(
                                        StreamSupport.stream(i.spliterator(), false)
                                                .map(this::findById)
                                                .toList())
                                .andFailFast()
                                .map(list -> list.stream().filter(article -> article != null).toList()))
                .orElseGet(() -> Uni.createFrom().item(List.of()));
    }

    @Override
    public Uni<List<Article>> findAll() {
        return dataSource.listAll()
                .map(entities -> entities.stream().map(ArticleMapper::toDomain).toList());
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
                .map(
                        a -> Uni.join()
                                .all(
                                        StreamSupport.stream(a.spliterator(), false)
                                                .map(this::delete)
                                                .toList())
                                .andFailFast().replaceWithVoid())
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
                .map(
                        i -> Uni.join()
                                .all(
                                        StreamSupport.stream(i.spliterator(), false)
                                                .map(this::deleteById)
                                                .toList())
                                .andFailFast().replaceWithVoid())
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
                .map(
                        t -> dataSource.findByArticleType(t.name())
                                .map(entities -> entities.stream().map(ArticleMapper::toDomain).toList()))
                .orElseGet(() -> Uni.createFrom().item(List.of()));
    }

    @Override
    public Uni<Article> findByAlbumId(Album.Id albumId) {
        return Optional.ofNullable(albumId)
                .map(
                        a -> dataSource.findByAlbumId(a.value())
                                .map(ArticleMapper::toDomain))
                .orElseGet(() -> Uni.createFrom().nullItem());
    }

    @Override
    public Uni<List<Article>> findByPublicFlag(boolean publicFlag) {
        return dataSource.findByPublicFlag(publicFlag)
                .map(entities -> entities.stream().map(ArticleMapper::toDomain).toList());
    }

    @Override
    public Uni<List<Article>> findByPublishedAtBetween(BusinessDateTime startDate, BusinessDateTime endDate) {
        return Stream.of(startDate, endDate)
                .anyMatch(Objects::isNull)
                        ? Uni.createFrom().item(List.of())
                        : dataSource.findByPublishedAtBetween(startDate.value(), endDate.value())
                                .map(entities -> entities.stream().map(ArticleMapper::toDomain).toList());
    }

    @Override
    public Uni<List<Article>> findByTitleContaining(String titleKeyword) {
        return Optional.ofNullable(titleKeyword)
                .map(
                        k -> dataSource.findByTitleContaining(k)
                                .map(entities -> entities.stream().map(ArticleMapper::toDomain).toList()))
                .orElseGet(() -> Uni.createFrom().item(List.of()));
    }
}
