package com.abservice.infrastructure.persistence.repository;

import com.abservice.domain.model.aggregate.artistcredit.ArtistCredit;
import com.abservice.domain.model.vo.common.ArtistCreditName;
import com.abservice.domain.repository.artistcredit.ArtistCreditRepository;
import com.abservice.infrastructure.persistence.datasource.ArtistCreditDataSource;
import com.abservice.infrastructure.persistence.mapper.ArtistCreditMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ArtistCreditRepository実装
 *
 * <p>
 * Panacheを使用した非同期リポジトリ実装。
 * </p>
 */
@ApplicationScoped
public class ArtistCreditRepositoryImpl implements ArtistCreditRepository {

    private final ArtistCreditDataSource dataSource;

    public ArtistCreditRepositoryImpl(ArtistCreditDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Uni<ArtistCredit> save(ArtistCredit aggregate) {
        if (aggregate == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("ArtistCredit cannot be null"));
        }

        var entity = ArtistCreditMapper.toEntity(aggregate);

        return dataSource.existsByArtistCreditId(entity.getArtistCreditId())
                .flatMap(exists -> {
                    if (exists) {
                        return dataSource.findById(entity.getArtistCreditId())
                                .flatMap(existingEntity -> {
                                    existingEntity.setDisplayName(entity.getDisplayName());
                                    existingEntity.setSortKey(entity.getSortKey());
                                    return dataSource.persistAndFlush(existingEntity);
                                });
                    } else {
                        return dataSource.persistAndFlush(entity);
                    }
                })
                .map(ArtistCreditMapper::toDomain);
    }

    @Override
    public Uni<List<ArtistCredit>> saveAll(Iterable<ArtistCredit> aggregates) {
        if (aggregates == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Aggregates cannot be null"));
        }

        var unis = java.util.stream.StreamSupport.stream(aggregates.spliterator(), false)
                .map(this::save)
                .collect(Collectors.toList());

        return Uni.join().all(unis).andFailFast();
    }

    @Override
    public Uni<ArtistCredit> findById(ArtistCredit.Id id) {
        if (id == null) {
            return Uni.createFrom().nullItem();
        }

        return dataSource.findById(id.value())
                .map(ArtistCreditMapper::toDomain);
    }

    @Override
    public Uni<List<ArtistCredit>> findAllById(Iterable<ArtistCredit.Id> ids) {
        if (ids == null) {
            return Uni.createFrom().item(List.of());
        }

        var unis = java.util.stream.StreamSupport.stream(ids.spliterator(), false)
                .map(this::findById)
                .collect(Collectors.toList());

        return Uni.join().all(unis).andFailFast()
                .map(list -> list.stream()
                        .filter(artistCredit -> artistCredit != null)
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<List<ArtistCredit>> findAll() {
        return dataSource.listAll()
                .map(entities -> entities.stream()
                        .map(ArtistCreditMapper::toDomain)
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<Void> delete(ArtistCredit aggregate) {
        if (aggregate == null) {
            return Uni.createFrom().voidItem();
        }
        return deleteById(aggregate.id());
    }

    @Override
    public Uni<Void> deleteAll(Iterable<ArtistCredit> aggregates) {
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
    public Uni<Void> deleteById(ArtistCredit.Id id) {
        if (id == null) {
            return Uni.createFrom().voidItem();
        }

        return dataSource.deleteByArtistCreditId(id.value())
                .replaceWithVoid();
    }

    @Override
    public Uni<Void> deleteAllById(Iterable<ArtistCredit.Id> ids) {
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
    public Uni<Boolean> existsById(ArtistCredit.Id id) {
        if (id == null) {
            return Uni.createFrom().item(false);
        }

        return dataSource.existsByArtistCreditId(id.value());
    }

    @Override
    public Uni<Long> count() {
        return dataSource.count();
    }

    // カスタムメソッド

    @Override
    public Uni<ArtistCredit> findByDisplayName(ArtistCreditName displayName) {
        if (displayName == null) {
            return Uni.createFrom().nullItem();
        }

        return dataSource.findByDisplayName(displayName.value())
                .map(ArtistCreditMapper::toDomain);
    }

    @Override
    public Uni<List<ArtistCredit>> findByDisplayNameContaining(String nameKeyword) {
        if (nameKeyword == null) {
            return Uni.createFrom().item(List.of());
        }

        return dataSource.findByDisplayNameContaining(nameKeyword)
                .map(entities -> entities.stream()
                        .map(ArtistCreditMapper::toDomain)
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<List<ArtistCredit>> findBySortKey(String sortKey) {
        if (sortKey == null) {
            return Uni.createFrom().item(List.of());
        }

        return dataSource.findBySortKey(sortKey)
                .map(entities -> entities.stream()
                        .map(ArtistCreditMapper::toDomain)
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<List<ArtistCredit>> findAllOrderBySortKey() {
        return dataSource.findAllOrderBySortKey()
                .map(entities -> entities.stream()
                        .map(ArtistCreditMapper::toDomain)
                        .collect(Collectors.toList()));
    }
}
