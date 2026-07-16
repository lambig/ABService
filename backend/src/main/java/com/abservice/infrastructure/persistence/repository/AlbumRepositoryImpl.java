package com.abservice.infrastructure.persistence.repository;

import static com.abservice.lib.Iterables.toList;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.CatalogNumber;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.infrastructure.persistence.datasource.AlbumDataSource;
import com.abservice.infrastructure.persistence.mapper.AlbumMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * AlbumRepository実装
 *
 * <p>
 * Panacheを使用した非同期リポジトリ実装。
 * </p>
 */
@ApplicationScoped
public class AlbumRepositoryImpl implements AlbumRepository {

    private final AlbumDataSource dataSource;

    public AlbumRepositoryImpl(AlbumDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Uni<Album> save(Album aggregate) {
        final var entity = AlbumMapper.toEntity(aggregate);
        // 存在すれば更新、なければ新規作成
        return dataSource.findByIdWithTracks(entity.getDomainId())
                .onItem().ifNotNull().transformToUni(
                        existingEntity -> dataSource.persistAndFlush(
                                existingEntity
                                        .setTitle(entity.getTitle())
                                        .setReleaseDate(entity.getReleaseDate())
                                        .setArtistDisplayName(entity.getArtistDisplayName())
                                        .setArtistSortKey(entity.getArtistSortKey())
                                        .setEventName(entity.getEventName())
                                        .setEventDate(entity.getEventDate())
                                        .setEventPlace(entity.getEventPlace())
                                        .setEventNote(entity.getEventNote())
                                        .setCatalogNumber(entity.getCatalogNumber())
                                        .replaceTracks(entity.getTracks())))
                .onItem().ifNull().switchTo(() -> dataSource.persistAlbumWithRelations(entity))
                .map(AlbumMapper::toDomain);
    }

    @Override
    public Uni<List<Album>> saveAll(Iterable<Album> aggregates) {
        return Multi.createFrom().iterable(aggregates)
                .onItem().transformToUniAndConcatenate(this::save)
                .collect().asList();
    }

    @Override
    public Uni<Album> findById(Album.Id id) {
        return Optional.ofNullable(id)
                .map(List::of)
                .map(this::findAllById)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(albums -> albums.stream().findFirst().orElse(null));
    }

    @Override
    public Uni<List<Album>> findAllById(Iterable<Album.Id> ids) {
        return Optional.ofNullable(ids)
                .map(toList(Album.Id::value))
                .map(dataSource::findByIdsWithTracks)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(toList(AlbumMapper::toDomain));
    }

    @Override
    public Uni<List<Album>> findAll() {
        return dataSource.listAll()
                .map(toList(AlbumMapper::toDomain));
    }

    @Override
    public Uni<Void> delete(Album aggregate) {
        return Optional.ofNullable(aggregate)
                .map(a -> deleteById(a.id()))
                .orElseGet(() -> Uni.createFrom().voidItem());
    }

    @Override
    public Uni<Void> deleteAll(Iterable<Album> aggregates) {
        return Optional.ofNullable(aggregates)
                .map(
                        a -> Multi.createFrom().iterable(a)
                                .onItem().call(this::delete)
                                .collect().asList().replaceWithVoid())
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
                        i -> Multi.createFrom().iterable(i)
                                .onItem().call(this::deleteById)
                                .collect().asList().replaceWithVoid())
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
    public Uni<List<Album>> findByTitle(AlbumTitle title) {
        return Optional.ofNullable(title)
                .map(AlbumTitle::value)
                .map(dataSource::findByTitle)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(toList(AlbumMapper::toDomain));
    }

    @Override
    public Uni<List<Album>> findByArtistName(String artistName) {
        return Optional.ofNullable(artistName)
                .filter(StringUtils::isNotBlank)
                .map(dataSource::findByArtistDisplayName)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(toList(AlbumMapper::toDomain));
    }

    @Override
    public Uni<List<Album>> findByEventName(String eventName) {
        return Optional.ofNullable(eventName)
                .filter(StringUtils::isNotBlank)
                .map(dataSource::findByEventName)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(toList(AlbumMapper::toDomain));
    }

    @Override
    public Uni<Album> findByCatalogNumber(CatalogNumber catalogNumber) {
        return Optional.ofNullable(catalogNumber)
                .map(CatalogNumber::value)
                .map(dataSource::findByCatalogNumber)
                .orElseGet(() -> Uni.createFrom().nullItem())
                .onItem().ifNotNull().transform(AlbumMapper::toDomain);
    }

    @Override
    public Uni<List<Album>> findByReleaseYear(int year) {
        return dataSource.findByReleaseYear(year)
                .map(toList(AlbumMapper::toDomain));
    }
}
