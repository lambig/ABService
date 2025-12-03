package com.abservice.infrastructure.persistence.mapper;

import static java.util.function.Predicate.not;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.model.aggregate.album.TrackTune;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.CatalogNumber;
import com.abservice.domain.model.vo.album.Isdn;
import com.abservice.domain.model.vo.album.TrackTitle;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.ArtistCreditName;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.common.EventDateAndSpace;
import com.abservice.domain.model.vo.common.EventReleasedAt;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.domain.model.vo.event.EventName;
import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.infrastructure.persistence.entity.AlbumEntity;
import com.abservice.infrastructure.persistence.entity.AlbumEventDateSpaceEntity;
import com.abservice.infrastructure.persistence.entity.TrackEntity;
import com.abservice.infrastructure.persistence.entity.TrackTuneEntity;
import com.abservice.infrastructure.persistence.entity.TrackTuneId;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
        return Optional.ofNullable(entity)
                .map(e -> Album.reconstruct(new Album.Id(e.getDomainId()), new AlbumTitle(e.getTitle()),
                        BusinessDate.of(e.getReleaseDate()), buildArtistCredit(e), buildEventReleasedAt(e),
                        Optional.ofNullable(e.getCatalogNumber()).map(CatalogNumber::new).orElse(null),
                        Optional.ofNullable(e.getIsdn()).map(Isdn::new).orElse(null), buildTracks(e)))
                .orElse(null);
    }

    private static ArtistCredit buildArtistCredit(AlbumEntity entity) {
        return new ArtistCredit(new ArtistCreditName(entity.getArtistDisplayName()), entity.getArtistSortKey());
    }

    private static List<Track> buildTracks(AlbumEntity entity) {
        return Optional.ofNullable(entity.getTracks())
                .map(list -> list.stream().map(AlbumMapper::trackToDomain).collect(Collectors.toList()))
                .orElseGet(Collections::emptyList);
    }

    private static EventReleasedAt buildEventReleasedAt(AlbumEntity entity) {
        return Optional.ofNullable(entity.getEventName()).map(eventName -> {
            var dateAndSpaces = extractDateAndSpaces(entity);
            return new EventReleasedAt(new EventName(eventName), dateAndSpaces, entity.getEventPlace(),
                    entity.getEventNote());
        }).orElse(null);
    }

    private static List<EventDateAndSpace> extractDateAndSpaces(AlbumEntity entity) {
        return Optional.ofNullable(entity.getEventDateSpaces()).filter(not(List::isEmpty))
                .map(list -> list.stream()
                        .map(e -> new EventDateAndSpace(BusinessDate.of(e.getEventDate()), e.getSpaceNumber()))
                        .collect(Collectors.toList()))
                .or(() -> buildLegacyDateAndSpaces(entity)).orElse(null);
    }

    private static Optional<List<EventDateAndSpace>> buildLegacyDateAndSpaces(AlbumEntity entity) {
        return Optional.ofNullable(entity.getEventDate()).map(
                eventDate -> List.of(new EventDateAndSpace(BusinessDate.of(eventDate), entity.getEventSpaceNumber())));
    }

    /**
     * DomainモデルからEntityへ変換
     *
     * @param album
     *            Album
     * @return AlbumEntity
     */
    public static AlbumEntity toEntity(Album album) {
        return Optional.ofNullable(album).map(a -> {
            var albumEntity = new AlbumEntity();
            albumEntity.setDomainId(a.id().value());
            albumEntity.setTitle(a.title().value());
            albumEntity.setReleaseDate(a.releaseDate().asLocalDate());
            setArtistCreditFields(albumEntity, a.artistCredit());
            Optional.ofNullable(a.eventReleasedAt()).ifPresent(event -> populateEventFields(albumEntity, event));
            setCatalogFields(albumEntity, a);
            setTracksField(albumEntity, a);
            return albumEntity;
        }).orElse(null);
    }

    private static void setArtistCreditFields(AlbumEntity entity, ArtistCredit credit) {
        entity.setArtistDisplayName(credit.displayName().value());
        entity.setArtistSortKey(credit.sortKey());
    }

    private static void setCatalogFields(AlbumEntity entity, Album album) {
        entity.setCatalogNumber(Optional.ofNullable(album.catalogNumber()).map(CatalogNumber::value).orElse(null));
        entity.setIsdn(Optional.ofNullable(album.isdn()).map(Isdn::value).orElse(null));
    }

    private static void setTracksField(AlbumEntity entity, Album album) {
        Optional.ofNullable(album.tracks()).filter(not(List::isEmpty))
                .map(tracks -> tracks.stream().map(track -> trackToEntity(track, entity)).collect(Collectors.toList()))
                .ifPresent(entity::setTracks);
    }

    private static void populateEventFields(AlbumEntity albumEntity, EventReleasedAt event) {
        albumEntity.setEventName(event.name().value());
        albumEntity.setEventPlace(event.place());
        albumEntity.setEventNote(event.note());

        Optional.ofNullable(event.dateAndSpaces()).filter(not(List::isEmpty)).ifPresent(dateAndSpaces -> {
            populateDateAndSpaceEntities(albumEntity, dateAndSpaces);
            populateLegacyDateAndSpace(albumEntity, dateAndSpaces.get(0));
        });
    }

    private static void populateDateAndSpaceEntities(AlbumEntity albumEntity, List<EventDateAndSpace> dateAndSpaces) {
        var entities = dateAndSpaces.stream().map(ds -> {
            var entity = new AlbumEventDateSpaceEntity();
            entity.setAlbum(albumEntity);
            entity.setEventDate(ds.date().asLocalDate());
            entity.setSpaceNumber(ds.spaceNumber());
            // 監査カラムのデフォルト値を設定
            entity.setCreatedByService("abservice");
            entity.setUpdatedByService("abservice");
            return entity;
        }).collect(Collectors.toList());
        albumEntity.setEventDateSpaces(entities);
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
        return Optional.ofNullable(entity)
                .map(e -> Track.reconstruct(new Track.Id(e.getDomainId()), e.getTrackNo(), new TrackTitle(e.getTitle()),
                        buildTrackArtistCredit(e),
                        Optional.ofNullable(e.getRecordingDate()).map(BusinessDate::of).orElse(null),
                        e.getRecordingPlace(), e.getIsLive(), buildTrackTunes(e)))
                .orElse(null);
    }

    private static ArtistCredit buildTrackArtistCredit(TrackEntity entity) {
        return Optional.ofNullable(entity.getArtistDisplayName())
                .map(name -> new ArtistCredit(new ArtistCreditName(name), entity.getArtistSortKey())).orElse(null);
    }

    private static List<TrackTune> buildTrackTunes(TrackEntity entity) {
        return Optional.ofNullable(entity.getTrackTunes())
                .map(list -> list.stream().map(AlbumMapper::trackTuneToDomain).collect(Collectors.toList()))
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
        return Optional.ofNullable(track).map(t -> {
            var trackEntity = new TrackEntity();
            trackEntity.setDomainId(t.id().value());
            trackEntity.setAlbum(albumEntity);
            trackEntity.setTrackNo(t.trackNo());
            trackEntity.setTitle(t.title().value());
            Optional.ofNullable(t.artistCredit()).ifPresent(ac -> setTrackArtistCredit(trackEntity, ac));
            trackEntity.setRecordingDate(
                    Optional.ofNullable(t.recordingDate()).map(BusinessDate::asLocalDate).orElse(null));
            trackEntity.setRecordingPlace(t.recordingPlace());
            trackEntity.setIsLive(t.isLive());
            setTrackTunesField(trackEntity, t);
            return trackEntity;
        }).orElse(null);
    }

    private static void setTrackArtistCredit(TrackEntity entity, ArtistCredit credit) {
        entity.setArtistDisplayName(credit.displayName().value());
        entity.setArtistSortKey(credit.sortKey());
    }

    private static void setTrackTunesField(TrackEntity entity, Track track) {
        Optional.ofNullable(track.tunes()).filter(not(List::isEmpty)).map(tunes -> tunes.stream()
                .map(trackTune -> trackTuneToEntity(trackTune, entity)).collect(Collectors.toList()))
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
        return Optional.ofNullable(entity)
                .map(e -> TrackTune.reconstruct(e.getId().getSeq(),
                        Optional.ofNullable(e.getTuneId()).map(Tune.Id::new).orElse(null),
                        Optional.ofNullable(e.getComposerCreditOverride()).map(Credit::new).orElse(null),
                        Optional.ofNullable(e.getArrangerCreditOverride()).map(Credit::new).orElse(null),
                        Optional.ofNullable(e.getLinkUrl()).map(Url::new).orElse(null)))
                .orElse(null);
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
        return Optional.ofNullable(trackTune).map(tt -> {
            var trackTuneEntity = new TrackTuneEntity();
            trackTuneEntity.setId(new TrackTuneId(trackEntity.getTrackId(), tt.seq()));
            trackTuneEntity.setTrack(trackEntity);
            trackTuneEntity.setTuneId(Optional.ofNullable(tt.tuneId()).map(Tune.Id::value).orElse(null));
            trackTuneEntity.setComposerCreditOverride(
                    Optional.ofNullable(tt.composerCreditOverride()).map(Credit::value).orElse(null));
            trackTuneEntity.setArrangerCreditOverride(
                    Optional.ofNullable(tt.arrangerCreditOverride()).map(Credit::value).orElse(null));
            trackTuneEntity.setLinkUrl(Optional.ofNullable(tt.linkUrl()).map(Url::value).orElse(null));
            return trackTuneEntity;
        }).orElse(null);
    }
}
