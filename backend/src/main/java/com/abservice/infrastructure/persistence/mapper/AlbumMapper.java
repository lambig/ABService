package com.abservice.infrastructure.persistence.mapper;

import static com.abservice.lib.Iterables.toList;
import static java.util.function.Predicate.not;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.model.aggregate.album.TrackTune;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.CatalogNumber;
import com.abservice.domain.model.vo.album.Isdn;
import com.abservice.domain.model.vo.album.TrackTitle;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.common.EventDateAndSpace;
import com.abservice.domain.model.vo.common.EventReleasedAt;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.infrastructure.persistence.entity.AlbumEntity;
import com.abservice.infrastructure.persistence.entity.AlbumEventDateSpaceEntity;
import com.abservice.infrastructure.persistence.entity.TrackEntity;
import com.abservice.infrastructure.persistence.entity.TrackTuneEntity;
import com.abservice.infrastructure.persistence.entity.TrackTuneId;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Album Mapper
 *
 * <p>
 * AlbumドメインモデルとAlbumEntityの相互変換を担当します。
 * </p>
 */
public final class AlbumMapper {

    private AlbumMapper() {
        // ユーティリティクラス
    }

    /**
     * EntityからDomainモデルへ変換
     *
     * @param entity
     *            AlbumEntity
     * @return Album
     */
    public static Album toDomain(AlbumEntity entity) {
        return Album.reconstruct(
                new Album.Id(entity.getDomainId()),
                new AlbumTitle(entity.getTitle()),
                BusinessDate.of(entity.getReleaseDate()),
                buildArtistCredit(entity),
                buildEventReleasedAt(entity),
                Optional.ofNullable(entity.getCatalogNumber())
                        .map(CatalogNumber::new)
                        .orElse(null),
                Optional.ofNullable(entity.getIsdn())
                        .map(Isdn::new)
                        .orElse(null),
                buildTracks(entity));
    }

    private static ArtistCredit buildArtistCredit(AlbumEntity entity) {
        return ArtistCredit.of(entity.getArtistDisplayName(), entity.getArtistSortKey());
    }

    private static List<Track> buildTracks(AlbumEntity entity) {
        return Optional.ofNullable(entity.getTracks())
                .map(toList(AlbumMapper::trackToDomain))
                .orElseGet(Collections::emptyList);
    }

    private static @Nullable EventReleasedAt buildEventReleasedAt(AlbumEntity entity) {
        return Optional.ofNullable(entity.getEventName()).map(
                eventName -> EventReleasedAt.of(
                        eventName,
                        extractDateAndSpaces(entity),
                        entity.getEventPlace(),
                        entity.getEventNote()))
                .orElse(null);
    }

    private static @Nullable List<EventDateAndSpace> extractDateAndSpaces(AlbumEntity entity) {
        return Optional.ofNullable(entity.getEventDateSpaces())
                .filter(not(List::isEmpty))
                .map(toList(e -> EventDateAndSpace.of(BusinessDate.of(e.getEventDate()), e.getSpaceNumber())))
                .or(() -> buildLegacyDateAndSpaces(entity)).orElse(null);
    }

    private static Optional<List<EventDateAndSpace>> buildLegacyDateAndSpaces(AlbumEntity entity) {
        return Optional.ofNullable(entity.getEventDate()).map(
                eventDate -> List.of(EventDateAndSpace.of(BusinessDate.of(eventDate), entity.getEventSpaceNumber())));
    }

    /**
     * DomainモデルからEntityへ変換
     *
     * @param album
     *            Album
     * @return AlbumEntity
     */
    public static AlbumEntity toEntity(Album album) {
        final var albumEntity = new AlbumEntity();
        albumEntity.setDomainId(album.id().value());
        albumEntity.setTitle(album.title().value());
        albumEntity.setReleaseDate(album.releaseDate().asLocalDate());
        setArtistCreditFields(albumEntity, album.artistCredit());
        Optional.ofNullable(album.eventReleasedAt())
                .ifPresent(event -> populateEventFields(albumEntity, event));
        setCatalogFields(albumEntity, album);
        setTracksField(albumEntity, album);
        return albumEntity;
    }

    private static void setArtistCreditFields(AlbumEntity entity, ArtistCredit credit) {
        entity.setArtistDisplayName(credit.displayName().value());
        entity.setArtistSortKey(credit.sortKey());
    }

    private static void setCatalogFields(AlbumEntity entity, Album album) {
        entity.setCatalogNumber(
                Optional.ofNullable(album.catalogNumber())
                        .map(CatalogNumber::value)
                        .orElse(null));
        entity.setIsdn(
                Optional.ofNullable(album.isdn())
                        .map(Isdn::value)
                        .orElse(null));
    }

    private static void setTracksField(AlbumEntity entity, Album album) {
        Optional.ofNullable(album.tracks())
                .filter(not(List::isEmpty))
                .map(toList(track -> trackToEntity(track, entity)))
                .ifPresent(entity::setTracks);
    }

    private static void populateEventFields(AlbumEntity albumEntity, EventReleasedAt event) {
        albumEntity.setEventName(event.name().value());
        albumEntity.setEventPlace(event.place());
        albumEntity.setEventNote(event.note());

        Optional.ofNullable(event.dateAndSpaces())
                .filter(not(List::isEmpty))
                .ifPresent(dateAndSpaces -> {
                    populateDateAndSpaceEntities(albumEntity, dateAndSpaces);
                    populateLegacyDateAndSpace(albumEntity, dateAndSpaces.get(0));
                });
    }

