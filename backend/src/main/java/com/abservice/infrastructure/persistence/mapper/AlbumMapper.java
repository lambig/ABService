package com.abservice.infrastructure.persistence.mapper;

import static com.abservice.lib.Iterables.toList;
import static java.util.function.Predicate.not;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.ExternalAudio;
import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.model.aggregate.album.TrackTune;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.CatalogNumber;
import com.abservice.domain.model.vo.album.Isdn;
import com.abservice.domain.model.vo.album.Publication;
import com.abservice.domain.model.vo.album.TrackTitle;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.AssetKey;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.common.EventReleasedAt;
import com.abservice.domain.model.vo.common.ExternalAudioUrl;
import com.abservice.domain.model.vo.common.MarkupContent;
import com.abservice.domain.model.vo.common.MarkupFormat;
import com.abservice.domain.model.vo.common.Url;
import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.infrastructure.persistence.entity.AlbumExternalAudioTableRecord;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import com.abservice.infrastructure.persistence.entity.TrackTableRecord;
import com.abservice.infrastructure.persistence.entity.TrackTuneTableRecord;
import com.abservice.infrastructure.persistence.entity.TrackTuneId;

import java.util.ArrayList;
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
                buildDescription(entity),
                buildEventReleasedAt(entity),
                Optional.ofNullable(entity.getCatalogNumber())
                        .map(CatalogNumber::new)
                        .orElse(null),
                Optional.ofNullable(entity.getIsdn())
                        .map(Isdn::new)
                        .orElse(null),
                Optional.ofNullable(entity.getCoverImageKey())
                        .map(AssetKey::new)
                        .orElse(null),
                buildPublication(entity),
                buildTracks(entity),
                buildExternalAudios(entity));
    }

    private static ArtistCredit buildArtistCredit(AlbumTableRecord entity) {
        return ArtistCredit.of(entity.getArtistDisplayName(), entity.getArtistSortKey());
    }

    /*
     * 説明本文がNULLの行は説明なしとして扱う。形式列はNOT NULLだが、既存行の既定値で埋まっているだけの 場合があるため本文の有無で判定する。
     */
    private static MarkupContent buildDescription(AlbumTableRecord entity) {
        return Optional.ofNullable(entity.getDescription())
                .map(content -> new MarkupContent(content, MarkupFormat.orDefault(entity.getDescriptionFormat())))
                .orElse(MarkupContent.EMPTY);
    }

    private static List<Track> buildTracks(AlbumTableRecord entity) {
        return Optional.ofNullable(entity.getTracks())
                .map(toList(AlbumMapper::trackToDomain))
                .orElseGet(Collections::emptyList);
    }

    private static List<ExternalAudio> buildExternalAudios(AlbumTableRecord entity) {
        return Optional.ofNullable(entity.getExternalAudios())
                .map(toList(AlbumMapper::externalAudioToDomain))
                .orElseGet(Collections::emptyList);
    }

    private static Publication buildPublication(AlbumTableRecord entity) {
        return Optional.ofNullable(entity.getPublishedAt())
                .map(publishedAt -> Publication.published(BusinessDateTime.of(publishedAt)))
                .orElseGet(Publication::draft);
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
        setDescriptionFields(albumEntity, album.description());
        Optional.ofNullable(album.eventReleasedAt())
                .ifPresent(event -> populateEventFields(albumEntity, event));
        setCatalogFields(albumEntity, album);
        setPublicationField(albumEntity, album.publication());
        setTracksField(albumEntity, album);
        setExternalAudiosField(albumEntity, album);
        return albumEntity;
    }

    private static void setArtistCreditFields(AlbumTableRecord entity, ArtistCredit credit) {
        entity.setArtistDisplayName(credit.displayName().value())
                .setArtistSortKey(credit.sortKey());
    }

    /*
     * 説明が空へ戻った場合に既存の本文を確実に消すため、公開情報と同じく常に値を設定する。 形式列はNOT
     * NULLのため、説明なしのときも形式は既定値を書き込む。
     */
    private static void setDescriptionFields(AlbumTableRecord entity, MarkupContent description) {
        entity.setDescription(
                Optional.of(description)
                        .filter(not(MarkupContent::isEmpty))
                        .map(MarkupContent::content)
                        .orElse(null))
                .setDescriptionFormat(description.format().name());
    }

    private static void setCatalogFields(AlbumTableRecord entity, Album album) {
        entity.setCatalogNumber(
                Optional.ofNullable(album.catalogNumber())
                        .map(CatalogNumber::value)
                        .orElse(null))
                .setIsdn(
                        Optional.ofNullable(album.isdn())
                                .map(Isdn::value)
                                .orElse(null))
                .setCoverImageKey(
                        Optional.ofNullable(album.coverImageKey())
                                .map(AssetKey::value)
                                .orElse(null));
    }

    /**
     * 公開情報を反映する
     *
     * <p>
     * {@code publication}がDraftへ戻った場合に既存の{@code published_at}を確実にnullへ戻すため、
     * 他フィールドの{@code ifPresent}方式ではなく常に値を設定する（値が無ければnullを設定する）。
     * </p>
     *
     * @param entity
     *            反映先のAlbumTableRecord
     * @param publication
     *            アルバム集約が保持する公開情報
     */
    private static void setPublicationField(AlbumTableRecord entity, Publication publication) {
        entity.setPublishedAt(
                publication.publishedAt()
                        .map(BusinessDateTime::value)
                        .orElse(null));
    }

    private static void setTracksField(AlbumTableRecord entity, Album album) {
        Optional.ofNullable(album.tracks())
                .filter(not(List::isEmpty))
                .map(toList(track -> trackToEntity(track, entity)))
                /*
                 * MUTABLE-COLLECTION: toList()の不変Listをそのままセットすると、後続の再保存時に
                 * orphanRemoval下のインプレース差分反映（AlbumRepositoryImpl.reconcileTracks）が
                 * removeIf/addで失敗する（#90）ため、Hibernate管理コレクションの初期値は可変にする。
                 */
                .map(ArrayList::new)
                .ifPresent(entity::setTracks);
    }

    private static void setExternalAudiosField(AlbumTableRecord entity, Album album) {
        Optional.ofNullable(album.externalAudios())
                .filter(not(List::isEmpty))
                .map(toList(externalAudio -> externalAudioToEntity(externalAudio, entity)))
                // MUTABLE-COLLECTION: setTracksFieldと同じ理由（#90）
                .map(ArrayList::new)
                .ifPresent(entity::setExternalAudios);
    }

    /**
     * AlbumExternalAudioTableRecordからExternalAudioドメインモデルへ変換
     *
     * @param entity
     *            AlbumExternalAudioTableRecord
     * @return ExternalAudio
     */
    private static ExternalAudio externalAudioToDomain(AlbumExternalAudioTableRecord entity) {
        return ExternalAudio.reconstruct(
                new ExternalAudio.Id(entity.getDomainId()),
                entity.getDisplayOrder(),
                ExternalAudioUrl.of(entity.getUrl()));
    }

    /**
     * ExternalAudioドメインモデルからAlbumExternalAudioTableRecordへ変換
     *
     * @param externalAudio
     *            ExternalAudio
     * @param albumEntity
     *            親のAlbumTableRecord
     * @return AlbumExternalAudioTableRecord
     */
    public static AlbumExternalAudioTableRecord externalAudioToEntity(
            ExternalAudio externalAudio,
            AlbumTableRecord albumEntity) {
        return new AlbumExternalAudioTableRecord()
                .setDomainId(externalAudio.id().value())
                .setAlbum(albumEntity)
                .setDisplayOrder(externalAudio.displayOrder())
                .setUrl(externalAudio.url().value().value());
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
    public static TrackTableRecord trackToEntity(Track track, AlbumTableRecord albumEntity) {
        final var trackEntity = new TrackTableRecord()
                .setDomainId(track.id().value())
                .setAlbum(albumEntity)
                .setTrackNo(track.trackNo())
                .setTitle(track.title().value());
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
                // MUTABLE-COLLECTION: setTracksFieldと同じ理由（#90）
                .map(ArrayList::new)
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
    public static TrackTuneTableRecord trackTuneToEntity(TrackTune trackTune, TrackTableRecord trackEntity) {
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
