package com.abservice.infrastructure.persistence.repository;

import static com.abservice.lib.Iterables.toList;
import static java.util.function.Predicate.not;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.ExternalAudio;
import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.model.aggregate.album.TrackTune;
import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.CatalogNumber;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.infrastructure.persistence.datasource.AlbumDataSource;
import com.abservice.infrastructure.persistence.entity.AlbumExternalAudioTableRecord;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import com.abservice.infrastructure.persistence.entity.TrackTableRecord;
import com.abservice.infrastructure.persistence.entity.TrackTuneTableRecord;
import com.abservice.infrastructure.persistence.mapper.AlbumMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
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
        final var entity = AlbumMapper.toEntity(aggregate);
        return dataSource.findByIdWithTracks(entity.getDomainId())
                .onItem().ifNotNull().transformToUni(
                        existingEntity -> {
                            reconcileTracks(existingEntity, aggregate.tracks());
                            reconcileExternalAudios(existingEntity, aggregate.externalAudios());
                            return dataSource.persistAndFlush(
                                    existingEntity
                                            .setTitle(entity.getTitle())
                                            .setReleaseDate(entity.getReleaseDate())
                                            .setArtistDisplayName(entity.getArtistDisplayName())
                                            .setArtistSortKey(entity.getArtistSortKey())
                                            .setEventName(entity.getEventName())
                                            .setEventDate(entity.getEventDate())
                                            .setEventPlace(entity.getEventPlace())
                                            .setEventSpaceNumber(entity.getEventSpaceNumber())
                                            .setEventNote(entity.getEventNote())
                                            .setCatalogNumber(entity.getCatalogNumber())
                                            .setIsdn(entity.getIsdn())
                                            .setCoverImageKey(entity.getCoverImageKey())
                                            .setPublishedAt(entity.getPublishedAt()));
                        })
                .onItem().ifNull().switchTo(() -> dataSource.persistAlbumWithRelations(entity))
                .map(AlbumMapper::toDomain);
    }

    /**
     * アルバムのトラック一覧を、既存行の内部ID・監査カラムを保ったまま反映する。
     *
     * <p>
     * {@code AlbumMapper.toEntity} を都度呼ぶと毎回新規エンティティが生成され、 {@code orphanRemoval}
     * により既存行が全削除・全件再insertされてしまう（#90）。 {@code domain_id}
     * で既存行を引き当てて差分のみ反映する（{@code AlbumArticleRepositoryImpl} の 入手経路の差分反映と同型）。
     * {@code orphanRemoval} 下ではコレクション参照そのものを差し替えることはできず（Hibernateが
     * dereferenceとして例外を送出する）、同一のコレクションインスタンスをインプレースで書き換える必要がある。
     * </p>
     *
     * @param album
     *            永続化済みのアルバムエンティティ（管理下）
     * @param desiredTracks
     *            アルバム集約が保持する望ましいトラック一覧
     */
    private static void reconcileTracks(AlbumTableRecord album, List<Track> desiredTracks) {
        final var existingByDomainId = album.getTracks().stream()
                .collect(Collectors.toMap(TrackTableRecord::getDomainId, Function.identity()));
        final var desiredIds = desiredTracks.stream()
                .map(t -> t.id().value())
                .collect(Collectors.toSet());

        album.getTracks().removeIf(not(e -> desiredIds.contains(e.getDomainId())));

        desiredTracks.forEach(
                track -> Optional.ofNullable(existingByDomainId.get(track.id().value()))
                        .ifPresentOrElse(
                                existing -> {
                                    copyTrackScalarFields(existing, track);
                                    reconcileTrackTunes(existing, track.tunes());
                                },
                                () -> album.getTracks().add(AlbumMapper.trackToEntity(track, album))));
    }

    /**
     * アルバムの外部音源一覧を、既存行の内部ID・監査カラムを保ったまま反映する。
     *
     * <p>
     * 反映方針は {@link #reconcileTracks} と同じ（{@code domain_id} で引き当て、インプレースで差分反映）。
     * 表示順の入れ替えは既存行の {@code display_order} を書き換えるため、トランザクション内の中間状態で
     * 一意制約に触れる。テーブル側の制約を遅延検証にしてコミット時に判定する（{@code V32}）。
     * </p>
     *
     * @param album
     *            永続化済みのアルバムエンティティ（管理下）
     * @param desiredExternalAudios
     *            アルバム集約が保持する望ましい外部音源一覧
     */
    private static void reconcileExternalAudios(AlbumTableRecord album, List<ExternalAudio> desiredExternalAudios) {
        final var existingByDomainId = album.getExternalAudios().stream()
                .collect(Collectors.toMap(AlbumExternalAudioTableRecord::getDomainId, Function.identity()));
        final var desiredIds = desiredExternalAudios.stream()
                .map(a -> a.id().value())
                .collect(Collectors.toSet());

        album.getExternalAudios().removeIf(not(e -> desiredIds.contains(e.getDomainId())));

        desiredExternalAudios.forEach(
                externalAudio -> Optional.ofNullable(existingByDomainId.get(externalAudio.id().value()))
                        .ifPresentOrElse(
                                existing -> copyExternalAudioFields(existing, externalAudio),
                                () -> album.getExternalAudios()
                                        .add(AlbumMapper.externalAudioToEntity(externalAudio, album))));
    }

    private static void copyExternalAudioFields(AlbumExternalAudioTableRecord target, ExternalAudio source) {
        target.setDisplayOrder(source.displayOrder());
        target.setUrl(source.url().value().value());
    }

    private static void copyTrackScalarFields(TrackTableRecord target, Track source) {
        target.setTrackNo(source.trackNo());
        target.setTitle(source.title().value());
        target.setRecordingDate(
                Optional.ofNullable(source.recordingDate())
                        .map(BusinessDate::asLocalDate)
                        .orElse(null));
        target.setRecordingPlace(source.recordingPlace());
        target.setIsLive(source.isLive());
        Optional.ofNullable(source.artistCredit())
                .ifPresentOrElse(
                        ac -> target.setArtistDisplayName(ac.displayName().value())
                                .setArtistSortKey(ac.sortKey()),
                        () -> target.setArtistDisplayName(null)
                                .setArtistSortKey(null));
    }

    /**
     * トラック内のチューン構成一覧を、既存行の内部ID・監査カラムを保ったまま反映する。
     *
     * @param trackEntity
     *            永続化済みのトラックエンティティ（管理下）
     * @param desiredTunes
     *            トラックが保持する望ましいチューン構成一覧
     */
    private static void reconcileTrackTunes(TrackTableRecord trackEntity, List<TrackTune> desiredTunes) {
        final var existingBySeq = trackEntity.getTrackTunes().stream()
                .collect(Collectors.toMap(e -> e.getId().getSeq(), Function.identity()));
        final var desiredSeqs = desiredTunes.stream()
                .map(TrackTune::seq)
                .collect(Collectors.toSet());

        trackEntity.getTrackTunes().removeIf(not(e -> desiredSeqs.contains(e.getId().getSeq())));

        desiredTunes.forEach(
                tune -> Optional.ofNullable(existingBySeq.get(tune.seq()))
                        .ifPresentOrElse(
                                existing -> copyTrackTuneFields(existing, tune),
                                () -> trackEntity.getTrackTunes()
                                        .add(AlbumMapper.trackTuneToEntity(tune, trackEntity))));
    }

    private static void copyTrackTuneFields(TrackTuneTableRecord target, TrackTune source) {
        target.setTuneId(
                Optional.ofNullable(source.tuneId())
                        .map(Tune.Id::value)
                        .orElse(null));
        target.setComposerCreditOverride(
                Optional.ofNullable(source.composerCreditOverride())
                        .map(Credit::value)
                        .orElse(null));
        target.setArrangerCreditOverride(
                Optional.ofNullable(source.arrangerCreditOverride())
                        .map(Credit::value)
                        .orElse(null));
        target.setLinkUrl(
                Optional.ofNullable(source.linkUrl())
                        .map(Url::value)
                        .orElse(null));
    }

    @Override
    public Uni<List<Album>> saveAll(Iterable<Album> aggregates) {
        return Multi.createFrom().iterable(aggregates)
                .onItem().transformToUniAndConcatenate(this::save)
                .collect().asList();
    }

    @Override
    public Uni<Album> findById(Album.Id id) {
        return Optional.ofNullable(id)
                .map(List::of)
                .map(this::findAllById)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(albums -> albums.stream().findFirst().orElse(null));
    }

    @Override
    public Uni<Album> findByIdExclusively(Album.Id id) {
        return Optional.ofNullable(id)
                .map(Album.Id::value)
                .map(this::lockedThenLoaded)
                .orElseGet(() -> Uni.createFrom().nullItem())
                .onItem().ifNotNull().transform(AlbumMapper::toDomain);
    }

    private Uni<AlbumTableRecord> lockedThenLoaded(String domainId) {
        return dataSource.lockByDomainId(domainId)
                .onItem().ifNotNull().transformToUni(locked -> dataSource.findByIdWithTracks(locked.getDomainId()));
    }

    @Override
    public Uni<List<Album>> findAllById(Iterable<Album.Id> ids) {
        return Optional.ofNullable(ids)
                .map(toList(Album.Id::value))
                .map(dataSource::findByIdsWithTracks)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(toList(AlbumMapper::toDomain));
    }

    @Override
    public Uni<List<Album>> findAll() {
        return dataSource.listAll()
                .map(toList(AlbumMapper::toDomain));
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
                .map(toList(Album::id))
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
    public Uni<List<Album>> findByTitle(AlbumTitle title) {
        return Optional.ofNullable(title)
                .map(AlbumTitle::value)
                .map(dataSource::findByTitle)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(toList(AlbumMapper::toDomain));
    }

    @Override
    public Uni<List<Album>> findByArtistName(String artistName) {
        return Optional.ofNullable(artistName)
                .filter(StringUtils::isNotBlank)
                .map(dataSource::findByArtistDisplayName)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(toList(AlbumMapper::toDomain));
    }

    @Override
    public Uni<List<Album>> findByEventName(String eventName) {
        return Optional.ofNullable(eventName)
                .filter(StringUtils::isNotBlank)
                .map(dataSource::findByEventName)
                .orElseGet(() -> Uni.createFrom().item(List.of()))
                .map(toList(AlbumMapper::toDomain));
    }

    @Override
    public Uni<Album> findByCatalogNumber(CatalogNumber catalogNumber) {
        return Optional.ofNullable(catalogNumber)
                .map(CatalogNumber::value)
                .map(dataSource::findByCatalogNumber)
                .orElseGet(() -> Uni.createFrom().nullItem())
                .onItem().ifNotNull().transform(AlbumMapper::toDomain);
    }

    @Override
    public Uni<List<Album>> findByReleaseYear(int year) {
        return dataSource.findByReleaseYear(year)
                .map(toList(AlbumMapper::toDomain));
    }
}