    private static void populateDateAndSpaceEntities(AlbumEntity albumEntity, List<EventDateAndSpace> dateAndSpaces) {
        albumEntity.setEventDateSpaces(toList(dateAndSpaces, ds -> {
            final var entity = new AlbumEventDateSpaceEntity();
            entity.setAlbum(albumEntity);
            entity.setEventDate(ds.date().asLocalDate());
            entity.setSpaceNumber(ds.spaceNumber());
            // 監査カラムのデフォルト値を設定
            entity.setCreatedByService("abservice");
            entity.setUpdatedByService("abservice");
            return entity;
        }));
    }

    private static void populateLegacyDateAndSpace(AlbumEntity albumEntity, EventDateAndSpace firstDateSpace) {
        albumEntity.setEventDate(firstDateSpace.date().asLocalDate());
        albumEntity.setEventSpaceNumber(firstDateSpace.spaceNumber());
    }

    /**
     * TrackEntityからTrackドメインモデルへ変換
     *
     * @param entity
     *            TrackEntity
     * @return Track
     */
    private static Track trackToDomain(TrackEntity entity) {
        return Track.reconstruct(
                new Track.Id(entity.getDomainId()),
                entity.getTrackNo(),
                new TrackTitle(entity.getTitle()),
                buildTrackArtistCredit(entity),
                Optional.ofNullable(entity.getRecordingDate())
                        .map(BusinessDate::of)
                        .orElse(null),
                entity.getRecordingPlace(),
                entity.getIsLive(),
                buildTrackTunes(entity));
    }

    private static @Nullable ArtistCredit buildTrackArtistCredit(TrackEntity entity) {
        return Optional.ofNullable(entity.getArtistDisplayName())
                .map(name -> ArtistCredit.of(name, entity.getArtistSortKey()))
                .orElse(null);
    }

    private static List<TrackTune> buildTrackTunes(TrackEntity entity) {
        return Optional.ofNullable(entity.getTrackTunes())
                .map(toList(AlbumMapper::trackTuneToDomain))
                .orElseGet(Collections::emptyList);
    }

    /**
     * TrackドメインモデルからTrackEntityへ変換
     *
     * @param track
     *            Track
     * @param albumEntity
     *            親のAlbumEntity
     * @return TrackEntity
     */
    private static TrackEntity trackToEntity(Track track, AlbumEntity albumEntity) {
        final var trackEntity = new TrackEntity();
        trackEntity.setDomainId(track.id().value());
        trackEntity.setAlbum(albumEntity);
        trackEntity.setTrackNo(track.trackNo());
        trackEntity.setTitle(track.title().value());
        Optional.ofNullable(track.artistCredit())
                .ifPresent(ac -> setTrackArtistCredit(trackEntity, ac));
        trackEntity.setRecordingDate(
                Optional.ofNullable(track.recordingDate())
                        .map(BusinessDate::asLocalDate)
                        .orElse(null));
        trackEntity.setRecordingPlace(track.recordingPlace());
        trackEntity.setIsLive(track.isLive());
        setTrackTunesField(trackEntity, track);
        return trackEntity;
    }

    private static void setTrackArtistCredit(TrackEntity entity, ArtistCredit credit) {
        entity.setArtistDisplayName(credit.displayName().value());
        entity.setArtistSortKey(credit.sortKey());
    }

    private static void setTrackTunesField(TrackEntity entity, Track track) {
        Optional.ofNullable(track.tunes())
                .filter(not(List::isEmpty))
                .map(toList(trackTune -> trackTuneToEntity(trackTune, entity)))
                .ifPresent(entity::setTrackTunes);
    }

    /**
     * TrackTuneEntityからTrackTuneドメインモデルへ変換
     *
     * @param entity
     *            TrackTuneEntity
     * @return TrackTune
     */
    private static TrackTune trackTuneToDomain(TrackTuneEntity entity) {
        return TrackTune.reconstruct(
                entity.getId().getSeq(),
                Optional.ofNullable(entity.getTuneId())
                        .map(Tune.Id::new)
                        .orElse(null),
                Optional.ofNullable(entity.getComposerCreditOverride())
                        .map(Credit::new)
                        .orElse(null),
                Optional.ofNullable(entity.getArrangerCreditOverride())
                        .map(Credit::new)
                        .orElse(null),
                Optional.ofNullable(entity.getLinkUrl())
                        .map(Url::new)
                        .orElse(null));
    }

    /**
     * TrackTuneドメインモデルからTrackTuneEntityへ変換
     *
     * @param trackTune
     *            TrackTune
     * @param trackEntity
     *            親のTrackEntity
     * @return TrackTuneEntity
     */
    private static TrackTuneEntity trackTuneToEntity(TrackTune trackTune, TrackEntity trackEntity) {
        final var trackTuneEntity = new TrackTuneEntity();
        trackTuneEntity.setId(new TrackTuneId(trackEntity.getTrackId(), trackTune.seq()));
        trackTuneEntity.setTrack(trackEntity);
        trackTuneEntity.setTuneId(
                Optional.ofNullable(trackTune.tuneId())
                        .map(Tune.Id::value)
                        .orElse(null));
        trackTuneEntity.setComposerCreditOverride(
                Optional.ofNullable(trackTune.composerCreditOverride())
                        .map(Credit::value)
                        .orElse(null));
        trackTuneEntity.setArrangerCreditOverride(
                Optional.ofNullable(trackTune.arrangerCreditOverride())
                        .map(Credit::value)
                        .orElse(null));
        trackTuneEntity.setLinkUrl(
                Optional.ofNullable(trackTune.linkUrl())
                        .map(Url::value)
                        .orElse(null));
        return trackTuneEntity;
    }
}
