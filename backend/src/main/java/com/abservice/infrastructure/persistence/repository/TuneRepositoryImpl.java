package com.abservice.infrastructure.persistence.repository;

import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.model.vo.tune.TuneKind;
import com.abservice.domain.model.vo.tune.TuneTitle;
import com.abservice.domain.repository.tune.TuneRepository;
import com.abservice.infrastructure.persistence.datasource.TuneDataSource;
import com.abservice.infrastructure.persistence.mapper.TuneMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.stream.StreamSupport;

/**
 * TuneRepository実装
 *
 * <p>
 * Panacheを使用した非同期リポジトリ実装。
 * </p>
 */
@ApplicationScoped
public class TuneRepositoryImpl implements TuneRepository {

    private final TuneDataSource dataSource;

    public TuneRepositoryImpl(TuneDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Uni<Tune> save(Tune aggregate) {
        return switch (aggregate) {
            case null -> Uni.createFrom().failure(new IllegalArgumentException("Tune cannot be null"));
            default -> {
                final var entity = TuneMapper.toEntity(aggregate);

                yield dataSource.existsByTuneId(entity.getDomainId()).flatMap(
                        exists -> exists
                                ? dataSource.find("domainId", entity.getDomainId()).firstResult()
                                        .flatMap(existingEntity -> {
                                            existingEntity.setTitle(entity.getTitle());
                                            existingEntity.setTuneKind(entity.getTuneKind());
                                            existingEntity.setDefaultComposerCredit(entity.getDefaultComposerCredit());
                                            existingEntity.setDefaultArrangerCredit(entity.getDefaultArrangerCredit());
                                            existingEntity.setOriginalWorkTitle(entity.getOriginalWorkTitle());
                                            existingEntity.setOriginalWorkCredit(entity.getOriginalWorkCredit());
                                            existingEntity.setTuneType(entity.getTuneType());
                                            existingEntity.setDefaultKey(entity.getDefaultKey());
                                            existingEntity.setDefaultTempo(entity.getDefaultTempo());
                                            return dataSource.persistAndFlush(existingEntity);
                                        })
                                : dataSource.persistAndFlush(entity))
                        .map(TuneMapper::toDomain);
            }
        };
    }

    @Override
    public Uni<List<Tune>> saveAll(Iterable<Tune> aggregates) {
        return switch (aggregates) {
            case null -> Uni.createFrom().failure(new IllegalArgumentException("Aggregates cannot be null"));
            default -> Uni.join()
                    .all(
                            StreamSupport.stream(aggregates.spliterator(), false)
                                    .map(this::save)
                                    .toList())
                    .andFailFast();
        };
    }

    @Override
    public Uni<Tune> findById(Tune.Id id) {
        return switch (id) {
            case null -> Uni.createFrom().nullItem();
            default -> dataSource.find("domainId", id.value()).firstResult().map(TuneMapper::toDomain);
        };
    }

    @Override
    public Uni<List<Tune>> findAllById(Iterable<Tune.Id> ids) {
        return switch (ids) {
            case null -> Uni.createFrom().item(List.of());
            default -> Uni.join()
                    .all(
                            StreamSupport.stream(ids.spliterator(), false)
                                    .map(this::findById)
                                    .toList())
                    .andFailFast()
                    .map(list -> list.stream().filter(tune -> tune != null).toList());
        };
    }

    @Override
    public Uni<List<Tune>> findAll() {
        return dataSource.listAll()
                .map(entities -> entities.stream().map(TuneMapper::toDomain).toList());
    }

    @Override
    public Uni<Void> delete(Tune aggregate) {
        return switch (aggregate) {
            case null -> Uni.createFrom().voidItem();
            default -> deleteById(aggregate.id());
        };
    }

    @Override
    public Uni<Void> deleteAll(Iterable<Tune> aggregates) {
        return switch (aggregates) {
            case null -> Uni.createFrom().voidItem();
            default -> Uni.join()
                    .all(
                            StreamSupport.stream(aggregates.spliterator(), false)
                                    .map(this::delete)
                                    .toList())
                    .andFailFast().replaceWithVoid();
        };
    }

    @Override
    public Uni<Void> deleteById(Tune.Id id) {
        return switch (id) {
            case null -> Uni.createFrom().voidItem();
            default -> dataSource.deleteByTuneId(id.value()).replaceWithVoid();
        };
    }

    @Override
    public Uni<Void> deleteAllById(Iterable<Tune.Id> ids) {
        return switch (ids) {
            case null -> Uni.createFrom().voidItem();
            default -> Uni.join()
                    .all(
                            StreamSupport.stream(ids.spliterator(), false)
                                    .map(this::deleteById)
                                    .toList())
                    .andFailFast().replaceWithVoid();
        };
    }

    @Override
    public Uni<Boolean> existsById(Tune.Id id) {
        return switch (id) {
            case null -> Uni.createFrom().item(false);
            default -> dataSource.existsByTuneId(id.value());
        };
    }

    @Override
    public Uni<Long> count() {
        return dataSource.count();
    }

    // カスタムメソッド

    @Override
    public Uni<List<Tune>> findByTitle(TuneTitle title) {
        return switch (title) {
            case null -> Uni.createFrom().item(List.of());
            default -> dataSource.findByTitle(title.value())
                    .map(entities -> entities.stream().map(TuneMapper::toDomain).toList());
        };
    }

    @Override
    public Uni<List<Tune>> findByTuneKind(TuneKind tuneKind) {
        return switch (tuneKind) {
            case null -> Uni.createFrom().item(List.of());
            default -> dataSource.findByTuneKind(tuneKind.name())
                    .map(entities -> entities.stream().map(TuneMapper::toDomain).toList());
        };
    }

    @Override
    public Uni<List<Tune>> findByTuneType(String tuneType) {
        return switch (tuneType) {
            case null -> Uni.createFrom().item(List.of());
            default -> dataSource.findByTuneType(tuneType)
                    .map(entities -> entities.stream().map(TuneMapper::toDomain).toList());
        };
    }

    @Override
    public Uni<List<Tune>> findByDefaultKey(String defaultKey) {
        return switch (defaultKey) {
            case null -> Uni.createFrom().item(List.of());
            default -> dataSource.findByDefaultKey(defaultKey)
                    .map(entities -> entities.stream().map(TuneMapper::toDomain).toList());
        };
    }
}
