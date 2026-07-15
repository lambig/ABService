package com.abservice.infrastructure.persistence.repository;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.albumarticle.AlbumArticle;
import com.abservice.domain.model.vo.album.LabelTag;
import com.abservice.domain.repository.albumarticle.AlbumArticleRepository;
import com.abservice.infrastructure.persistence.datasource.AlbumArticleDataSource;
import com.abservice.infrastructure.persistence.mapper.AlbumArticleMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.stream.StreamSupport;

/**
 * AlbumArticleRepository実装
 *
 * <p>
 * Panacheを使用した非同期リポジトリ実装。
 * </p>
 */
@ApplicationScoped
public class AlbumArticleRepositoryImpl implements AlbumArticleRepository {

    private final AlbumArticleDataSource dataSource;

    public AlbumArticleRepositoryImpl(AlbumArticleDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Uni<AlbumArticle> save(AlbumArticle aggregate) {
        return switch (aggregate) {
            case null -> Uni.createFrom().failure(new IllegalArgumentException("AlbumArticle cannot be null"));
            default -> {
                final var entity = AlbumArticleMapper.toEntity(aggregate);

                yield dataSource.existsByAlbumId(entity.getDomainId()).flatMap(
                        exists -> exists
                                ? dataSource.find("domainId", entity.getDomainId()).firstResult()
                                        .flatMap(existingEntity -> {
                                            existingEntity.setIntroLong(entity.getIntroLong());
                                            existingEntity.setIntroShort(entity.getIntroShort());
                                            existingEntity.setFirstEventSpace(entity.getFirstEventSpace());
                                            existingEntity.setLabelTag(entity.getLabelTag());
                                            return dataSource.persistAndFlush(existingEntity);
                                        })
                                : dataSource.persistAndFlush(entity))
                        .map(AlbumArticleMapper::toDomain);
            }
        };
    }

    @Override
    public Uni<List<AlbumArticle>> saveAll(Iterable<AlbumArticle> aggregates) {
        return switch (aggregates) {
            case null -> Uni.createFrom().failure(new IllegalArgumentException("Aggregates cannot be null"));
            default -> Uni.join()
                    .all(
                            StreamSupport.stream(aggregates.spliterator(), false).map(this::save)
                                    .toList())
                    .andFailFast();
        };
    }

    @Override
    public Uni<AlbumArticle> findById(Album.Id id) {
        return switch (id) {
            case null -> Uni.createFrom().nullItem();
            default -> dataSource.find("domainId", id.value()).firstResult().map(AlbumArticleMapper::toDomain);
        };
    }

    @Override
    public Uni<List<AlbumArticle>> findAllById(Iterable<Album.Id> ids) {
        return switch (ids) {
            case null -> Uni.createFrom().item(List.of());
            default -> Uni.join()
                    .all(
                            StreamSupport.stream(ids.spliterator(), false).map(this::findById)
                                    .toList())
                    .andFailFast()
                    .map(
                            list -> list.stream().filter(albumArticle -> albumArticle != null)
                                    .toList());
        };
    }

    @Override
    public Uni<List<AlbumArticle>> findAll() {
        return dataSource.listAll()
                .map(entities -> entities.stream().map(AlbumArticleMapper::toDomain).toList());
    }

    @Override
    public Uni<Void> delete(AlbumArticle aggregate) {
        return switch (aggregate) {
            case null -> Uni.createFrom().voidItem();
            default -> deleteById(aggregate.albumId());
        };
    }

    @Override
    public Uni<Void> deleteAll(Iterable<AlbumArticle> aggregates) {
        return switch (aggregates) {
            case null -> Uni.createFrom().voidItem();
            default -> Uni.join()
                    .all(
                            StreamSupport.stream(aggregates.spliterator(), false).map(this::delete)
                                    .toList())
                    .andFailFast().replaceWithVoid();
        };
    }

    @Override
    public Uni<Void> deleteById(Album.Id id) {
        return switch (id) {
            case null -> Uni.createFrom().voidItem();
            default -> dataSource.deleteByAlbumId(id.value()).replaceWithVoid();
        };
    }

    @Override
    public Uni<Void> deleteAllById(Iterable<Album.Id> ids) {
        return switch (ids) {
            case null -> Uni.createFrom().voidItem();
            default -> Uni.join()
                    .all(
                            StreamSupport.stream(ids.spliterator(), false).map(this::deleteById)
                                    .toList())
                    .andFailFast().replaceWithVoid();
        };
    }

    @Override
    public Uni<Boolean> existsById(Album.Id id) {
        return switch (id) {
            case null -> Uni.createFrom().item(false);
            default -> dataSource.existsByAlbumId(id.value());
        };
    }

    @Override
    public Uni<Long> count() {
        return dataSource.count();
    }

    // カスタムメソッド

    @Override
    public Uni<AlbumArticle> findByAlbumId(Album.Id albumId) {
        return switch (albumId) {
            case null -> Uni.createFrom().nullItem();
            default -> dataSource.findByAlbumId(albumId.value()).map(AlbumArticleMapper::toDomain);
        };
    }

    @Override
    public Uni<List<AlbumArticle>> findByLabelTag(LabelTag labelTag) {
        return switch (labelTag) {
            case null -> Uni.createFrom().item(List.of());
            default -> dataSource.findByLabelTag(labelTag.name())
                    .map(entities -> entities.stream().map(AlbumArticleMapper::toDomain).toList());
        };
    }

    @Override
    public Uni<List<AlbumArticle>> findByFirstEventSpaceContaining(String spaceKeyword) {
        return switch (spaceKeyword) {
            case null -> Uni.createFrom().item(List.of());
            default -> dataSource.findByFirstEventSpaceContaining(spaceKeyword)
                    .map(entities -> entities.stream().map(AlbumArticleMapper::toDomain).toList());
        };
    }

    @Override
    public Uni<List<AlbumArticle>> findWithDistribution() {
        return dataSource.findWithDistribution()
                .map(entities -> entities.stream().map(AlbumArticleMapper::toDomain).toList());
    }

    @Override
    public Uni<List<AlbumArticle>> findWithAcquisitionChannels() {
        return dataSource.findWithAcquisitionChannels()
                .map(entities -> entities.stream().map(AlbumArticleMapper::toDomain).toList());
    }
}
