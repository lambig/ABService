package com.abservice.infrastructure.persistence.repository;

import static com.abservice.lib.Iterables.toList;

import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.model.vo.tune.TuneKind;
import com.abservice.domain.model.vo.tune.TuneTitle;
import com.abservice.domain.repository.tune.TuneRepository;
import com.abservice.infrastructure.persistence.datasource.TuneDataSource;
import com.abservice.infrastructure.persistence.mapper.TuneMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

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
        final var entity = TuneMapper.toEntity(aggregate);
        // 存在すれば更新、なければ新規作成
        return dataSource.find("domainId", entity.getDomainId()).firstResult()
                .onItem().ifNotNull().transformToUni(
                        existingEntity -> dataSource.persistAndFlush(
                                existingEntity
                                        .setTitle(entity.getTitle())
                                        .setTuneKind(entity.getTuneKind())
                                        .setDefaultComposerCredit(entity.getDefaultComposerCredit())
                                        .setDefaultArrangerCredit(entity.getDefaultArrangerCredit())
                                        .setOriginalWorkTitle(entity.getOriginalWorkTitle())
                                        .setOriginalWorkCredit(entity.getOriginalWorkCredit())
                                        .setTuneType(entity.getTuneType())
                                        .setDefaultKey(entity.getDefaultKey())
                                        .setDefaultTempo(entity.getDefaultTempo())))
                .onItem().ifNull().switchTo(() -> dataSource.persistAndFlush(entity))
                .map(TuneMapper::toDomain);
    }

    @Override
    public Uni<List<Tune>> saveAll(Iterable<Tune> aggregates) {
        return Multi.createFrom().iterable(aggregates)
                .onItem().transformToUniAndConcatenate(this::save)
                .collect().asList();
    }

    @Override
    public Uni<Tune> findById(Tune.Id id) {
        return Optional.ofNullable(id)
                .map(List::of)
                .map(this::findAllById)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(tunes -> tunes.stream().findFirst().orElse(null));
    }

    @Override
    public Uni<List<Tune>> findAllById(Iterable<Tune.Id> ids) {
        return Optional.ofNullable(ids)
                .map(toList(Tune.Id::value))
                .map(dataSource::findByIds)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(toList(TuneMapper::toDomain));
    }

    @Override
    public Uni<List<Tune>> findAll() {
        return dataSource.listAll()
                .map(toList(TuneMapper::toDomain));
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
                        a -> Multi.createFrom().iterable(a)
                                .onItem().call(this::delete)
                                .collect().asList().replaceWithVoid())
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
                        i -> Multi.createFrom().iterable(i)
                                .onItem().call(this::deleteById)
                                .collect().asList().replaceWithVoid())
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
                .map(TuneTitle::value)
                .map(dataSource::findByTitle)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(toList(TuneMapper::toDomain));
    }

    @Override
    public Uni<List<Tune>> findByTuneKind(TuneKind tuneKind) {
        return Optional.ofNullable(tuneKind)
                .map(TuneKind::name)
                .map(dataSource::findByTuneKind)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(toList(TuneMapper::toDomain));
    }

    @Override
    public Uni<List<Tune>> findByTuneType(String tuneType) {
        return Optional.ofNullable(tuneType)
                .map(dataSource::findByTuneType)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(toList(TuneMapper::toDomain));
    }

    @Override
    public Uni<List<Tune>> findByDefaultKey(String defaultKey) {
        return Optional.ofNullable(defaultKey)
                .map(dataSource::findByDefaultKey)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(toList(TuneMapper::toDomain));
    }
}
