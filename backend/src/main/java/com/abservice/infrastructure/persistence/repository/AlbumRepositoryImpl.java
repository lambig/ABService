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
import java.util.stream.Collectors;

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
        if (aggregate == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Album cannot be null"));
        }

        var entity = AlbumMapper.toEntity(aggregate);

        // 既存確認
        return dataSource.existsByAlbumId(entity.getDomainId()).flatMap(exists -> {
            if (exists) {
                // 更新の場合は既存のエンティティをマージ
                return dataSource.findByIdWithTracks(entity.getDomainId()).flatMap(existingEntity -> {
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
                    if (entity.getTracks() != null) {
                        entity.getTracks().forEach(track -> {
                            track.setAlbum(existingEntity);
                            existingEntity.getTracks().add(track);
                        });
                    }

                    return dataSource.persistAndFlush(existingEntity);
                });
            } else {
                // 新規作成
                return dataSource.persistAlbumWithRelations(entity);
            }
        }).map(AlbumMapper::toDomain);
    }

    @Override
    public Uni<List<Album>> saveAll(Iterable<Album> aggregates) {
        if (aggregates == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Aggregates cannot be null"));
        }

        var unis = java.util.stream.StreamSupport.stream(aggregates.spliterator(), false).map(this::save)
                .collect(Collectors.toList());

        return Uni.join().all(unis).andFailFast();
    }

    @Override
    public Uni<Album> findById(Album.Id id) {
        if (id == null) {
            return Uni.createFrom().nullItem();
        }

        return dataSource.findByIdWithTracks(id.value()).map(AlbumMapper::toDomain);
    }

    @Override
    public Uni<List<Album>> findAllById(Iterable<Album.Id> ids) {
        if (ids == null) {
            return Uni.createFrom().item(List.of());
        }

        var unis = java.util.stream.StreamSupport.stream(ids.spliterator(), false).map(this::findById)
                .collect(Collectors.toList());

        return Uni.join().all(unis).andFailFast()
                .map(list -> list.stream().filter(album -> album != null).collect(Collectors.toList()));
    }

    @Override
    public Uni<List<Album>> findAll() {
        return dataSource.listAll()
                .map(entities -> entities.stream().map(AlbumMapper::toDomain).collect(Collectors.toList()));
    }

    @Override
    public Uni<Void> delete(Album aggregate) {
        if (aggregate == null) {
            return Uni.createFrom().voidItem();
        }
        return deleteById(aggregate.id());
    }

    @Override
    public Uni<Void> deleteAll(Iterable<Album> aggregates) {
        if (aggregates == null) {
            return Uni.createFrom().voidItem();
        }

        var unis = java.util.stream.StreamSupport.stream(aggregates.spliterator(), false).map(this::delete)
                .collect(Collectors.toList());

        return Uni.join().all(unis).andFailFast().replaceWithVoid();
    }

    @Override
    public Uni<Void> deleteById(Album.Id id) {
        if (id == null) {
            return Uni.createFrom().voidItem();
        }

        return dataSource.deleteByAlbumId(id.value()).replaceWithVoid();
    }

    @Override
    public Uni<Void> deleteAllById(Iterable<Album.Id> ids) {
        if (ids == null) {
            return Uni.createFrom().voidItem();
        }

        var unis = java.util.stream.StreamSupport.stream(ids.spliterator(), false).map(this::deleteById)
                .collect(Collectors.toList());

        return Uni.join().all(unis).andFailFast().replaceWithVoid();
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
    public Uni<List<Album>> findByTitle(AlbumTitle title) {
        if (title == null) {
            return Uni.createFrom().item(List.of());
        }

        return dataSource.findByTitle(title.value())
                .map(entities -> entities.stream().map(AlbumMapper::toDomain).collect(Collectors.toList()));
    }

    @Override
    public Uni<List<Album>> findByArtistName(String artistName) {
        if (artistName == null || artistName.isBlank()) {
            return Uni.createFrom().item(List.of());
        }

        return dataSource.findByArtistDisplayName(artistName)
                .map(entities -> entities.stream().map(AlbumMapper::toDomain).collect(Collectors.toList()));
    }

    @Override
    public Uni<List<Album>> findByEventName(String eventName) {
        if (eventName == null || eventName.isBlank()) {
            return Uni.createFrom().item(List.of());
        }

        return dataSource.findByEventName(eventName)
                .map(entities -> entities.stream().map(AlbumMapper::toDomain).collect(Collectors.toList()));
    }

    @Override
    public Uni<Album> findByCatalogNumber(CatalogNumber catalogNumber) {
        if (catalogNumber == null) {
            return Uni.createFrom().nullItem();
        }

        return dataSource.findByCatalogNumber(catalogNumber.value()).map(AlbumMapper::toDomain);
    }

    @Override
    public Uni<List<Album>> findByReleaseYear(int year) {
        return dataSource.findByReleaseYear(year)
                .map(entities -> entities.stream().map(AlbumMapper::toDomain).collect(Collectors.toList()));
    }
}
