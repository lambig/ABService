package com.abservice.infrastructure.persistence.repository;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.CatalogNumber;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.infrastructure.persistence.datasource.AlbumDataSource;
import com.abservice.infrastructure.persistence.mapper.AlbumMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
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
        return Optional.ofNullable(aggregate)
                .map(a -> {
                    final var entity = AlbumMapper.toEntity(a);

                    // 既存確認
                    return dataSource.existsByAlbumId(entity.getDomainId())
                            // 更新の場合は既存のエンティティをマージ、そうでなければ新規作成
                            .flatMap(
                                    exists -> exists
                                            ? dataSource.findByIdWithTracks(entity.getDomainId())
                                                    .flatMap(existingEntity -> {
                                                        // エンティティの更新
                                                        existingEntity.setTitle(entity.getTitle());
                                                        existingEntity.setReleaseDate(entity.getReleaseDate());
                                                        existingEntity
                                                                .setArtistDisplayName(entity.getArtistDisplayName());
                                                        existingEntity.setArtistSortKey(entity.getArtistSortKey());
                                                        existingEntity.setEventName(entity.getEventName());
                                                        existingEntity.setEventDate(entity.getEventDate());
                                                        existingEntity.setEventPlace(entity.getEventPlace());
                                                        existingEntity.setEventNote(entity.getEventNote());
                                                        existingEntity.setCatalogNumber(entity.getCatalogNumber());

                                                        // トラックを更新
                                                        existingEntity.getTracks().clear();
                                                        Optional.ofNullable(entity.getTracks())
                                                                .ifPresent(tracks -> tracks.forEach(track -> {
                                                                    track.setAlbum(existingEntity);
                                                                    existingEntity.getTracks().add(track);
                                                                }));

                                                        return dataSource.persistAndFlush(existingEntity);
                                                    })
                                            : dataSource.persistAlbumWithRelations(entity))
                            .map(AlbumMapper::toDomain);
                })
                .orElseGet(() -> Uni.createFrom().failure(new IllegalArgumentException("Album cannot be null")));
    }

    @Override
    public Uni<List<Album>> saveAll(Iterable<Album> aggregates) {
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
    public Uni<Album> findById(Album.Id id) {
        return Optional.ofNullable(id)
                .map(
                        i -> dataSource.findByIdWithTracks(i.value())
                                .map(AlbumMapper::toDomain))
                .orElseGet(() -> Uni.createFrom().nullItem());
    }

    @Override
    public Uni<List<Album>> findAllById(Iterable<Album.Id> ids) {
        return Optional.ofNullable(ids)
                .map(
                        i -> Uni.join()
                                .all(
                                        StreamSupport.stream(i.spliterator(), false)
                                                .map(this::findById)
                                                .toList())
                                .andFailFast()
                                .map(list -> list.stream().filter(album -> album != null).toList()))
                .orElseGet(() -> Uni.createFrom().item(List.of()));
    }

    @Override
    public Uni<List<Album>> findAll() {
        return dataSource.listAll()
                .map(entities -> entities.stream().map(AlbumMapper::toDomain).toList());
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
    public Uni<List<Album>> findByTitle(AlbumTitle title) {
        return Optional.ofNullable(title)
                .map(
                        t -> dataSource.findByTitle(t.value())
                                .map(entities -> entities.stream().map(AlbumMapper::toDomain).toList()))
                .orElseGet(() -> Uni.createFrom().item(List.of()));
    }

    @Override
    public Uni<List<Album>> findByArtistName(String artistName) {
        return Optional.ofNullable(artistName)
                .filter(StringUtils::isNotBlank)
                .map(dataSource::findByArtistDisplayName)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(entities -> entities.stream().map(AlbumMapper::toDomain).toList());
    }

    @Override
    public Uni<List<Album>> findByEventName(String eventName) {
        return Optional.ofNullable(eventName)
                .filter(StringUtils::isNotBlank)
                .map(dataSource::findByEventName)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(entities -> entities.stream().map(AlbumMapper::toDomain).toList());
    }

    @Override
    public Uni<Album> findByCatalogNumber(CatalogNumber catalogNumber) {
        return Optional.ofNullable(catalogNumber)
                .map(
                        c -> dataSource.findByCatalogNumber(c.value())
                                .map(AlbumMapper::toDomain))
                .orElseGet(() -> Uni.createFrom().nullItem());
    }

    @Override
    public Uni<List<Album>> findByReleaseYear(int year) {
        return dataSource.findByReleaseYear(year)
                .map(entities -> entities.stream().map(AlbumMapper::toDomain).toList());
    }
}
