package com.abservice.infrastructure.persistence.repository;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.infrastructure.persistence.datasource.ArticleDataSource;
import com.abservice.infrastructure.persistence.mapper.ArticleMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

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
        if (aggregate == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Article cannot be null"));
        }

        var entity = ArticleMapper.toEntity(aggregate);

        return dataSource.existsByArticleId(entity.getArticleId())
                .flatMap(exists -> {
                    if (exists) {
                        return dataSource.findById(entity.getArticleId())
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
                                });
                    } else {
                        return dataSource.persistAndFlush(entity);
                    }
                })
                .map(ArticleMapper::toDomain);
    }

    @Override
    public Uni<List<Article>> saveAll(Iterable<Article> aggregates) {
        if (aggregates == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Aggregates cannot be null"));
        }

        var unis = java.util.stream.StreamSupport.stream(aggregates.spliterator(), false)
                .map(this::save)
                .collect(Collectors.toList());

        return Uni.join().all(unis).andFailFast();
    }

    @Override
    public Uni<Article> findById(Article.Id id) {
        if (id == null) {
            return Uni.createFrom().nullItem();
        }

        return dataSource.findById(id.value())
                .map(ArticleMapper::toDomain);
    }

    @Override
    public Uni<List<Article>> findAllById(Iterable<Article.Id> ids) {
        if (ids == null) {
            return Uni.createFrom().item(List.of());
        }

        var unis = java.util.stream.StreamSupport.stream(ids.spliterator(), false)
                .map(this::findById)
                .collect(Collectors.toList());

        return Uni.join().all(unis).andFailFast()
                .map(list -> list.stream()
                        .filter(article -> article != null)
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<List<Article>> findAll() {
        return dataSource.listAll()
                .map(entities -> entities.stream()
                        .map(ArticleMapper::toDomain)
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<Void> delete(Article aggregate) {
        if (aggregate == null) {
            return Uni.createFrom().voidItem();
        }
        return deleteById(aggregate.id());
    }

    @Override
    public Uni<Void> deleteAll(Iterable<Article> aggregates) {
        if (aggregates == null) {
            return Uni.createFrom().voidItem();
        }

        var unis = java.util.stream.StreamSupport.stream(aggregates.spliterator(), false)
                .map(this::delete)
                .collect(Collectors.toList());

        return Uni.join().all(unis).andFailFast()
                .replaceWithVoid();
    }

    @Override
    public Uni<Void> deleteById(Article.Id id) {
        if (id == null) {
            return Uni.createFrom().voidItem();
        }

        return dataSource.deleteByArticleId(id.value())
                .replaceWithVoid();
    }

    @Override
    public Uni<Void> deleteAllById(Iterable<Article.Id> ids) {
        if (ids == null) {
            return Uni.createFrom().voidItem();
        }

        var unis = java.util.stream.StreamSupport.stream(ids.spliterator(), false)
                .map(this::deleteById)
                .collect(Collectors.toList());

        return Uni.join().all(unis).andFailFast()
                .replaceWithVoid();
    }

    @Override
    public Uni<Boolean> existsById(Article.Id id) {
        if (id == null) {
            return Uni.createFrom().item(false);
        }

        return dataSource.existsByArticleId(id.value());
    }

    @Override
    public Uni<Long> count() {
        return dataSource.count();
    }

    // カスタムメソッド

    @Override
    public Uni<List<Article>> findByArticleType(ArticleType articleType) {
        if (articleType == null) {
            return Uni.createFrom().item(List.of());
        }

        return dataSource.findByArticleType(articleType.name())
                .map(entities -> entities.stream()
                        .map(ArticleMapper::toDomain)
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<Article> findByAlbumId(Album.Id albumId) {
        if (albumId == null) {
            return Uni.createFrom().nullItem();
        }

        return dataSource.findByAlbumId(albumId.value())
                .map(ArticleMapper::toDomain);
    }

    @Override
    public Uni<List<Article>> findByPublicFlag(boolean publicFlag) {
        return dataSource.findByPublicFlag(publicFlag)
                .map(entities -> entities.stream()
                        .map(ArticleMapper::toDomain)
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<List<Article>> findByPublishedAtBetween(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            return Uni.createFrom().item(List.of());
        }

        return dataSource.findByPublishedAtBetween(
                startDate.toInstant(ZoneOffset.UTC),
                endDate.toInstant(ZoneOffset.UTC))
                .map(entities -> entities.stream()
                        .map(ArticleMapper::toDomain)
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<List<Article>> findByTitleContaining(String titleKeyword) {
        if (titleKeyword == null) {
            return Uni.createFrom().item(List.of());
        }

        return dataSource.findByTitleContaining(titleKeyword)
                .map(entities -> entities.stream()
                        .map(ArticleMapper::toDomain)
                        .collect(Collectors.toList()));
    }
}
