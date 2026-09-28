package com.abservice.application.query.audio;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.infrastructure.persistence.datasource.ListeningPackageRow;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("配布パッケージの組み立てと版")
class ListeningPackageViewTest {
    private static final UUID FIRST_AUDIO = UUID.fromString("0192f8a0-0000-7000-8000-000000000001");
    private static final UUID SECOND_AUDIO = UUID.fromString("0192f8a0-0000-7000-8000-000000000002");
    private static final String SEPARATOR = " / ";
    private static final Fixture ALBUM_A = new Fixture("album-a", FIRST_AUDIO);
    private static final Fixture ALBUM_B = new Fixture("album-b", SECOND_AUDIO);

    @Test
    @DisplayName("作品順・曲順・登場順を保ち、曲名の無い曲はチューン名から名を組む")
    void assemblesAlbumsTracksAndItems() {
        final var view = ListeningPackageView.of(rows(), SEPARATOR);
        assertThat(view.albums()).extracting(ListeningPackageView.Album::albumId).containsExactly("album-a", "album-b");
        assertThat(view.albums().getFirst().tracks()).extracting(ListeningPackageView.Track::trackNo)
                .containsExactly(1, 2);
        assertThat(view.albums().getFirst().tracks()).extracting(ListeningPackageView.Track::title)
                .containsExactly("Opening", "Reel / Jig");
        assertThat(view.albums().get(1).tracks()).isEmpty();
        assertThat(view.playbackItems()).extracting(ListeningPackageView.Crossfade::playbackItemId)
                .containsExactly("album-crossfade:album-a", "album-crossfade:album-b");
        assertThat(view.playbackItems().getFirst().audioAssetId()).isEqualTo(FIRST_AUDIO);
        assertThat(view.playbackItems().getFirst().durationSeconds()).isEqualTo(2.5);
        assertThat(ListeningPackageView.albumIdOf("album-crossfade:album-a")).contains("album-a");
        assertThat(ListeningPackageView.albumIdOf("track:album-a")).isEmpty();
    }

    @Test
    @DisplayName("同じ実体を参照する作品が複数あっても音源は1件で、版は内容だけで決まる")
    void sharesAssetsAndFingerprintsContent() {
        final var view = ListeningPackageView.of(rows(), SEPARATOR);
        assertThat(view.assets()).extracting(ListeningPackageView.AudioAsset::assetId)
                .containsExactly(FIRST_AUDIO, SECOND_AUDIO);
        assertThat(view.assets().getFirst().mediaType()).isEqualTo("audio/flac");
        assertThat(view.assets().getFirst().checksum())
                .isEqualTo(new ListeningPackageView.Checksum("sha256", "a".repeat(64)));
        assertThat(view.assets().getFirst().required()).isTrue();
        assertThat(view.playbackItems().getFirst().kind()).isEqualTo("album-crossfade");
        assertThat(view.packageVersion()).matches("^[0-9a-f]{64}$")
                .isEqualTo(ListeningPackageView.of(rows(), SEPARATOR).packageVersion());
        final var shared = ListeningPackageView.of(
                rows().stream().map(row -> withAudio(row, FIRST_AUDIO)).toList(),
                SEPARATOR);
        assertThat(shared.assets()).hasSize(1);
        assertThat(shared.packageVersion()).isNotEqualTo(view.packageVersion());
        assertThat(ListeningPackageView.of(List.of(), SEPARATOR).packageVersion())
                .matches("^[0-9a-f]{64}$").isNotEqualTo(view.packageVersion());
    }

    @Test
    @DisplayName("版は Manifest の内容全体を表し、schema の版や互換範囲が変わっても別の版になる")
    void fingerprintsContractAsWell() {
        final var current = ListeningPackageView.of(rows(), SEPARATOR);
        assertThat(current.schemaVersion()).isEqualTo(2);
        assertThat(current.compatibleAppVersion()).isEqualTo(
                new ListeningPackageView.AppVersionRange(
                        List.of(
                                1,
                                10,
                                0),
                        List.of(
                                2,
                                0,
                                0)));
        assertThat(current.presentationAssetIds()).isEmpty();
        final var nextSchema = ListeningPackageView.of(
                new ListeningPackageView.Contract(3, current.compatibleAppVersion()),
                rows(),
                SEPARATOR);
        final var widerRange = ListeningPackageView.of(
                new ListeningPackageView.Contract(
                        current.schemaVersion(),
                        new ListeningPackageView.AppVersionRange(
                                current.compatibleAppVersion().minInclusive(),
                                List.of(
                                        3,
                                        0,
                                        0))),
                rows(),
                SEPARATOR);
        assertThat(nextSchema.albums()).isEqualTo(current.albums());
        assertThat(nextSchema.packageVersion()).isNotEqualTo(current.packageVersion());
        assertThat(widerRange.packageVersion()).isNotIn(current.packageVersion(), nextSchema.packageVersion());
    }

    private static List<ListeningPackageRow> rows() {
        return List.of(
                track(
                        ALBUM_A,
                        "track-1",
                        "Opening",
                        1,
                        "Opening tune"),
                track(
                        ALBUM_A,
                        "track-2",
                        null,
                        1,
                        "Reel"),
                track(
                        ALBUM_A,
                        "track-2",
                        null,
                        2,
                        null),
                track(
                        ALBUM_A,
                        "track-2",
                        null,
                        3,
                        "Jig"),
                withoutTracks(ALBUM_B));
    }

    /** 曲番号はIDの末尾の数字から取る。 */
    private static ListeningPackageRow track(
            Fixture album,
            String trackId,
            @Nullable String trackTitle,
            int tuneSeq,
            @Nullable String tuneTitle) {
        return new ListeningPackageRow(
                album.albumId(),
                album.title(),
                album.audioId(),
                110_250L,
                "a".repeat(64),
                44_100,
                110_250L,
                trackId,
                Integer.parseInt(trackId.substring(trackId.length() - 1)),
                trackTitle,
                tuneSeq,
                tuneTitle);
    }

    private static ListeningPackageRow withoutTracks(Fixture album) {
        return new ListeningPackageRow(
                album.albumId(),
                album.title(),
                album.audioId(),
                110_250L,
                "a".repeat(64),
                44_100,
                110_250L,
                null,
                null,
                null,
                null,
                null);
    }

    private static ListeningPackageRow withAudio(ListeningPackageRow row, UUID audioId) {
        return new ListeningPackageRow(
                row.albumId(),
                row.albumTitle(),
                audioId,
                row.byteLength(),
                row.sha256(),
                row.sampleRate(),
                row.totalSamples(),
                row.trackId(),
                row.trackNo(),
                row.trackTitle(),
                row.tuneSeq(),
                row.tuneTitle());
    }

    private record Fixture(String albumId, UUID audioId) {
        private String title() {
            return "Title of " + albumId;
        }
    }
}
