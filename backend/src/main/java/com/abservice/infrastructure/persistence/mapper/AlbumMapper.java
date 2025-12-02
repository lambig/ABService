package com.abservice.infrastructure.persistence.mapper;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.model.aggregate.album.TrackTune;
import com.abservice.domain.model.aggregate.artistcredit.ArtistCredit;
import com.abservice.domain.model.aggregate.event.Event;
import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.CatalogNumber;
import com.abservice.domain.model.vo.album.Duration;
import com.abservice.domain.model.vo.album.Isrc;
import com.abservice.domain.model.vo.album.TrackTitle;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.infrastructure.persistence.entity.AlbumEntity;
import com.abservice.infrastructure.persistence.entity.TrackEntity;
import com.abservice.infrastructure.persistence.entity.TrackTuneEntity;
import com.abservice.infrastructure.persistence.entity.TrackTuneId;

import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Album Mapper
 *
 * <p>
 * AlbumドメインモデルとAlbumEntityの相互変換を担当します。
 * </p>
 */
public class AlbumMapper {

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
        if (entity == null) {
            return null;
        }

        var tracks = entity.getTracks() != null
                ? entity.getTracks().stream().map(AlbumMapper::trackToDomain).collect(Collectors.toList())
                : Collections.<Track>emptyList();

        return new Album(new Album.Id(entity.getDomainId()), new AlbumTitle(entity.getTitle()), entity.getReleaseDate(),
                new ArtistCredit.Id(entity.getArtistCreditId()),
                entity.getEventId() != null ? new Event.Id(entity.getEventId()) : null,
                entity.getCatalogNumber() != null ? new CatalogNumber(entity.getCatalogNumber()) : null, tracks);
    }

    /**
     * DomainモデルからEntityへ変換
     *
     * @param album
     *            Album
     * @return AlbumEntity
     */
    public static AlbumEntity toEntity(Album album) {
        if (album == null) {
            return null;
        }

        var albumEntity = new AlbumEntity();
        albumEntity.setDomainId(album.id().value());
        albumEntity.setTitle(album.title().value());
        albumEntity.setReleaseDate(album.releaseDate());
        albumEntity.setArtistCreditId(album.artistCreditId().value());
        albumEntity.setEventId(album.eventId() != null ? album.eventId().value() : null);
        albumEntity.setCatalogNumber(album.catalogNumber() != null ? album.catalogNumber().value() : null);

        // トラックを変換
        if (album.tracks() != null && !album.tracks().isEmpty()) {
            var trackEntities = album.tracks().stream().map(track -> trackToEntity(track, albumEntity))
                    .collect(Collectors.toList());
            albumEntity.setTracks(trackEntities);
        }

        return albumEntity;
    }

    /**
     * TrackEntityからTrackドメインモデルへ変換
     *
     * @param entity
     *            TrackEntity
     * @return Track
     */
    private static Track trackToDomain(TrackEntity entity) {
        if (entity == null) {
            return null;
        }

        var trackTunes = entity.getTrackTunes() != null
                ? entity.getTrackTunes().stream().map(AlbumMapper::trackTuneToDomain).collect(Collectors.toList())
                : Collections.<TrackTune>emptyList();

        return new Track(new Track.Id(entity.getDomainId()), entity.getTrackNo(), new TrackTitle(entity.getTitle()),
                entity.getArtistCreditId() != null ? new ArtistCredit.Id(entity.getArtistCreditId()) : null,
                entity.getRecordingDate(), entity.getRecordingPlace(),
                entity.getDurationMsec() != null ? new Duration(entity.getDurationMsec()) : null, entity.getIsLive(),
                entity.getIsrc() != null ? new Isrc(entity.getIsrc()) : null, trackTunes);
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
        if (track == null) {
            return null;
        }

        var trackEntity = new TrackEntity();
        trackEntity.setDomainId(track.id().value());
        trackEntity.setAlbum(albumEntity);
        trackEntity.setTrackNo(track.trackNo());
        trackEntity.setTitle(track.title().value());
        trackEntity.setArtistCreditId(track.artistCreditId() != null ? track.artistCreditId().value() : null);
        trackEntity.setRecordingDate(track.recordingDate());
        trackEntity.setRecordingPlace(track.recordingPlace());
        trackEntity.setDurationMsec(track.duration() != null ? track.duration().milliseconds() : null);
        trackEntity.setIsLive(track.isLive());
        trackEntity.setIsrc(track.isrc() != null ? track.isrc().value() : null);

        // TrackTunesを変換
        if (track.tunes() != null && !track.tunes().isEmpty()) {
            var trackTuneEntities = track.tunes().stream().map(trackTune -> trackTuneToEntity(trackTune, trackEntity))
                    .collect(Collectors.toList());
            trackEntity.setTrackTunes(trackTuneEntities);
        }

        return trackEntity;
    }

    /**
     * TrackTuneEntityからTrackTuneドメインモデルへ変換
     *
     * @param entity
     *            TrackTuneEntity
     * @return TrackTune
     */
    private static TrackTune trackTuneToDomain(TrackTuneEntity entity) {
        if (entity == null) {
            return null;
        }

        return new TrackTune(entity.getId().getSeq(),
                entity.getTuneId() != null ? new Tune.Id(entity.getTuneId()) : null,
                entity.getComposerCreditOverride() != null ? new Credit(entity.getComposerCreditOverride()) : null,
                entity.getArrangerCreditOverride() != null ? new Credit(entity.getArrangerCreditOverride()) : null,
                entity.getLinkUrl() != null ? new Url(entity.getLinkUrl()) : null);
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
        if (trackTune == null) {
            return null;
        }

        var trackTuneEntity = new TrackTuneEntity();
        var id = new TrackTuneId(trackEntity.getTrackId(), trackTune.seq());
        trackTuneEntity.setId(id);
        trackTuneEntity.setTrack(trackEntity);
        trackTuneEntity.setTuneId(trackTune.tuneId() != null ? trackTune.tuneId().value() : null);
        trackTuneEntity.setComposerCreditOverride(
                trackTune.composerCreditOverride() != null ? trackTune.composerCreditOverride().value() : null);
        trackTuneEntity.setArrangerCreditOverride(
                trackTune.arrangerCreditOverride() != null ? trackTune.arrangerCreditOverride().value() : null);
        trackTuneEntity.setLinkUrl(trackTune.linkUrl() != null ? trackTune.linkUrl().value() : null);

        return trackTuneEntity;
    }
}
