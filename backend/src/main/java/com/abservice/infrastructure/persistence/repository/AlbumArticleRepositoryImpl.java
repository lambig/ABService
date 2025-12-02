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
import java.util.stream.Collectors;

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
        if (aggregate == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("AlbumArticle cannot be null"));
        }

        var entity = AlbumArticleMapper.toEntity(aggregate);

        return dataSource.existsByAlbumId(entity.getAlbumId())
                .flatMap(exists -> {
                    if (exists) {
                        return dataSource.findById(entity.getAlbumId())
                                .flatMap(existingEntity -> {
                                    existingEntity.setIntroLong(entity.getIntroLong());
                                    existingEntity.setIntroShort(entity.getIntroShort());
                                    existingEntity.setFirstEventSpace(entity.getFirstEventSpace());
                                    existingEntity.setLabelTag(entity.getLabelTag());
                                    return dataSource.persistAndFlush(existingEntity);
                                });
                    } else {
                        return dataSource.persistAndFlush(entity);
                    }
                })
                .map(AlbumArticleMapper::toDomain);
    }

    @Override
    public Uni<List<AlbumArticle>> saveAll(Iterable<AlbumArticle> aggregates) {
        if (aggregates == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Aggregates cannot be null"));
        }

        var unis = java.util.stream.StreamSupport.stream(aggregates.spliterator(), false)
                .map(this::save)
                .collect(Collectors.toList());

        return Uni.join().all(unis).andFailFast();
    }

    @Override
    public Uni<AlbumArticle> findById(Album.Id id) {
        if (id == null) {
            return Uni.createFrom().nullItem();
        }

        return dataSource.findById(id.value())
                .map(AlbumArticleMapper::toDomain);
    }

    @Override
    public Uni<List<AlbumArticle>> findAllById(Iterable<Album.Id> ids) {
        if (ids == null) {
            return Uni.createFrom().item(List.of());
        }

        var unis = java.util.stream.StreamSupport.stream(ids.spliterator(), false)
                .map(this::findById)
                .collect(Collectors.toList());

        return Uni.join().all(unis).andFailFast()
                .map(list -> list.stream()
                        .filter(albumArticle -> albumArticle != null)
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<List<AlbumArticle>> findAll() {
        return dataSource.listAll()
                .map(entities -> entities.stream()
                        .map(AlbumArticleMapper::toDomain)
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<Void> delete(AlbumArticle aggregate) {
        if (aggregate == null) {
            return Uni.createFrom().voidItem();
        }
        return deleteById(aggregate.albumId());
    }

    @Override
    public Uni<Void> deleteAll(Iterable<AlbumArticle> aggregates) {
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
    public Uni<Void> deleteById(Album.Id id) {
        if (id == null) {
            return Uni.createFrom().voidItem();
        }

        return dataSource.deleteByAlbumId(id.value())
                .replaceWithVoid();
    }

    @Override
    public Uni<Void> deleteAllById(Iterable<Album.Id> ids) {
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
    public Uni<Boolean> existsById(Album.Id id) {
        if (id == null) {
            return Uni.createFrom().item(false);
        }

        return dataSource.existsByAlbumId(id.value());
    }

    @Override
    public Uni<Long> count() {
        return dataSource.count();
    }

    // カスタムメソッド

    @Override
    public Uni<AlbumArticle> findByAlbumId(Album.Id albumId) {
        if (albumId == null) {
            return Uni.createFrom().nullItem();
        }

        return dataSource.findByAlbumId(albumId.value())
                .map(AlbumArticleMapper::toDomain);
    }

    @Override
    public Uni<List<AlbumArticle>> findByLabelTag(LabelTag labelTag) {
        if (labelTag == null) {
            return Uni.createFrom().item(List.of());
        }

        return dataSource.findByLabelTag(labelTag.value())
                .map(entities -> entities.stream()
                        .map(AlbumArticleMapper::toDomain)
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<List<AlbumArticle>> findByFirstEventSpaceContaining(String spaceKeyword) {
        if (spaceKeyword == null) {
            return Uni.createFrom().item(List.of());
        }

        return dataSource.findByFirstEventSpaceContaining(spaceKeyword)
                .map(entities -> entities.stream()
                        .map(AlbumArticleMapper::toDomain)
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<List<AlbumArticle>> findWithDistribution() {
        return dataSource.findWithDistribution()
                .map(entities -> entities.stream()
                        .map(AlbumArticleMapper::toDomain)
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<List<AlbumArticle>> findWithAcquisitionChannels() {
        return dataSource.findWithAcquisitionChannels()
                .map(entities -> entities.stream()
                        .map(AlbumArticleMapper::toDomain)
                        .collect(Collectors.toList()));
    }
}
