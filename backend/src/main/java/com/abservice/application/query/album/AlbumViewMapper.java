package com.abservice.application.query.album;

import com.abservice.application.query.album.model.AlbumView;
import com.abservice.application.query.album.model.AlbumView.ExternalAudioView;
import com.abservice.application.query.album.model.AlbumView.TrackTuneView;
import com.abservice.application.query.album.model.AlbumView.TrackView;
import com.abservice.infrastructure.persistence.datasource.AlbumExternalAudioRow;
import com.abservice.infrastructure.persistence.datasource.AlbumTrackRow;
import com.abservice.infrastructure.persistence.datasource.AlbumTrackTuneRow;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * アルバムエンティティから Read Model（{@link AlbumView}）への変換
 *
 * <p>
 * CQRS の Read 側マッパー。{@code infrastructure.persistence.datasource} が返す
 * {@link AlbumTableRecord} を照会結果 DTO へ平坦化します。ドメインモデルを経由しません。
 * </p>
 */
final class AlbumViewMapper {

    private AlbumViewMapper() {
    }

    /**
     * エンティティを Read Model へ変換します。
     *
     * <p>
     * カバー画像はDBに保管キーだけを持つため、配信URLは {@code assetBasePath} と組み合わせて組み立てます。
     * </p>
     *
     * @param entity
     *            アルバムエンティティ
     * @param assetBasePath
     *            アセットの配信ベースパス（{@code abservice.assets.public-base-path}）
     * @param externalAudios
     *            当該アルバムの外部音源（別クエリで取得した投影）
     * @param tracks
     *            当該アルバムのトラック（詳細照会のみ。一覧照会では空を渡す）
     * @return アルバムの Read Model
     */
    static AlbumView toView(
            AlbumTableRecord entity,
            String assetBasePath,
            List<AlbumExternalAudioRow> externalAudios,
            List<TrackView> tracks) {
        return new AlbumView(
                entity.getDomainId(),
                entity.getTitle(),
                entity.getReleaseDate().toString(),
                entity.getArtistDisplayName(),
                entity.getArtistSortKey(),
                entity.getDescription(),
                entity.getDescriptionFormat(),
                entity.getCatalogNumber(),
                entity.getIsdn(),
                entity.getEventName(),
                toDateString(entity.getEventDate()),
                entity.getEventPlace(),
                entity.getEventSpaceNumber(),
                entity.getEventNote(),
                entity.getPublishedAt(),
                toCoverImageUrl(entity.getCoverImageKey(), assetBasePath),
                toExternalAudioViews(externalAudios),
                tracks);
    }

    /**
     * トラックとチューン構成の投影を Read Model へ組み立てます。
     *
     * <p>
     * チューン構成は所属トラックのドメインIDで突き合わせます（トラック件数分のクエリを避け、まとめて引いた 結果を振り分けるため）。
     * </p>
     *
     * @param tracks
     *            トラックの投影（トラック番号の昇順）
     * @param trackTunes
     *            チューン構成の投影（所属トラックを問わない全件）
     * @return トラックの Read Model のリスト
     */
    static List<TrackView> toTrackViews(List<AlbumTrackRow> tracks, List<AlbumTrackTuneRow> trackTunes) {
        return toTrackViewsWith(tracks, groupByTrackId(trackTunes));
    }

    private static Map<String, List<AlbumTrackTuneRow>> groupByTrackId(List<AlbumTrackTuneRow> trackTunes) {
        return trackTunes.stream().collect(Collectors.groupingBy(AlbumTrackTuneRow::trackId));
    }

    private static List<TrackView> toTrackViewsWith(
            List<AlbumTrackRow> tracks,
            Map<String, List<AlbumTrackTuneRow>> tunesByTrackId) {
        return tracks.stream()
                .sorted(Comparator.comparing(AlbumTrackRow::trackNo))
                .map(track -> toTrackView(track, tunesByTrackId.getOrDefault(track.trackId(), List.of())))
                .toList();
    }

    private static TrackView toTrackView(AlbumTrackRow track, List<AlbumTrackTuneRow> tunes) {
        return new TrackView(
                track.trackId(),
                track.trackNo(),
                track.title(),
                track.artistDisplayName(),
                track.artistSortKey(),
                toTrackTuneViews(tunes));
    }

    private static List<TrackTuneView> toTrackTuneViews(List<AlbumTrackTuneRow> tunes) {
        return tunes.stream()
                .sorted(Comparator.comparing(AlbumTrackTuneRow::seq))
                .map(
                        tune -> new TrackTuneView(
                                tune.seq(),
                                tune.tuneTitle(),
                                tune.composerCreditOverride(),
                                tune.arrangerCreditOverride(),
                                tune.linkUrl()))
                .toList();
    }

    private static List<ExternalAudioView> toExternalAudioViews(List<AlbumExternalAudioRow> externalAudios) {
        return externalAudios.stream()
                .sorted(Comparator.comparing(AlbumExternalAudioRow::displayOrder))
                .map(
                        audio -> new ExternalAudioView(
                                audio.externalAudioId(),
                                audio.displayOrder(),
                                audio.url()))
                .toList();
    }

    private static @Nullable String toCoverImageUrl(@Nullable String coverImageKey, String assetBasePath) {
        return Optional.ofNullable(coverImageKey)
                .map(key -> assetBasePath + "/" + key)
                .orElse(null);
    }

    private static @Nullable String toDateString(@Nullable LocalDate date) {
        return Optional.ofNullable(date)
                .map(LocalDate::toString)
                .orElse(null);
    }
}
