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
import java.util.Optional;
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
        return Optional.ofNullable(aggregate)
                .map(a -> {
                    final var entity = AlbumArticleMapper.toEntity(a);

                    return dataSource.existsByAlbumId(entity.getDomainId()).flatMap(
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
                })
                .orElseGet(() -> Uni.createFrom().failure(new IllegalArgumentException("AlbumArticle cannot be null")));
    }

    @Override
    public Uni<List<AlbumArticle>> saveAll(Iterable<AlbumArticle> aggregates) {
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
    public Uni<AlbumArticle> findById(Album.Id id) {
        return Optional.ofNullable(id)
                .map(i -> dataSource.find("domainId", i.value()).firstResult().map(AlbumArticleMapper::toDomain))
                .orElseGet(() -> Uni.createFrom().nullItem());
    }

    @Override
    public Uni<List<AlbumArticle>> findAllById(Iterable<Album.Id> ids) {
        return Optional.ofNullable(ids)
                .map(
                        i -> Uni.join()
                                .all(
                                        StreamSupport.stream(i.spliterator(), false)
                                                .map(this::findById)
                                                .toList())
                                .andFailFast()
                                .map(
                                        list -> list.stream().filter(albumArticle -> albumArticle != null)
                                                .toList()))
                .orElseGet(() -> Uni.createFrom().item(List.of()));
    }

    @Override
    public Uni<List<AlbumArticle>> findAll() {
        return dataSource.listAll()
                .map(entities -> entities.stream().map(AlbumArticleMapper::toDomain).toList());
    }

    @Override
    public Uni<Void> delete(AlbumArticle aggregate) {
        return Optional.ofNullable(aggregate)
                .map(a -> deleteById(a.albumId()))
                .orElseGet(() -> Uni.createFrom().voidItem());
    }

    @Override
    public Uni<Void> deleteAll(Iterable<AlbumArticle> aggregates) {
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
    public Uni<Void> deleteById(Album.Id id) {
        return Optional.ofNullable(id)
                .map(i -> dataSource.deleteByAlbumId(i.value()).replaceWithVoid())
                .orElseGet(() -> Uni.createFrom().voidItem());
    }

    @Override
    public Uni<Void> deleteAllById(Iterable<Album.Id> ids) {
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
    public Uni<Boolean> existsById(Album.Id id) {
        return Optional.ofNullable(id)
                .map(i -> dataSource.existsByAlbumId(i.value()))
                .orElseGet(() -> Uni.createFrom().item(false));
    }

    @Override
    public Uni<Long> count() {
        return dataSource.count();
    }

    // カスタムメソッド

    @Override
    public Uni<AlbumArticle> findByAlbumId(Album.Id albumId) {
        return Optional.ofNullable(albumId)
                .map(
                        a -> dataSource.findByAlbumId(a.value())
                                .map(AlbumArticleMapper::toDomain))
                .orElseGet(() -> Uni.createFrom().nullItem());
    }

    @Override
    public Uni<List<AlbumArticle>> findByLabelTag(LabelTag labelTag) {
        return Optional.ofNullable(labelTag)
                .map(
                        t -> dataSource.findByLabelTag(t.name())
                                .map(entities -> entities.stream().map(AlbumArticleMapper::toDomain).toList()))
                .orElseGet(() -> Uni.createFrom().item(List.of()));
    }

    @Override
    public Uni<List<AlbumArticle>> findByFirstEventSpaceContaining(String spaceKeyword) {
        return Optional.ofNullable(spaceKeyword)
                .map(
                        k -> dataSource.findByFirstEventSpaceContaining(k)
                                .map(entities -> entities.stream().map(AlbumArticleMapper::toDomain).toList()))
                .orElseGet(() -> Uni.createFrom().item(List.of()));
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
