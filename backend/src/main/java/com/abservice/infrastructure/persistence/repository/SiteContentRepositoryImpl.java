package com.abservice.infrastructure.persistence.repository;

import static com.abservice.lib.Iterables.toList;

import com.abservice.domain.model.aggregate.site.SiteContent;
import com.abservice.domain.model.vo.site.SiteContentKey;
import com.abservice.domain.repository.site.SiteContentRepository;
import com.abservice.infrastructure.persistence.datasource.SiteContentDataSource;
import com.abservice.infrastructure.persistence.mapper.SiteContentMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

/**
 * SiteContentRepository実装
 *
 * <p>
 * Panacheを使用した非同期リポジトリ実装。
 * </p>
 */
@ApplicationScoped
public class SiteContentRepositoryImpl implements SiteContentRepository {

    private final SiteContentDataSource dataSource;

    public SiteContentRepositoryImpl(SiteContentDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Uni<SiteContent> save(SiteContent aggregate) {
        final var entity = SiteContentMapper.toEntity(aggregate);
        return dataSource.findByDomainId(entity.getDomainId())
                .onItem().ifNotNull().transformToUni(
                        existing -> dataSource.persistAndFlush(
                                existing
                                        .setContentKey(entity.getContentKey())
                                        .setContent(entity.getContent())
                                        .setContentFormat(entity.getContentFormat())))
                .onItem().ifNull().switchTo(() -> dataSource.persistAndFlush(entity))
                .map(SiteContentMapper::toDomain);
    }

    @Override
    public Uni<List<SiteContent>> saveAll(Iterable<SiteContent> aggregates) {
        return Multi.createFrom().iterable(aggregates)
                .onItem().transformToUniAndConcatenate(this::save)
                .collect().asList();
    }

    @Override
    public Uni<SiteContent> findById(SiteContent.Id id) {
        return Optional.ofNullable(id)
                .map(SiteContent.Id::value)
                .map(dataSource::findByDomainId)
                .orElseGet(() -> Uni.createFrom().nullItem())
                .onItem().ifNotNull().transform(SiteContentMapper::toDomain);
    }

    @Override
    public Uni<List<SiteContent>> findAllById(Iterable<SiteContent.Id> ids) {
        return Optional.ofNullable(ids)
                .map(toList(SiteContent.Id::value))
                .map(dataSource::findByIds)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(toList(SiteContentMapper::toDomain));
    }

    @Override
    public Uni<List<SiteContent>> findAll() {
        return dataSource.listAllOrderByKey()
                .map(toList(SiteContentMapper::toDomain));
    }

    @Override
    public Uni<Void> delete(SiteContent aggregate) {
        return Optional.ofNullable(aggregate)
                .map(a -> deleteById(a.id()))
                .orElseGet(() -> Uni.createFrom().voidItem());
    }

    @Override
    public Uni<Void> deleteAll(Iterable<SiteContent> aggregates) {
        return Optional.ofNullable(aggregates)
                .map(toList(SiteContent::id))
                .map(this::deleteAllById)
                .orElseGet(() -> Uni.createFrom().voidItem());
    }

    @Override
    public Uni<Void> deleteById(SiteContent.Id id) {
        return Optional.ofNullable(id)
                .map(i -> dataSource.deleteByDomainId(i.value()).replaceWithVoid())
                .orElseGet(() -> Uni.createFrom().voidItem());
    }

    @Override
    public Uni<Void> deleteAllById(Iterable<SiteContent.Id> ids) {
        return Optional.ofNullable(ids)
                .map(toList(SiteContent.Id::value))
                .map(dataSource::deleteByDomainIds)
                .orElseGet(() -> Uni.createFrom().voidItem());
    }

    @Override
    public Uni<Boolean> existsById(SiteContent.Id id) {
        return Optional.ofNullable(id)
                .map(i -> dataSource.existsByDomainId(i.value()))
                .orElseGet(() -> Uni.createFrom().item(false));
    }

    @Override
    public Uni<Long> count() {
        return dataSource.count();
    }

    @Override
    public Uni<SiteContent> findByKey(SiteContentKey key) {
        return Optional.ofNullable(key)
                .map(SiteContentKey::value)
                .map(dataSource::findByContentKey)
                .orElseGet(() -> Uni.createFrom().nullItem())
                .onItem().ifNotNull().transform(SiteContentMapper::toDomain);
    }
}
