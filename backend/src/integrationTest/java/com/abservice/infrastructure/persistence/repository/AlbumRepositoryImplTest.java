package com.abservice.infrastructure.persistence.repository;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.CatalogNumber;
import com.abservice.domain.model.vo.album.Isdn;
import com.abservice.domain.model.vo.album.TrackTitle;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.EventDateAndSpace;
import com.abservice.domain.model.vo.common.EventReleasedAt;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
class AlbumRepositoryImplTest {

    @Inject
    private AlbumRepositoryImpl repository;

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
                null,
                null,
                null,
                false);
        final var track2 = Track.create(
                2,
                new TrackTitle("Track 2"),
                null,
                null,
                null,
                false);

        final var album = Album
                .create(
                        new AlbumTitle("Album with Tracks"),
                        testReleaseDate,
                        testArtistCredit,
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

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldSaveAlbumWithCatalogNumber(UniAsserter asserter) {
        initTestData();

        final var album = Album.create(
                new AlbumTitle("Album with Catalog"),
                testReleaseDate,
                testArtistCredit,
                null,
                new CatalogNumber("TEST-001"),
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
                null,
                null,
                new Isdn("2784702901978"));

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
                        List.of(EventDateAndSpace.of(testReleaseDate, "A-01")),
                        "Test Venue",
                        "Test Note");

        final var album = Album.create(
                new AlbumTitle("Album with Event"),
                testReleaseDate,
                testArtistCredit,
                eventReleasedAt,
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
                null,
                null,
                null,
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
                        null,
                        null,
                        null);
        final var album2 = Album
                .create(
                        new AlbumTitle("Count Album 2"),
                        testReleaseDate,
                        testArtistCredit,
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
    void shouldFindByTitle(UniAsserter asserter) {
        initTestData();

        final var title = new AlbumTitle("Unique Title for Search");
        final var album = Album.create(
                title,
                testReleaseDate,
                testArtistCredit,
                null,
                null,
                null);

        // Save
        asserter.execute(() -> repository.save(album));

        // Find by title
        asserter.assertThat(() -> repository.findByTitle(title), found -> {
            assertThat(found.size() >= 1).isTrue();
            assertThat(found.stream().anyMatch(a -> a.title().value().equals("Unique Title for Search"))).isTrue();
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByCatalogNumber(UniAsserter asserter) {
        initTestData();

        final var catalogNumber = new CatalogNumber("UNIQUE-CAT-999");
        final var album = Album.create(
                new AlbumTitle("Album with Unique Catalog"),
                testReleaseDate,
                testArtistCredit,
                null,
                catalogNumber,
                null);

        // Save
        asserter.execute(() -> repository.save(album));

        // Find by catalog number
        asserter.assertThat(() -> repository.findByCatalogNumber(catalogNumber), found -> {
            assertThat(found).isNotNull();
            assertThat(found.catalogNumber()).isEqualTo(catalogNumber);
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldHandleNullInputs(UniAsserter asserter) {
        // Save null should fail
        asserter.assertFailedWith(() -> repository.save(null), IllegalArgumentException.class);

        // Find by null ID should return null
        asserter.assertThat(() -> repository.findById(null), found -> assertThat(found).isNull());

        // Exists with null ID should return false
        asserter.assertThat(() -> repository.existsById(null), exists -> assertThat(exists).isFalse());

        // Delete with null ID should not throw
        asserter.execute(() -> repository.deleteById(null));
    }
}
