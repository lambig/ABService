package com.abservice.infrastructure.persistence.repository;

import static com.abservice.lib.Iterables.toList;
import static java.util.function.Predicate.not;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.albumarticle.AlbumAcquisitionChannel;
import com.abservice.domain.model.aggregate.albumarticle.AlbumArticle;
import com.abservice.domain.model.aggregate.albumarticle.AlbumDistribution;
import com.abservice.domain.model.vo.album.LabelTag;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.domain.repository.albumarticle.AlbumArticleRepository;
import com.abservice.infrastructure.persistence.datasource.AlbumArticleDataSource;
import com.abservice.infrastructure.persistence.entity.AlbumAcquisitionChannelTableRecord;
import com.abservice.infrastructure.persistence.entity.AlbumArticleTableRecord;
import com.abservice.infrastructure.persistence.entity.AlbumDistributionTableRecord;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import com.abservice.infrastructure.persistence.mapper.AlbumArticleMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * AlbumArticleRepository実装
 *
 * <p>
 * Panacheを使用した非同期リポジトリ実装。
 * </p>
 */
@ApplicationScoped
public class AlbumArticleRepositoryImpl implements AlbumArticleRepository {

    private final AlbumArticleDataSource dataSource;

    public AlbumArticleRepositoryImpl(AlbumArticleDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Uni<AlbumArticle> save(AlbumArticle aggregate) {
        return dataSource
                .findAlbumWithArticleRelationsByDomainId(AlbumArticleMapper.toEntity(aggregate).getDomainId())
                .onItem().ifNotNull().transformToUni(album -> upsertArticle(album, aggregate))
                .onItem().ifNull().switchTo(
                        () -> Uni.createFrom().failure(
                                new IllegalStateException(
                                        "Album not found for id: " + aggregate.albumId().value())))
                .map(AlbumArticleMapper::toDomain);
    }

    private Uni<AlbumArticleTableRecord> upsertArticle(AlbumTableRecord album, AlbumArticle aggregate) {
        final var newValues = AlbumArticleMapper.toEntity(aggregate);
        reconcileDistribution(album, aggregate.distribution());
        reconcileAcquisitionChannels(album, aggregate.getAcquisitionChannels());
        return dataSource.persistAndFlush(
                Optional.ofNullable(album.getAlbumArticle())
                        .map(existing -> copyArticleScalarFields(existing, newValues))
                        .orElseGet(() -> linkNewArticle(album, newValues)));
    }

    private static AlbumArticleTableRecord copyArticleScalarFields(AlbumArticleTableRecord target,
            AlbumArticleTableRecord source) {
        target.setIntroLong(source.getIntroLong());
        target.setIntroShort(source.getIntroShort());
        target.setFirstEventSpace(source.getFirstEventSpace());
        target.setLabelTag(source.getLabelTag());
        return target;
    }

    private static AlbumArticleTableRecord linkNewArticle(AlbumTableRecord album, AlbumArticleTableRecord newValues) {
        newValues.setAlbum(album);
        album.setAlbumArticle(newValues);
        return newValues;
    }

    private static void reconcileDistribution(AlbumTableRecord album, @Nullable AlbumDistribution desired) {
        Optional.ofNullable(desired)
                .map(AlbumArticleMapper::toDistributionEntity)
                .ifPresentOrElse(
                        newEntity -> applyDistribution(album, newEntity),
                        () -> album.setAlbumDistribution(null));
    }

    private static void applyDistribution(AlbumTableRecord album, AlbumDistributionTableRecord newEntity) {
        Optional.ofNullable(album.getAlbumDistribution())
                .ifPresentOrElse(
                        existing -> copyDistributionFields(existing, newEntity),
                        () -> {
                            newEntity.setAlbum(album);
                            album.setAlbumDistribution(newEntity);
                        });
    }

    private static void copyDistributionFields(AlbumDistributionTableRecord target,
            AlbumDistributionTableRecord source) {
        target.setPhysicalPrice(source.getPhysicalPrice());
        target.setDownloadPrice(source.getDownloadPrice());
        target.setDemoUrl(source.getDemoUrl());
        target.setNote(source.getNote());
    }

    private static void reconcileAcquisitionChannels(AlbumTableRecord album, List<AlbumAcquisitionChannel> desired) {
        final var existingByDomainId = album.getAcquisitionChannels().stream()
                .collect(Collectors.toMap(AlbumAcquisitionChannelTableRecord::getDomainId, Function.identity()));
        final var desiredIds = desired.stream()
                .map(c -> c.id().value())
                .collect(Collectors.toSet());

        album.getAcquisitionChannels().removeIf(not(e -> desiredIds.contains(e.getDomainId())));

        desired.forEach(
                channel -> Optional.ofNullable(existingByDomainId.get(channel.id().value()))
                        .ifPresentOrElse(
                                existing -> copyChannelFields(existing, channel),
                                () -> {
                                    final var newEntity = AlbumArticleMapper.toAcquisitionChannelEntity(channel);
                                    newEntity.setAlbum(album);
                                    album.getAcquisitionChannels().add(newEntity);
                                }));
    }

    private static void copyChannelFields(AlbumAcquisitionChannelTableRecord target, AlbumAcquisitionChannel source) {
        target.setChannelType(source.getChannelType().name());
        target.setName(source.getName());
        target.setUrl(
                Optional.ofNullable(source.getUrl())
                        .map(Url::value)
                        .orElse(null));
        target.setNote(source.getNote());
    }

    @Override
    public Uni<List<AlbumArticle>> saveAll(Iterable<AlbumArticle> aggregates) {
        return Multi.createFrom().iterable(aggregates)
                .onItem().transformToUniAndConcatenate(this::save)
                .collect().asList();
    }

    @Override
    public Uni<AlbumArticle> findById(Album.Id id) {
        return Optional.ofNullable(id)
                .map(List::of)
                .map(this::findAllById)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(albumArticles -> albumArticles.stream().findFirst().orElse(null));
    }

    @Override
    public Uni<List<AlbumArticle>> findAllById(Iterable<Album.Id> ids) {
        return Optional.ofNullable(ids)
                .map(toList(Album.Id::value))
                .map(dataSource::findByIds)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(toList(AlbumArticleMapper::toDomain));
    }

    @Override
    public Uni<List<AlbumArticle>> findAll() {
        return dataSource.findAllEager()
                .map(toList(AlbumArticleMapper::toDomain));
    }

    @Override
    public Uni<Void> delete(AlbumArticle aggregate) {
        return Optional.ofNullable(aggregate)
                .map(a -> deleteById(a.albumId()))
                .orElseGet(() -> Uni.createFrom().voidItem());
    }

    @Override
    public Uni<Void> deleteAll(Iterable<AlbumArticle> aggregates) {
        return Optional.ofNullable(aggregates)
                .map(toList(AlbumArticle::albumId))
                .map(this::deleteAllById)
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
                .map(toList(Album.Id::value))
                .map(dataSource::deleteByAlbumIds)
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

    @Override
    public Uni<AlbumArticle> findByAlbumId(Album.Id albumId) {
        return Optional.ofNullable(albumId)
                .map(Album.Id::value)
                .map(dataSource::findByAlbumId)
                .orElseGet(() -> Uni.createFrom().nullItem())
                .onItem().ifNotNull().transform(AlbumArticleMapper::toDomain);
    }

    @Override
    public Uni<List<AlbumArticle>> findByLabelTag(LabelTag labelTag) {
        return Optional.ofNullable(labelTag)
                .map(LabelTag::name)
                .map(dataSource::findByLabelTag)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(toList(AlbumArticleMapper::toDomain));
    }

    @Override
    public Uni<List<AlbumArticle>> findByFirstEventSpaceContaining(String spaceKeyword) {
        return Optional.ofNullable(spaceKeyword)
                .map(dataSource::findByFirstEventSpaceContaining)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(toList(AlbumArticleMapper::toDomain));
    }

    @Override
    public Uni<List<AlbumArticle>> findWithDistribution() {
        return dataSource.findWithDistribution()
                .map(toList(AlbumArticleMapper::toDomain));
    }

    @Override
    public Uni<List<AlbumArticle>> findWithAcquisitionChannels() {
        return dataSource.findWithAcquisitionChannels()
                .map(toList(AlbumArticleMapper::toDomain));
    }
}
