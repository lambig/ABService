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
        return switch (aggregate) {
            case null -> Uni.createFrom().failure(new IllegalArgumentException("Album cannot be null"));
            default -> {
                final var entity = AlbumMapper.toEntity(aggregate);

                // 既存確認
                yield dataSource.existsByAlbumId(entity.getDomainId())
                        // 更新の場合は既存のエンティティをマージ、そうでなければ新規作成
                        .flatMap(
                                exists -> exists
                                        ? dataSource.findByIdWithTracks(entity.getDomainId())
                                                .flatMap(existingEntity -> {
                                                    // エンティティの更新
                                                    existingEntity.setTitle(entity.getTitle());
                                                    existingEntity.setReleaseDate(entity.getReleaseDate());
                                                    existingEntity.setArtistDisplayName(entity.getArtistDisplayName());
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
            }
        };
    }

    @Override
    public Uni<List<Album>> saveAll(Iterable<Album> aggregates) {
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
    public Uni<Album> findById(Album.Id id) {
        return switch (id) {
            case null -> Uni.createFrom().nullItem();
            default -> dataSource.findByIdWithTracks(id.value()).map(AlbumMapper::toDomain);
        };
    }

    @Override
    public Uni<List<Album>> findAllById(Iterable<Album.Id> ids) {
        return switch (ids) {
            case null -> Uni.createFrom().item(List.of());
            default -> Uni.join()
                    .all(
                            StreamSupport.stream(ids.spliterator(), false).map(this::findById)
                                    .toList())
                    .andFailFast()
                    .map(list -> list.stream().filter(album -> album != null).toList());
        };
    }

    @Override
    public Uni<List<Album>> findAll() {
        return dataSource.listAll()
                .map(entities -> entities.stream().map(AlbumMapper::toDomain).toList());
    }

    @Override
    public Uni<Void> delete(Album aggregate) {
        return switch (aggregate) {
            case null -> Uni.createFrom().voidItem();
            default -> deleteById(aggregate.id());
        };
    }

    @Override
    public Uni<Void> deleteAll(Iterable<Album> aggregates) {
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
    public Uni<List<Album>> findByTitle(AlbumTitle title) {
        return switch (title) {
            case null -> Uni.createFrom().item(List.of());
            default -> dataSource.findByTitle(title.value())
                    .map(entities -> entities.stream().map(AlbumMapper::toDomain).toList());
        };
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
        return switch (catalogNumber) {
            case null -> Uni.createFrom().nullItem();
            default -> dataSource.findByCatalogNumber(catalogNumber.value()).map(AlbumMapper::toDomain);
        };
    }

    @Override
    public Uni<List<Album>> findByReleaseYear(int year) {
        return dataSource.findByReleaseYear(year)
                .map(entities -> entities.stream().map(AlbumMapper::toDomain).toList());
    }
}
