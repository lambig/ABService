package com.abservice.application.query.album;

import com.abservice.application.query.album.model.AlbumView;
import com.abservice.application.query.album.model.AlbumView.ExternalAudioView;
import com.abservice.infrastructure.persistence.datasource.AlbumExternalAudioRow;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
     * @return アルバムの Read Model
     */
    static AlbumView toView(
            AlbumTableRecord entity,
            String assetBasePath,
            List<AlbumExternalAudioRow> externalAudios) {
        return new AlbumView(
                entity.getDomainId(),
                entity.getTitle(),
                entity.getReleaseDate().toString(),
                entity.getArtistDisplayName(),
                entity.getArtistSortKey(),
                entity.getCatalogNumber(),
                entity.getIsdn(),
                entity.getEventName(),
                toDateString(entity.getEventDate()),
                entity.getEventPlace(),
                entity.getEventSpaceNumber(),
                entity.getEventNote(),
                entity.getPublishedAt(),
                toCoverImageUrl(entity.getCoverImageKey(), assetBasePath),
                toExternalAudioViews(externalAudios));
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
