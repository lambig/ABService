package com.abservice.infrastructure.persistence.repository;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.CatalogNumber;
import com.abservice.domain.model.vo.album.Isdn;
import com.abservice.domain.model.vo.album.Publication;
import com.abservice.domain.model.vo.album.TrackTitle;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.EventReleasedAt;
import com.abservice.domain.model.vo.common.MarkupContent;
import com.abservice.domain.model.vo.common.MarkupFormat;
import com.abservice.infrastructure.persistence.datasource.AlbumDataSource;
import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * AlbumRepositoryImpl統合テスト
 *
 * <p>
 * Quarkus + Hibernate
 * Reactiveの統合テストでは、{@link RunOnVertxContext}と{@link UniAsserter}を使用します。
 * これにより、リアクティブなデータベース操作が適切なVertxコンテキスト内で実行されます。
 * </p>
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
class AlbumRepositoryImplTest {

    @Inject
    private AlbumRepositoryImpl repository;

    @Inject
    private AlbumDataSource dataSource;

    private ArtistCredit testArtistCredit;
    private BusinessDate testReleaseDate;

    private void initTestData() {
        testArtistCredit = ArtistCredit.of("Test Artist", "test-artist");
        testReleaseDate = BusinessDate.of(
                LocalDate.of(
                        2024,
                        1,
                        1));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldSaveAndFindAlbum(UniAsserter asserter) {
        initTestData();

        final var album = Album
                .create(
                        new AlbumTitle("Test Album"),
                        testReleaseDate,
                        testArtistCredit,
                        MarkupContent.EMPTY,
                        null,
                        null,
                        null,
                        null);

        // Save the album
        asserter.assertThat(() -> repository.save(album), saved -> {
            assertThat(saved).isNotNull();
            assertThat(saved.id()).isEqualTo(album.id());
            assertThat(saved.title().value()).isEqualTo("Test Album");
        });

        // Find the saved album
        asserter.assertThat(() -> repository.findById(album.id()), found -> {
            assertThat(found).isNotNull();
            assertThat(found.id()).isEqualTo(album.id());
            assertThat(found.title().value()).isEqualTo("Test Album");
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldSaveAlbumWithTracks(UniAsserter asserter) {
        initTestData();

        final var track1 = Track.create(
                1,
                new TrackTitle("Track 1"),
                null);
        final var track2 = Track.create(
                2,
                new TrackTitle("Track 2"),
                null);

        final var album = Album
                .create(
                        new AlbumTitle("Album with Tracks"),
                        testReleaseDate,
                        testArtistCredit,
                        MarkupContent.EMPTY,
                        null,
                        null,
                        null,
                        null)
                .addTrack(track1).addTrack(track2);

        asserter.assertThat(() -> repository.save(album), saved -> {
            assertThat(saved.tracks().size()).isEqualTo(2);
            assertThat(saved.tracks().get(0).title().value()).isEqualTo("Track 1");
            assertThat(saved.tracks().get(1).title().value()).isEqualTo("Track 2");
        });
    }

    /**
     * #90: Album再保存のたびにTrackが総入れ替えされ、内部idとcreated_atが再生成されるバグの回帰テスト。
     */
    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldPreserveTrackIdentityAndCreatedAtOnUnrelatedResave(UniAsserter asserter) {
        initTestData();

        final var track = Track.create(
                1,
                new TrackTitle("Track 1"),
                null);
        final var album = Album
                .create(
                        new AlbumTitle("Original Title"),
                        testReleaseDate,
                        testArtistCredit,
                        MarkupContent.EMPTY,
                        null,
                        null,
                        null,
                        null)
                .addTrack(track);

        final Long[] capturedTrackId = new Long[1];
        final Instant[] capturedCreatedAt = new Instant[1];

        asserter.execute(() -> repository.save(album));
        asserter.assertThat(
                () -> dataSource.findByIdWithTracks(album.id().value()),
                entity -> {
                    final var trackEntity = entity.getTracks().get(0);
                    capturedTrackId[0] = trackEntity.getTrackId();
                    capturedCreatedAt[0] = trackEntity.getCreatedAt();
                });

        asserter.execute(() -> repository.save(album.changeTitle(new AlbumTitle("Updated Title"))));
        asserter.assertThat(
                () -> dataSource.findByIdWithTracks(album.id().value()),
                entity -> {
                    final var trackEntity = entity.getTracks().get(0);
                    assertThat(trackEntity.getTrackId()).isEqualTo(capturedTrackId[0]);
                    assertThat(trackEntity.getCreatedAt()).isEqualTo(capturedCreatedAt[0]);
                });
    }

    /**
     * #90の回帰テスト: 既存Trackがある状態で新規Trackを追加して再保存しても、既存Trackの内部id・ created_atは変化しない。
     */
    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldPreserveExistingTrackIdentityWhenAddingAnotherTrackOnResave(UniAsserter asserter) {
        initTestData();

        final var track1 = Track.create(
                1,
                new TrackTitle("Track 1"),
                null);
        final var track2 = Track.create(
                2,
                new TrackTitle("Track 2"),
                null);
        final var album = Album
                .create(
                        new AlbumTitle("Album"),
                        testReleaseDate,
                        testArtistCredit,
                        MarkupContent.EMPTY,
                        null,
                        null,
                        null,
                        null)
                .addTrack(track1);

        final Long[] capturedTrackId = new Long[1];
        final Instant[] capturedCreatedAt = new Instant[1];

        asserter.execute(() -> repository.save(album));
        asserter.assertThat(
                () -> dataSource.findByIdWithTracks(album.id().value()),
                entity -> {
                    final var trackEntity = entity.getTracks().get(0);
                    capturedTrackId[0] = trackEntity.getTrackId();
                    capturedCreatedAt[0] = trackEntity.getCreatedAt();
                });

        asserter.execute(() -> repository.save(album.addTrack(track2)));
        asserter.assertThat(
                () -> dataSource.findByIdWithTracks(album.id().value()),
                entity -> {
                    assertThat(entity.getTracks()).hasSize(2);
                    final var track1Entity = entity.getTracks().stream()
                            .filter(t -> t.getDomainId().equals(track1.id().value()))
                            .findFirst()
                            .orElseThrow();
                    assertThat(track1Entity.getTrackId()).isEqualTo(capturedTrackId[0]);
                    assertThat(track1Entity.getCreatedAt()).isEqualTo(capturedCreatedAt[0]);
                });
    }

    /**
     * #90の回帰テスト: 2件のTrackがある状態で1件削除して再保存しても、残るTrackの内部id・ created_atは変化しない。
     */
    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldPreserveRemainingTrackIdentityWhenRemovingATrackOnResave(UniAsserter asserter) {
        initTestData();

        final var track1 = Track.create(
                1,
                new TrackTitle("Track 1"),
                null);
        final var track2 = Track.create(
                2,
                new TrackTitle("Track 2"),
                null);
        final var album = Album
                .create(
                        new AlbumTitle("Album"),
                        testReleaseDate,
                        testArtistCredit,
                        MarkupContent.EMPTY,
                        null,
                        null,
                        null,
                        null)
                .addTrack(track1).addTrack(track2);

        final Long[] capturedTrackId = new Long[1];
        final Instant[] capturedCreatedAt = new Instant[1];

        asserter.execute(() -> repository.save(album));
        asserter.assertThat(
                () -> dataSource.findByIdWithTracks(album.id().value()),
                entity -> {
                    final var track1Entity = entity.getTracks().stream()
                            .filter(t -> t.getDomainId().equals(track1.id().value()))
                            .findFirst()
                            .orElseThrow();
                    capturedTrackId[0] = track1Entity.getTrackId();
                    capturedCreatedAt[0] = track1Entity.getCreatedAt();
                });

        asserter.execute(() -> repository.save(album.removeTrack(track2.id())));
        asserter.assertThat(
                () -> dataSource.findByIdWithTracks(album.id().value()),
                entity -> {
                    assertThat(entity.getTracks()).hasSize(1);
                    final var remaining = entity.getTracks().get(0);
                    assertThat(remaining.getDomainId()).isEqualTo(track1.id().value());
                    assertThat(remaining.getTrackId()).isEqualTo(capturedTrackId[0]);
                    assertThat(remaining.getCreatedAt()).isEqualTo(capturedCreatedAt[0]);
                });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldSaveAndRestoreAlbumDescription(UniAsserter asserter) {
        initTestData();

        final var album = Album.create(
                new AlbumTitle("Album with Description"),
                testReleaseDate,
                testArtistCredit,
                MarkupContent.markdown("## 概要\n\n往復確認用の説明"),
                null,
                null,
                null,
                null);

        asserter.execute(() -> repository.save(album));

        asserter.assertThat(() -> repository.findById(album.id()), found -> {
            assertThat(found.description().content()).isEqualTo("## 概要\n\n往復確認用の説明");
            assertThat(found.description().format()).isEqualTo(MarkupFormat.MARKDOWN);
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldSaveAlbumWithoutDescriptionAsEmpty(UniAsserter asserter) {
        initTestData();

        final var album = Album.create(
                new AlbumTitle("Album without Description"),
                testReleaseDate,
                testArtistCredit,
                MarkupContent.EMPTY,
                null,
                null,
                null,
                null);

        asserter.execute(() -> repository.save(album));

        asserter.assertThat(
                () -> repository.findById(album.id()),
                found -> assertThat(found.description().isEmpty()).isTrue());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldSaveAlbumWithCatalogNumber(UniAsserter asserter) {
        initTestData();

        final var album = Album.create(
                new AlbumTitle("Album with Catalog"),
                testReleaseDate,
                testArtistCredit,
                MarkupContent.EMPTY,
                null,
                new CatalogNumber("TEST-001"),
                null,
                null);

        asserter.assertThat(() -> repository.save(album), saved -> {
            assertThat(saved.catalogNumber()).isNotNull();
            assertThat(saved.catalogNumber().value()).isEqualTo("TEST-001");
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldSaveAlbumWithIsdn(UniAsserter asserter) {
        initTestData();

        final var album = Album.create(
                new AlbumTitle("Album with ISDN"),
                testReleaseDate,
                testArtistCredit,
                MarkupContent.EMPTY,
                null,
                null,
                new Isdn("2784702901978"),
                null);

        asserter.assertThat(() -> repository.save(album), saved -> {
            assertThat(saved.isdn()).isNotNull();
            assertThat(saved.isdn().value()).isEqualTo("2784702901978");
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldSaveAlbumWithEvent(UniAsserter asserter) {
        initTestData();

        final var eventReleasedAt = EventReleasedAt
                .of(
                        "Test Event",
                        testReleaseDate,
                        "Test Venue",
                        "A-01",
                        "Test Note");

        final var album = Album.create(
                new AlbumTitle("Album with Event"),
                testReleaseDate,
                testArtistCredit,
                MarkupContent.EMPTY,
                eventReleasedAt,
                null,
                null,
                null);

        asserter.assertThat(() -> repository.save(album), saved -> {
            assertThat(saved.eventReleasedAt()).isNotNull();
            assertThat(saved.eventReleasedAt().name().value()).isEqualTo("Test Event");
            assertThat(saved.eventReleasedAt().place()).isEqualTo("Test Venue");
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldUpdateExistingAlbum(UniAsserter asserter) {
        initTestData();

        final var album = Album
                .create(
                        new AlbumTitle("Original Title"),
                        testReleaseDate,
                        testArtistCredit,
                        MarkupContent.EMPTY,
                        null,
                        null,
                        null,
                        null);

        // Save original
        asserter.assertThat(
                () -> repository.save(album),
                saved -> assertThat(saved.title().value()).isEqualTo("Original Title"));

        // Update
        final var updated = Album.reconstruct(
                album.id(),
                new AlbumTitle("Updated Title"),
                testReleaseDate,
                testArtistCredit,
                MarkupContent.EMPTY,
                null,
                null,
                null,
                null,
                Publication.draft(),
                List.of(),
                List.of());

        asserter.assertThat(() -> repository.save(updated), result -> {
            assertThat(result.id()).isEqualTo(album.id());
            assertThat(result.title().value()).isEqualTo("Updated Title");
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldDeleteAlbum(UniAsserter asserter) {
        initTestData();

        final var album = Album
                .create(
                        new AlbumTitle("Album to Delete"),
                        testReleaseDate,
                        testArtistCredit,
                        MarkupContent.EMPTY,
                        null,
                        null,
                        null,
                        null);

        // Save
        asserter.assertThat(() -> repository.save(album), saved -> assertThat(saved).isNotNull());

        // Delete
        asserter.execute(() -> repository.deleteById(album.id()));

        // Verify deletion
        asserter.assertThat(() -> repository.findById(album.id()), found -> assertThat(found).isNull());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldCheckExistence(UniAsserter asserter) {
        initTestData();

        final var album = Album
                .create(
                        new AlbumTitle("Existing Album"),
                        testReleaseDate,
                        testArtistCredit,
                        MarkupContent.EMPTY,
                        null,
                        null,
                        null,
                        null);

        // Save
        asserter.execute(() -> repository.save(album));

        // Check exists
        asserter.assertThat(() -> repository.existsById(album.id()), exists -> assertThat(exists).isTrue());

        // Check non-existent
        final var nonExistentId = new Album.Id("01234567-89ab-7def-0123-456789abcdef");
        asserter.assertThat(() -> repository.existsById(nonExistentId), exists -> assertThat(exists).isFalse());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldCountAlbums(UniAsserter asserter) {
        initTestData();

        final var album1 = Album
                .create(
                        new AlbumTitle("Count Album 1"),
                        testReleaseDate,
                        testArtistCredit,
                        MarkupContent.EMPTY,
                        null,
                        null,
                        null,
                        null);
        final var album2 = Album
                .create(
                        new AlbumTitle("Count Album 2"),
                        testReleaseDate,
                        testArtistCredit,
                        MarkupContent.EMPTY,
                        null,
                        null,
                        null,
                        null);

        // Save albums
        asserter.execute(() -> repository.save(album1));
        asserter.execute(() -> repository.save(album2));

        // Verify count
        asserter.assertThat(() -> repository.count(), count -> assertThat(count >= 2).isTrue());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldHandleNullInputs(UniAsserter asserter) {
        // Save null は @NullMarked 契約違反のためシステムエラー（同期スロー）
        assertThatThrownBy(() -> repository.save(null)).isInstanceOf(NullPointerException.class);

        // Find by null ID should return null
        asserter.assertThat(() -> repository.findById(null), found -> assertThat(found).isNull());

        // Exists with null ID should return false
        asserter.assertThat(() -> repository.existsById(null), exists -> assertThat(exists).isFalse());

        // Delete with null ID should not throw
        asserter.execute(() -> repository.deleteById(null));
    }
}
