package com.abservice.application.service.album;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.model.aggregate.album.TrackTune;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.TrackTitle;
import com.abservice.domain.model.vo.album.TrackTuneTitle;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.MarkupContent;
import com.abservice.infrastructure.persistence.repository.AlbumRepositoryImpl;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Albumのトラック追加・更新・削除・順序変更ユースケース（#146）の統合テスト
 *
 * <p>
 * {@code tunes}（トラック内チューン構成）が更新・順序変更で維持されることは、REST契約（追加・更新・削除の疎通、
 * 検証エラー・対象不在・番号重複時のステータスコード）を検証する {@code AlbumTrackRestIntegrationTest}
 * では確認できない（REST未提供のチューン追加を前提とするため）ため、ここではリポジトリを直接使って前提データを組み立てる。
 * </p>
 */
@QuarkusTest
class TrackCommandServiceIntegrationTest {

    @Inject
    private AddTrackService addTrackService;

    @Inject
    private UpdateTrackService updateTrackService;

    @Inject
    private RemoveTrackService removeTrackService;

    @Inject
    private ReorderTracksService reorderTracksService;

    @Inject
    private AlbumRepositoryImpl albumRepository;

    private static Album newAlbum(String title) {
        return Album.create(
                new AlbumTitle(title),
                BusinessDate.of(
                        LocalDate.of(
                                2026,
                                1,
                                1)),
                ArtistCredit.of("Track Test Artist"),
                MarkupContent.EMPTY,
                null,
                null,
                null,
                null);
    }

