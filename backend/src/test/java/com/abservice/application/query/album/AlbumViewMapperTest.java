package com.abservice.application.query.album;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.abservice.application.query.album.model.AlbumView.ExternalAudioView;
import com.abservice.application.query.album.model.AlbumView.TrackTuneView;
import com.abservice.application.query.album.model.AlbumView.TrackView;
import com.abservice.infrastructure.persistence.datasource.AlbumExternalAudioRow;
import com.abservice.infrastructure.persistence.datasource.AlbumTrackRow;
import com.abservice.infrastructure.persistence.datasource.AlbumTrackTuneRow;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AlbumViewMapper（Entity→Read Model 変換）のテスト")
class AlbumViewMapperTest {

    private static final String ASSET_BASE_PATH = "/assets";

    @Test
    @DisplayName("全項目が Read Model に写像される")
    void toViewShouldMapAllFields() {
        // Arrange
        final var entity = new AlbumTableRecord();
        entity.setDomainId("0192f8a0-0000-7000-8000-000000000000");
        entity.setTitle("アルバムタイトル");
        entity.setReleaseDate(
                LocalDate.of(
                        2026,
                        1,
                        1));
        entity.setArtistDisplayName("アーティスト名");
        entity.setArtistSortKey("artist-sort-key");
        entity.setCatalogNumber("ABC-0001");
        entity.setIsdn("2784702901978");
        entity.setEventName("コミックマーケット104");
        entity.setEventDate(
                LocalDate.of(
                        2026,
                        1,
                        1));
        entity.setEventPlace("東京ビッグサイト");
        entity.setEventSpaceNumber("東ホ-01a");
        entity.setEventNote("新譜あります");
        entity.setCoverImageKey("01a0233d-d25a-7c3b-924f-236ee154fecc.png");

        // Act
        final var view = AlbumViewMapper.toView(
                entity,
                ASSET_BASE_PATH,
                List.of(
                        new AlbumExternalAudioRow(
                                1L,
                                "0192f8a0-0000-7000-8000-0000000000a2",
                                2,
                                "https://soundcloud.com/example/second"),
                        new AlbumExternalAudioRow(
                                1L,
                                "0192f8a0-0000-7000-8000-0000000000a1",
                                1,
                                "https://soundcloud.com/example/first")),
                List.of());

        // Assert
        assertThat(view.albumId()).isEqualTo("0192f8a0-0000-7000-8000-000000000000");
        assertThat(view.title()).isEqualTo("アルバムタイトル");
        assertThat(view.releaseDate()).isEqualTo("2026-01-01");
        assertThat(view.artistDisplayName()).isEqualTo("アーティスト名");
        assertThat(view.artistSortKey()).isEqualTo("artist-sort-key");
        assertThat(view.catalogNumber()).isEqualTo("ABC-0001");
        assertThat(view.isdn()).isEqualTo("2784702901978");
        assertThat(view.eventName()).isEqualTo("コミックマーケット104");
        assertThat(view.eventDate()).isEqualTo("2026-01-01");
        assertThat(view.eventPlace()).isEqualTo("東京ビッグサイト");
        assertThat(view.eventSpaceNumber()).isEqualTo("東ホ-01a");
        assertThat(view.eventNote()).isEqualTo("新譜あります");
        assertThat(view.coverImageUrl()).isEqualTo("/assets/01a0233d-d25a-7c3b-924f-236ee154fecc.png");
        assertThat(view.externalAudios())
                .extracting(
                        ExternalAudioView::externalAudioId,
                        ExternalAudioView::displayOrder,
                        ExternalAudioView::url)
                .containsExactly(
                        tuple(
                                "0192f8a0-0000-7000-8000-0000000000a1",
                                1,
                                "https://soundcloud.com/example/first"),
                        tuple(
                                "0192f8a0-0000-7000-8000-0000000000a2",
                                2,
                                "https://soundcloud.com/example/second"));
    }

    @Test
    @DisplayName("nullable 項目が null のエンティティも写像できる")
    void toViewShouldMapNullableFields() {
        // Arrange
        final var entity = new AlbumTableRecord();
        entity.setDomainId("0192f8a0-0000-7000-8000-000000000001");
        entity.setTitle("タイトルのみ");
        entity.setReleaseDate(
                LocalDate.of(
                        2026,
                        2,
                        1));
        entity.setArtistDisplayName("アーティスト名");

        // Act
        final var view = AlbumViewMapper.toView(
                entity,
                ASSET_BASE_PATH,
                List.of(),
                List.of());

        // Assert
        assertThat(view.artistSortKey()).isNull();
        assertThat(view.catalogNumber()).isNull();
        assertThat(view.isdn()).isNull();
        assertThat(view.eventName()).isNull();
        assertThat(view.eventDate()).isNull();
        assertThat(view.eventPlace()).isNull();
        assertThat(view.eventSpaceNumber()).isNull();
        assertThat(view.eventNote()).isNull();
        assertThat(view.coverImageUrl()).isNull();
        assertThat(view.externalAudios()).isEmpty();
        assertThat(view.tracks()).isEmpty();
    }

    @Test
    @DisplayName("トラックはトラック番号の昇順、チューン構成は登場順の昇順で写像する")
    void toTrackViewsShouldSortByTrackNoAndSeq() {
        // Arrange
        final var firstTrackId = "0192f8a0-0000-7000-8000-0000000000b1";
        final var secondTrackId = "0192f8a0-0000-7000-8000-0000000000b2";

        // Act
        final var tracks = AlbumViewMapper.toTrackViews(
                List.of(
                        new AlbumTrackRow(
                                secondTrackId,
                                2,
                                "2曲目",
                                null,
                                null),
                        new AlbumTrackRow(
                                firstTrackId,
                                1,
                                "1曲目",
                                "トラックアーティスト",
                                "track-artist")),
                List.of(
                        new AlbumTrackTuneRow(
                                firstTrackId,
                                2,
                                "チューン2",
                                null,
                                null,
                                null),
                        new AlbumTrackTuneRow(
                                firstTrackId,
                                1,
                                "チューン1",
                                "Trad.",
                                "Arranger",
                                "https://example.com/tune")));

        // Assert
        assertThat(tracks).extracting(TrackView::trackNo, TrackView::title)
                .containsExactly(
                        tuple(1, "1曲目"),
                        tuple(2, "2曲目"));
        assertThat(tracks.getFirst().artistDisplayName()).isEqualTo("トラックアーティスト");
        assertThat(tracks.getFirst().tunes())
                .extracting(
                        TrackTuneView::seq,
                        TrackTuneView::tuneTitle,
                        TrackTuneView::composerCreditOverride,
                        TrackTuneView::arrangerCreditOverride,
                        TrackTuneView::linkUrl)
                .containsExactly(
                        tuple(
                                1,
                                "チューン1",
                                "Trad.",
                                "Arranger",
                                "https://example.com/tune"),
                        tuple(
                                2,
                                "チューン2",
                                null,
                                null,
                                null));
        assertThat(tracks.getLast().tunes()).isEmpty();
    }
}
