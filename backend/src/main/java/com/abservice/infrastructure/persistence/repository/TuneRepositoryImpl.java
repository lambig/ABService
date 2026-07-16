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
import java.util.Optional;
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
        return Optional.ofNullable(aggregate)
                .map(a -> {
                    final var entity = TuneMapper.toEntity(a);

                    return dataSource.existsByTuneId(entity.getDomainId()).flatMap(
                            exists -> exists
                                    ? dataSource.find("domainId", entity.getDomainId()).firstResult()
                                            .flatMap(existingEntity -> {
                                                existingEntity.setTitle(entity.getTitle());
                                                existingEntity.setTuneKind(entity.getTuneKind());
                                                existingEntity
                                                        .setDefaultComposerCredit(entity.getDefaultComposerCredit());
                                                existingEntity
                                                        .setDefaultArrangerCredit(entity.getDefaultArrangerCredit());
                                                existingEntity.setOriginalWorkTitle(entity.getOriginalWorkTitle());
                                                existingEntity.setOriginalWorkCredit(entity.getOriginalWorkCredit());
                                                existingEntity.setTuneType(entity.getTuneType());
                                                existingEntity.setDefaultKey(entity.getDefaultKey());
                                                existingEntity.setDefaultTempo(entity.getDefaultTempo());
                                                return dataSource.persistAndFlush(existingEntity);
                                            })
                                    : dataSource.persistAndFlush(entity))
                            .map(TuneMapper::toDomain);
                })
                .orElseGet(() -> Uni.createFrom().failure(new IllegalArgumentException("Tune cannot be null")));
    }

    @Override
    public Uni<List<Tune>> saveAll(Iterable<Tune> aggregates) {
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
    public Uni<Tune> findById(Tune.Id id) {
        return Optional.ofNullable(id)
                .map(i -> dataSource.find("domainId", i.value()).firstResult().map(TuneMapper::toDomain))
                .orElseGet(() -> Uni.createFrom().nullItem());
    }

    @Override
    public Uni<List<Tune>> findAllById(Iterable<Tune.Id> ids) {
        return Optional.ofNullable(ids)
                .map(
                        i -> Uni.join()
                                .all(
                                        StreamSupport.stream(i.spliterator(), false)
                                                .map(this::findById)
                                                .toList())
                                .andFailFast()
                                .map(list -> list.stream().filter(tune -> tune != null).toList()))
                .orElseGet(() -> Uni.createFrom().item(List.of()));
    }

    @Override
    public Uni<List<Tune>> findAll() {
        return dataSource.listAll()
                .map(entities -> entities.stream().map(TuneMapper::toDomain).toList());
    }

    @Override
    public Uni<Void> delete(Tune aggregate) {
        return Optional.ofNullable(aggregate)
                .map(a -> deleteById(a.id()))
                .orElseGet(() -> Uni.createFrom().voidItem());
    }

    @Override
    public Uni<Void> deleteAll(Iterable<Tune> aggregates) {
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
    public Uni<Void> deleteById(Tune.Id id) {
        return Optional.ofNullable(id)
                .map(i -> dataSource.deleteByTuneId(i.value()).replaceWithVoid())
                .orElseGet(() -> Uni.createFrom().voidItem());
    }

    @Override
    public Uni<Void> deleteAllById(Iterable<Tune.Id> ids) {
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
    public Uni<Boolean> existsById(Tune.Id id) {
        return Optional.ofNullable(id)
                .map(i -> dataSource.existsByTuneId(i.value()))
                .orElseGet(() -> Uni.createFrom().item(false));
    }

    @Override
    public Uni<Long> count() {
        return dataSource.count();
    }

    // カスタムメソッド

    @Override
    public Uni<List<Tune>> findByTitle(TuneTitle title) {
        return Optional.ofNullable(title)
                .map(
                        t -> dataSource.findByTitle(t.value())
                                .map(entities -> entities.stream().map(TuneMapper::toDomain).toList()))
                .orElseGet(() -> Uni.createFrom().item(List.of()));
    }

    @Override
    public Uni<List<Tune>> findByTuneKind(TuneKind tuneKind) {
        return Optional.ofNullable(tuneKind)
                .map(
                        k -> dataSource.findByTuneKind(k.name())
                                .map(entities -> entities.stream().map(TuneMapper::toDomain).toList()))
                .orElseGet(() -> Uni.createFrom().item(List.of()));
    }

    @Override
    public Uni<List<Tune>> findByTuneType(String tuneType) {
        return Optional.ofNullable(tuneType)
                .map(
                        t -> dataSource.findByTuneType(t)
                                .map(entities -> entities.stream().map(TuneMapper::toDomain).toList()))
                .orElseGet(() -> Uni.createFrom().item(List.of()));
    }

    @Override
    public Uni<List<Tune>> findByDefaultKey(String defaultKey) {
        return Optional.ofNullable(defaultKey)
                .map(
                        k -> dataSource.findByDefaultKey(k)
                                .map(entities -> entities.stream().map(TuneMapper::toDomain).toList()))
                .orElseGet(() -> Uni.createFrom().item(List.of()));
    }
}