    private static Track newTrack(int trackNo, String title) {
        return Track.create(
                trackNo,
                TrackTitle.of(title),
                null);
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldAddTrackAndPersistIt(UniAsserter asserter) {
        final var album = newAlbum("Add Track Album");
        asserter.execute(() -> albumRepository.save(album));

        asserter.assertThat(
                () -> addTrackService.execute(
                        new AddTrackInput(
                                album.id().value(),
                                1,
                                "1曲目",
                                null,
                                null,
                                null)),
                output -> {
                    assertThat(output.albumId()).isEqualTo(album.id().value());
                    assertThat(output.trackNo()).isEqualTo(1);
                    assertThat(output.title()).isEqualTo("1曲目");
                });

        asserter.assertThat(
                () -> albumRepository.findById(album.id()),
                found -> assertThat(found.getTracks()).hasSize(1));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFailToAddTrackWithDuplicateTrackNo(UniAsserter asserter) {
        final var album = newAlbum("Duplicate Track No Album")
                .addTrack(newTrack(1, "既存トラック"));
        asserter.execute(() -> albumRepository.save(album));

        asserter.assertFailedWith(
                () -> addTrackService.execute(
                        new AddTrackInput(
                                album.id().value(),
                                1,
                                "重複トラック",
                                null,
                                null,
                                null)),
                BusinessRuleViolationException.class);
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFailToAddTrackWhenAlbumNotFound(UniAsserter asserter) {
        asserter.assertFailedWith(
                () -> addTrackService.execute(
                        new AddTrackInput(
                                Album.Id.generate().value(),
                                1,
                                "1曲目",
                                null,
                                null,
                                null)),
                EntityNotFoundException.class);
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldUpdateTrackAndReplaceTunes(UniAsserter asserter) {
        final var trackWithoutTune = newTrack(1, "更新前タイトル");
        final var albumWithoutTune = newAlbum("Update Track Album").addTrack(trackWithoutTune);
        asserter.execute(() -> albumRepository.save(albumWithoutTune));

        final var existingTrack = trackWithoutTune.addTune(
                TrackTune.create(
                        1,
                        null,
                        TrackTuneTitle.of("更新前チューン"),
                        null,
                        null,
                        null));
        final var album = albumWithoutTune.updateTrack(existingTrack);
        asserter.execute(() -> albumRepository.save(album));

        asserter.assertThat(
                () -> updateTrackService.execute(
                        new UpdateTrackInput(
                                album.id().value(),
                                existingTrack.id().value(),
                                2,
                                "更新後タイトル",
                                "更新後アーティスト",
                                null,
                                List.of(
                                        new TrackTuneInput(
                                                1,
                                                "更新後チューン1",
                                                "Trad.",
                                                null,
                                                null),
                                        new TrackTuneInput(
                                                2,
                                                "更新後チューン2",
                                                null,
                                                null,
                                                null)))),
                output -> {
                    assertThat(output.trackNo()).isEqualTo(2);
                    assertThat(output.title()).isEqualTo("更新後タイトル");
                });

        asserter.assertThat(
                () -> albumRepository.findById(album.id()),
                found -> {
                    final var updated = found.getTrack(existingTrack.id());
                    assertThat(updated.trackNo()).isEqualTo(2);
                    assertThat(updated.getTunes()).hasSize(2);
                    assertThat(updated.getTunes().getFirst().tuneTitle().value()).isEqualTo("更新後チューン1");
                    assertThat(updated.getTunes().getFirst().composerCreditOverride().value()).isEqualTo("Trad.");
                    assertThat(updated.getTunes().getLast().tuneTitle().value()).isEqualTo("更新後チューン2");
                });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFailToUpdateTrackWhenTrackNotFound(UniAsserter asserter) {
        final var album = newAlbum("Update Not Found Album");
        asserter.execute(() -> albumRepository.save(album));

        asserter.assertFailedWith(
                () -> updateTrackService.execute(
                        new UpdateTrackInput(
                                album.id().value(),
                                Track.Id.generate().value(),
                                1,
                                "タイトル",
                                null,
                                null,
                                null)),
                BusinessRuleViolationException.class);
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldRemoveTrack(UniAsserter asserter) {
        final var existingTrack = newTrack(1, "削除対象");
        final var album = newAlbum("Remove Track Album").addTrack(existingTrack);
        asserter.execute(() -> albumRepository.save(album));

        asserter.execute(
                () -> removeTrackService.execute(
                        new RemoveTrackInput(
                                album.id().value(),
                                existingTrack.id().value())));

        asserter.assertThat(
                () -> albumRepository.findById(album.id()),
                found -> assertThat(found.getTracks()).isEmpty());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFailToRemoveTrackWhenNotFound(UniAsserter asserter) {
        final var album = newAlbum("Remove Not Found Album");
        asserter.execute(() -> albumRepository.save(album));

        asserter.assertFailedWith(
                () -> removeTrackService.execute(
                        new RemoveTrackInput(
                                album.id().value(),
                                Track.Id.generate().value())),
                BusinessRuleViolationException.class);
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldReorderTracksWhilePreservingTunes(UniAsserter asserter) {
        final var track1WithoutTune = newTrack(1, "1曲目");
        final var track2 = newTrack(2, "2曲目");
        final var albumWithoutTune = newAlbum("Reorder Track Album")
                .addTrack(track1WithoutTune)
                .addTrack(track2);
        asserter.execute(() -> albumRepository.save(albumWithoutTune));

        final var track1 = track1WithoutTune.addTune(
                TrackTune.create(
                        1,
                        null,
                        null,
                        null,
                        null,
                        null));
        final var album = albumWithoutTune.updateTrack(track1);
        asserter.execute(() -> albumRepository.save(album));

        asserter.assertThat(
                () -> reorderTracksService.execute(
                        new ReorderTracksInput(
                                album.id().value(),
                                List.of(
                                        track2.id().value(),
                                        track1.id().value()))),
                output -> {
                    assertThat(output.tracks().get(0).trackId()).isEqualTo(track2.id().value());
                    assertThat(output.tracks().get(0).trackNo()).isEqualTo(1);
                    assertThat(output.tracks().get(1).trackId()).isEqualTo(track1.id().value());
                    assertThat(output.tracks().get(1).trackNo()).isEqualTo(2);
                });

        asserter.assertThat(
                () -> albumRepository.findById(album.id()),
                found -> {
                    final var reorderedTrack1 = found.getTrack(track1.id());
                    assertThat(reorderedTrack1.trackNo()).isEqualTo(2);
                    assertThat(reorderedTrack1.getTunes()).hasSize(1);
                    assertThat(reorderedTrack1.getTunes().getFirst()).isEqualTo(track1.getTunes().getFirst());
                });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFailToReorderWhenSizeMismatch(UniAsserter asserter) {
        final var track1 = newTrack(1, "1曲目");
        final var album = newAlbum("Reorder Size Mismatch Album").addTrack(track1);
        asserter.execute(() -> albumRepository.save(album));

        asserter.assertFailedWith(
                () -> reorderTracksService.execute(
                        new ReorderTracksInput(
                                album.id().value(),
                                List.of(
                                        track1.id().value(),
                                        Track.Id.generate().value()))),
                BusinessRuleViolationException.class);
    }
}
