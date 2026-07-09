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
import java.util.stream.Collectors;
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
        return switch (aggregate) {
            case null -> Uni.createFrom().failure(new IllegalArgumentException("Article cannot be null"));
            default -> {
                final var entity = ArticleMapper.toEntity(aggregate);

                yield dataSource.existsByArticleId(entity.getDomainId()).flatMap(exists -> exists
                        ? dataSource.find("domainId", entity.getDomainId()).firstResult().flatMap(existingEntity -> {
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
                        : dataSource.persistAndFlush(entity)).map(ArticleMapper::toDomain);
            }
        };
    }

    @Override
    public Uni<List<Article>> saveAll(Iterable<Article> aggregates) {
        return switch (aggregates) {
            case null -> Uni.createFrom().failure(new IllegalArgumentException("Aggregates cannot be null"));
            default -> {
                final var unis = StreamSupport.stream(aggregates.spliterator(), false).map(this::save)
                        .collect(Collectors.toList());

                yield Uni.join().all(unis).andFailFast();
            }
        };
    }

    @Override
    public Uni<Article> findById(Article.Id id) {
        return switch (id) {
            case null -> Uni.createFrom().nullItem();
            default -> dataSource.find("domainId", id.value()).firstResult().map(ArticleMapper::toDomain);
        };
    }

    @Override
    public Uni<List<Article>> findAllById(Iterable<Article.Id> ids) {
        return switch (ids) {
            case null -> Uni.createFrom().item(List.of());
            default -> {
                final var unis = StreamSupport.stream(ids.spliterator(), false).map(this::findById)
                        .collect(Collectors.toList());

                yield Uni.join().all(unis).andFailFast()
                        .map(list -> list.stream().filter(article -> article != null).collect(Collectors.toList()));
            }
        };
    }

    @Override
    public Uni<List<Article>> findAll() {
        return dataSource.listAll()
                .map(entities -> entities.stream().map(ArticleMapper::toDomain).collect(Collectors.toList()));
    }

    @Override
    public Uni<Void> delete(Article aggregate) {
        return switch (aggregate) {
            case null -> Uni.createFrom().voidItem();
            default -> deleteById(aggregate.id());
        };
    }

    @Override
    public Uni<Void> deleteAll(Iterable<Article> aggregates) {
        return switch (aggregates) {
            case null -> Uni.createFrom().voidItem();
            default -> {
                final var unis = StreamSupport.stream(aggregates.spliterator(), false).map(this::delete)
                        .collect(Collectors.toList());

                yield Uni.join().all(unis).andFailFast().replaceWithVoid();
            }
        };
    }

    @Override
    public Uni<Void> deleteById(Article.Id id) {
        return switch (id) {
            case null -> Uni.createFrom().voidItem();
            default -> dataSource.deleteByArticleId(id.value()).replaceWithVoid();
        };
    }

    @Override
    public Uni<Void> deleteAllById(Iterable<Article.Id> ids) {
        return switch (ids) {
            case null -> Uni.createFrom().voidItem();
            default -> {
                final var unis = StreamSupport.stream(ids.spliterator(), false).map(this::deleteById)
                        .collect(Collectors.toList());

                yield Uni.join().all(unis).andFailFast().replaceWithVoid();
            }
        };
    }

    @Override
    public Uni<Boolean> existsById(Article.Id id) {
        return switch (id) {
            case null -> Uni.createFrom().item(false);
            default -> dataSource.existsByArticleId(id.value());
        };
    }

    @Override
    public Uni<Long> count() {
        return dataSource.count();
    }

    // カスタムメソッド

    @Override
    public Uni<List<Article>> findByArticleType(ArticleType articleType) {
        return switch (articleType) {
            case null -> Uni.createFrom().item(List.of());
            default -> dataSource.findByArticleType(articleType.name())
                    .map(entities -> entities.stream().map(ArticleMapper::toDomain).collect(Collectors.toList()));
        };
    }

    @Override
    public Uni<Article> findByAlbumId(Album.Id albumId) {
        return switch (albumId) {
            case null -> Uni.createFrom().nullItem();
            default -> dataSource.findByAlbumId(albumId.value()).map(ArticleMapper::toDomain);
        };
    }

    @Override
    public Uni<List<Article>> findByPublicFlag(boolean publicFlag) {
        return dataSource.findByPublicFlag(publicFlag)
                .map(entities -> entities.stream().map(ArticleMapper::toDomain).collect(Collectors.toList()));
    }

    @Override
    public Uni<List<Article>> findByPublishedAtBetween(BusinessDateTime startDate, BusinessDateTime endDate) {
        return Stream.of(startDate, endDate).anyMatch(Objects::isNull)
                ? Uni.createFrom().item(List.of())
                : dataSource.findByPublishedAtBetween(startDate.value(), endDate.value())
                        .map(entities -> entities.stream().map(ArticleMapper::toDomain).collect(Collectors.toList()));
    }

    @Override
    public Uni<List<Article>> findByTitleContaining(String titleKeyword) {
        return switch (titleKeyword) {
            case null -> Uni.createFrom().item(List.of());
            default -> dataSource.findByTitleContaining(titleKeyword)
                    .map(entities -> entities.stream().map(ArticleMapper::toDomain).collect(Collectors.toList()));
        };
    }
}
