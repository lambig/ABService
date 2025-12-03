package com.abservice.infrastructure.persistence.mapper;

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
        if (entity == null) {
            return null;
        }

        var tracks = entity.getTracks() != null
                ? entity.getTracks().stream().map(AlbumMapper::trackToDomain).collect(Collectors.toList())
                : Collections.<Track>emptyList();

        // ArtistCredit (VO) を構築
        var artistCredit = new ArtistCredit(new ArtistCreditName(entity.getArtistDisplayName()),
                entity.getArtistSortKey());

        // EventReleasedAt (VO) を構築
        EventReleasedAt eventReleasedAt = null;
        if (entity.getEventName() != null) {
            // 新しいテーブルから日付・スペース情報を取得
            List<EventDateAndSpace> dateAndSpaces = entity.getEventDateSpaces() != null
                    ? entity.getEventDateSpaces().stream()
                            .map(e -> new EventDateAndSpace(BusinessDate.of(e.getEventDate()), e.getSpaceNumber()))
                            .collect(Collectors.toList())
                    : null;

            // 後方互換性：新テーブルにデータがない場合は古いカラムから取得
            if ((dateAndSpaces == null || dateAndSpaces.isEmpty()) && entity.getEventDate() != null) {
                dateAndSpaces = List.of(
                        new EventDateAndSpace(BusinessDate.of(entity.getEventDate()), entity.getEventSpaceNumber()));
            }

            eventReleasedAt = new EventReleasedAt(new EventName(entity.getEventName()), dateAndSpaces,
                    entity.getEventPlace(), entity.getEventNote());
        }

        return Album.reconstruct(new Album.Id(entity.getDomainId()), new AlbumTitle(entity.getTitle()),
                BusinessDate.of(entity.getReleaseDate()), artistCredit, eventReleasedAt,
                entity.getCatalogNumber() != null ? new CatalogNumber(entity.getCatalogNumber()) : null,
                entity.getIsdn() != null ? new Isdn(entity.getIsdn()) : null, tracks);
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
        albumEntity.setReleaseDate(album.releaseDate().asLocalDate());

        // ArtistCredit (VO) を分解
        albumEntity.setArtistDisplayName(album.artistCredit().displayName().value());
        albumEntity.setArtistSortKey(album.artistCredit().sortKey());

        // EventReleasedAt (VO) を分解
        if (album.eventReleasedAt() != null) {
            albumEntity.setEventName(album.eventReleasedAt().name().value());
            albumEntity.setEventPlace(album.eventReleasedAt().place());
            albumEntity.setEventNote(album.eventReleasedAt().note());

            // 日付・スペース情報を新テーブルに保存
            if (album.eventReleasedAt().dateAndSpaces() != null && !album.eventReleasedAt().dateAndSpaces().isEmpty()) {
                var dateSpaceEntities = album.eventReleasedAt().dateAndSpaces().stream().map(ds -> {
                    var entity = new AlbumEventDateSpaceEntity();
                    entity.setAlbum(albumEntity);
                    entity.setEventDate(ds.date().asLocalDate());
                    entity.setSpaceNumber(ds.spaceNumber());
                    return entity;
                }).collect(Collectors.toList());
                albumEntity.setEventDateSpaces(dateSpaceEntities);

                // 後方互換性：最初の日付・スペースを古いカラムにも保存
                var firstDateSpace = album.eventReleasedAt().dateAndSpaces().get(0);
                albumEntity.setEventDate(firstDateSpace.date().asLocalDate());
                albumEntity.setEventSpaceNumber(firstDateSpace.spaceNumber());
            }
        }

        albumEntity.setCatalogNumber(album.catalogNumber() != null ? album.catalogNumber().value() : null);
        albumEntity.setIsdn(album.isdn() != null ? album.isdn().value() : null);

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

        // ArtistCredit (VO) を構築 - nullの場合はAlbumから継承
        ArtistCredit artistCredit = null;
        if (entity.getArtistDisplayName() != null) {
            artistCredit = new ArtistCredit(new ArtistCreditName(entity.getArtistDisplayName()),
                    entity.getArtistSortKey());
        }

        return Track.reconstruct(new Track.Id(entity.getDomainId()), entity.getTrackNo(),
                new TrackTitle(entity.getTitle()), artistCredit,
                entity.getRecordingDate() != null ? BusinessDate.of(entity.getRecordingDate()) : null,
                entity.getRecordingPlace(), entity.getIsLive(), trackTunes);
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

        // ArtistCredit (VO) を分解
        if (track.artistCredit() != null) {
            trackEntity.setArtistDisplayName(track.artistCredit().displayName().value());
            trackEntity.setArtistSortKey(track.artistCredit().sortKey());
        }

        trackEntity.setRecordingDate(track.recordingDate() != null ? track.recordingDate().asLocalDate() : null);
        trackEntity.setRecordingPlace(track.recordingPlace());
        trackEntity.setIsLive(track.isLive());

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

        return TrackTune.reconstruct(entity.getId().getSeq(),
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
