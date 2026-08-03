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
import com.abservice.domain.model.vo.common.EventReleasedAt;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import com.abservice.infrastructure.persistence.entity.TrackTableRecord;
import com.abservice.infrastructure.persistence.entity.TrackTuneTableRecord;
import com.abservice.infrastructure.persistence.entity.TrackTuneId;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Album Mapper
 *
 * <p>
 * AlbumドメインモデルとAlbumTableRecordの相互変換を担当します。
 * </p>
 */
public final class AlbumMapper {

    private AlbumMapper() {
    }

    /**
     * EntityからDomainモデルへ変換
     *
     * @param entity
     *            AlbumTableRecord
     * @return Album
     */
    public static Album toDomain(AlbumTableRecord entity) {
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

    private static ArtistCredit buildArtistCredit(AlbumTableRecord entity) {
        return ArtistCredit.of(entity.getArtistDisplayName(), entity.getArtistSortKey());
    }

    private static List<Track> buildTracks(AlbumTableRecord entity) {
        return Optional.ofNullable(entity.getTracks())
                .map(toList(AlbumMapper::trackToDomain))
                .orElseGet(Collections::emptyList);
    }

    private static @Nullable EventReleasedAt buildEventReleasedAt(AlbumTableRecord entity) {
        return Optional.ofNullable(entity.getEventName())
                .map(
                        eventName -> EventReleasedAt.of(
                                eventName,
                                Optional.ofNullable(entity.getEventDate())
                                        .map(BusinessDate::of)
                                        .orElse(null),
                                entity.getEventPlace(),
                                entity.getEventSpaceNumber(),
                                entity.getEventNote()))
                .orElse(null);
    }

    /**
     * DomainモデルからEntityへ変換
     *
     * @param album
     *            Album
     * @return AlbumTableRecord
     */
    public static AlbumTableRecord toEntity(Album album) {
        final var albumEntity = new AlbumTableRecord()
                .setDomainId(album.id().value())
                .setTitle(album.title().value())
                .setReleaseDate(album.releaseDate().asLocalDate());
        setArtistCreditFields(albumEntity, album.artistCredit());
        Optional.ofNullable(album.eventReleasedAt())
                .ifPresent(event -> populateEventFields(albumEntity, event));
        setCatalogFields(albumEntity, album);
        setTracksField(albumEntity, album);
        return albumEntity;
    }

    private static void setArtistCreditFields(AlbumTableRecord entity, ArtistCredit credit) {
        entity.setArtistDisplayName(credit.displayName().value())
                .setArtistSortKey(credit.sortKey());
    }

    private static void setCatalogFields(AlbumTableRecord entity, Album album) {
        entity.setCatalogNumber(
                Optional.ofNullable(album.catalogNumber())
                        .map(CatalogNumber::value)
                        .orElse(null))
                .setIsdn(
                        Optional.ofNullable(album.isdn())
                                .map(Isdn::value)
                                .orElse(null));
    }

    private static void setTracksField(AlbumTableRecord entity, Album album) {
        Optional.ofNullable(album.tracks())
                .filter(not(List::isEmpty))
                .map(toList(track -> trackToEntity(track, entity)))
                .ifPresent(entity::setTracks);
    }

    private static void populateEventFields(AlbumTableRecord albumEntity, EventReleasedAt event) {
        albumEntity.setEventName(event.name().value())
                .setEventPlace(event.place())
                .setEventSpaceNumber(event.spaceNumber())
                .setEventNote(event.note());
        Optional.ofNullable(event.date())
                .ifPresent(date -> albumEntity.setEventDate(date.asLocalDate()));
    }

    /**
     * TrackTableRecordからTrackドメインモデルへ変換
     *
     * @param entity
     *            TrackTableRecord
     * @return Track
     */
    private static Track trackToDomain(TrackTableRecord entity) {
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

    private static @Nullable ArtistCredit buildTrackArtistCredit(TrackTableRecord entity) {
        return Optional.ofNullable(entity.getArtistDisplayName())
                .map(name -> ArtistCredit.of(name, entity.getArtistSortKey()))
                .orElse(null);
    }

    private static List<TrackTune> buildTrackTunes(TrackTableRecord entity) {
        return Optional.ofNullable(entity.getTrackTunes())
                .map(toList(AlbumMapper::trackTuneToDomain))
                .orElseGet(Collections::emptyList);
    }

    /**
     * TrackドメインモデルからTrackTableRecordへ変換
     *
     * @param track
     *            Track
     * @param albumEntity
     *            親のAlbumTableRecord
     * @return TrackTableRecord
     */
    private static TrackTableRecord trackToEntity(Track track, AlbumTableRecord albumEntity) {
        final var trackEntity = new TrackTableRecord()
                .setDomainId(track.id().value())
                .setAlbum(albumEntity)
                .setTrackNo(track.trackNo())
                .setTitle(track.title().value())
                .setRecordingDate(
                        Optional.ofNullable(track.recordingDate())
                                .map(BusinessDate::asLocalDate)
                                .orElse(null))
                .setRecordingPlace(track.recordingPlace())
                .setIsLive(track.isLive());
        Optional.ofNullable(track.artistCredit())
                .ifPresent(ac -> setTrackArtistCredit(trackEntity, ac));
        setTrackTunesField(trackEntity, track);
        return trackEntity;
    }

    private static void setTrackArtistCredit(TrackTableRecord entity, ArtistCredit credit) {
        entity.setArtistDisplayName(credit.displayName().value())
                .setArtistSortKey(credit.sortKey());
    }

    private static void setTrackTunesField(TrackTableRecord entity, Track track) {
        Optional.ofNullable(track.tunes())
                .filter(not(List::isEmpty))
                .map(toList(trackTune -> trackTuneToEntity(trackTune, entity)))
                .ifPresent(entity::setTrackTunes);
    }

    /**
     * TrackTuneTableRecordからTrackTuneドメインモデルへ変換
     *
     * @param entity
     *            TrackTuneTableRecord
     * @return TrackTune
     */
    private static TrackTune trackTuneToDomain(TrackTuneTableRecord entity) {
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
     * TrackTuneドメインモデルからTrackTuneTableRecordへ変換
     *
     * @param trackTune
     *            TrackTune
     * @param trackEntity
     *            親のTrackTableRecord
     * @return TrackTuneTableRecord
     */
    private static TrackTuneTableRecord trackTuneToEntity(TrackTune trackTune, TrackTableRecord trackEntity) {
        return new TrackTuneTableRecord()
                .setId(new TrackTuneId(trackEntity.getTrackId(), trackTune.seq()))
                .setTrack(trackEntity)
                .setTuneId(
                        Optional.ofNullable(trackTune.tuneId())
                                .map(Tune.Id::value)
                                .orElse(null))
                .setComposerCreditOverride(
                        Optional.ofNullable(trackTune.composerCreditOverride())
                                .map(Credit::value)
                                .orElse(null))
                .setArrangerCreditOverride(
                        Optional.ofNullable(trackTune.arrangerCreditOverride())
                                .map(Credit::value)
                                .orElse(null))
                .setLinkUrl(
                        Optional.ofNullable(trackTune.linkUrl())
                                .map(Url::value)
                                .orElse(null));
    }
}
