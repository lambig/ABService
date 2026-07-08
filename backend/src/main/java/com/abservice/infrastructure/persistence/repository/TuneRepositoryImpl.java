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
import java.util.stream.Collectors;
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
        if (aggregate == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Tune cannot be null"));
        }

        final var entity = TuneMapper.toEntity(aggregate);

        return dataSource.existsByTuneId(entity.getDomainId()).flatMap(exists -> {
            if (exists) {
                return dataSource.find("domainId", entity.getDomainId()).firstResult().flatMap(existingEntity -> {
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
                });
            } else {
                return dataSource.persistAndFlush(entity);
            }
        }).map(TuneMapper::toDomain);
    }

    @Override
    public Uni<List<Tune>> saveAll(Iterable<Tune> aggregates) {
        if (aggregates == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Aggregates cannot be null"));
        }

        final var unis = StreamSupport.stream(aggregates.spliterator(), false).map(this::save)
                .collect(Collectors.toList());

        return Uni.join().all(unis).andFailFast();
    }

    @Override
    public Uni<Tune> findById(Tune.Id id) {
        if (id == null) {
            return Uni.createFrom().nullItem();
        }

        return dataSource.find("domainId", id.value()).firstResult().map(TuneMapper::toDomain);
    }

    @Override
    public Uni<List<Tune>> findAllById(Iterable<Tune.Id> ids) {
        if (ids == null) {
            return Uni.createFrom().item(List.of());
        }

        final var unis = StreamSupport.stream(ids.spliterator(), false).map(this::findById)
                .collect(Collectors.toList());

        return Uni.join().all(unis).andFailFast()
                .map(list -> list.stream().filter(tune -> tune != null).collect(Collectors.toList()));
    }

    @Override
    public Uni<List<Tune>> findAll() {
        return dataSource.listAll()
                .map(entities -> entities.stream().map(TuneMapper::toDomain).collect(Collectors.toList()));
    }

    @Override
    public Uni<Void> delete(Tune aggregate) {
        if (aggregate == null) {
            return Uni.createFrom().voidItem();
        }
        return deleteById(aggregate.id());
    }

    @Override
    public Uni<Void> deleteAll(Iterable<Tune> aggregates) {
        if (aggregates == null) {
            return Uni.createFrom().voidItem();
        }

        final var unis = StreamSupport.stream(aggregates.spliterator(), false).map(this::delete)
                .collect(Collectors.toList());

        return Uni.join().all(unis).andFailFast().replaceWithVoid();
    }

    @Override
    public Uni<Void> deleteById(Tune.Id id) {
        if (id == null) {
            return Uni.createFrom().voidItem();
        }

        return dataSource.deleteByTuneId(id.value()).replaceWithVoid();
    }

    @Override
    public Uni<Void> deleteAllById(Iterable<Tune.Id> ids) {
        if (ids == null) {
            return Uni.createFrom().voidItem();
        }

        final var unis = StreamSupport.stream(ids.spliterator(), false).map(this::deleteById)
                .collect(Collectors.toList());

        return Uni.join().all(unis).andFailFast().replaceWithVoid();
    }

    @Override
    public Uni<Boolean> existsById(Tune.Id id) {
        if (id == null) {
            return Uni.createFrom().item(false);
        }

        return dataSource.existsByTuneId(id.value());
    }

    @Override
    public Uni<Long> count() {
        return dataSource.count();
    }

    // カスタムメソッド

    @Override
    public Uni<List<Tune>> findByTitle(TuneTitle title) {
        if (title == null) {
            return Uni.createFrom().item(List.of());
        }

        return dataSource.findByTitle(title.value())
                .map(entities -> entities.stream().map(TuneMapper::toDomain).collect(Collectors.toList()));
    }

    @Override
    public Uni<List<Tune>> findByTuneKind(TuneKind tuneKind) {
        if (tuneKind == null) {
            return Uni.createFrom().item(List.of());
        }

        return dataSource.findByTuneKind(tuneKind.name())
                .map(entities -> entities.stream().map(TuneMapper::toDomain).collect(Collectors.toList()));
    }

    @Override
    public Uni<List<Tune>> findByTuneType(String tuneType) {
        if (tuneType == null) {
            return Uni.createFrom().item(List.of());
        }

        return dataSource.findByTuneType(tuneType)
                .map(entities -> entities.stream().map(TuneMapper::toDomain).collect(Collectors.toList()));
    }

    @Override
    public Uni<List<Tune>> findByDefaultKey(String defaultKey) {
        if (defaultKey == null) {
            return Uni.createFrom().item(List.of());
        }

        return dataSource.findByDefaultKey(defaultKey)
                .map(entities -> entities.stream().map(TuneMapper::toDomain).collect(Collectors.toList()));
    }
}
