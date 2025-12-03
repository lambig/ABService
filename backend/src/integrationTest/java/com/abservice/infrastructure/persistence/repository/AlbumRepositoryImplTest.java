package com.abservice.infrastructure.persistence.repository;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.album.CatalogNumber;
import com.abservice.domain.model.vo.album.Isdn;
import com.abservice.domain.model.vo.album.TrackTitle;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.ArtistCreditName;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.EventDateAndSpace;
import com.abservice.domain.model.vo.common.EventReleasedAt;
import com.abservice.domain.model.vo.event.EventName;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
        testArtistCredit = new ArtistCredit(new ArtistCreditName("Test Artist"), "test-artist");
        testReleaseDate = BusinessDate.of(LocalDate.of(2024, 1, 1));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldSaveAndFindAlbum(UniAsserter asserter) {
        initTestData();

        var album = Album.create(new AlbumTitle("Test Album"), testReleaseDate, testArtistCredit, null, null, null);

        // Save the album
        asserter.assertThat(() -> repository.save(album), saved -> {
            Assertions.assertNotNull(saved);
            Assertions.assertEquals(album.id(), saved.id());
            Assertions.assertEquals("Test Album", saved.title().value());
        });

        // Find the saved album
        asserter.assertThat(() -> repository.findById(album.id()), found -> {
            Assertions.assertNotNull(found);
            Assertions.assertEquals(album.id(), found.id());
            Assertions.assertEquals("Test Album", found.title().value());
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldSaveAlbumWithTracks(UniAsserter asserter) {
        initTestData();

        var track1 = Track.create(1, new TrackTitle("Track 1"), null, null, null, false);
        var track2 = Track.create(2, new TrackTitle("Track 2"), null, null, null, false);

        var album = Album
                .create(new AlbumTitle("Album with Tracks"), testReleaseDate, testArtistCredit, null, null, null)
                .addTrack(track1).addTrack(track2);

        asserter.assertThat(() -> repository.save(album), saved -> {
            Assertions.assertEquals(2, saved.tracks().size());
            Assertions.assertEquals("Track 1", saved.tracks().get(0).title().value());
            Assertions.assertEquals("Track 2", saved.tracks().get(1).title().value());
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldSaveAlbumWithCatalogNumber(UniAsserter asserter) {
        initTestData();

        var album = Album.create(new AlbumTitle("Album with Catalog"), testReleaseDate, testArtistCredit, null,
                new CatalogNumber("TEST-001"), null);

        asserter.assertThat(() -> repository.save(album), saved -> {
            Assertions.assertNotNull(saved.catalogNumber());
            Assertions.assertEquals("TEST-001", saved.catalogNumber().value());
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldSaveAlbumWithIsdn(UniAsserter asserter) {
        initTestData();

        var album = Album.create(new AlbumTitle("Album with ISDN"), testReleaseDate, testArtistCredit, null, null,
                new Isdn("2784702901978"));

        asserter.assertThat(() -> repository.save(album), saved -> {
            Assertions.assertNotNull(saved.isdn());
            Assertions.assertEquals("2784702901978", saved.isdn().value());
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldSaveAlbumWithEvent(UniAsserter asserter) {
        initTestData();

        var eventReleasedAt = new EventReleasedAt(new EventName("Test Event"),
                List.of(new EventDateAndSpace(testReleaseDate, "A-01")), "Test Venue", "Test Note");

        var album = Album.create(new AlbumTitle("Album with Event"), testReleaseDate, testArtistCredit, eventReleasedAt,
                null, null);

        asserter.assertThat(() -> repository.save(album), saved -> {
            Assertions.assertNotNull(saved.eventReleasedAt());
            Assertions.assertEquals("Test Event", saved.eventReleasedAt().name().value());
            Assertions.assertEquals("Test Venue", saved.eventReleasedAt().place());
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldUpdateExistingAlbum(UniAsserter asserter) {
        initTestData();

        var album = Album.create(new AlbumTitle("Original Title"), testReleaseDate, testArtistCredit, null, null, null);

        // Save original
        asserter.assertThat(() -> repository.save(album),
                saved -> Assertions.assertEquals("Original Title", saved.title().value()));

        // Update
        var updated = Album.reconstruct(album.id(), new AlbumTitle("Updated Title"), testReleaseDate, testArtistCredit,
                null, null, null, List.of());

        asserter.assertThat(() -> repository.save(updated), result -> {
            Assertions.assertEquals(album.id(), result.id());
            Assertions.assertEquals("Updated Title", result.title().value());
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldDeleteAlbum(UniAsserter asserter) {
        initTestData();

        var album = Album.create(new AlbumTitle("Album to Delete"), testReleaseDate, testArtistCredit, null, null,
                null);

        // Save
        asserter.assertThat(() -> repository.save(album), saved -> Assertions.assertNotNull(saved));

        // Delete
        asserter.execute(() -> repository.deleteById(album.id()));

        // Verify deletion
        asserter.assertThat(() -> repository.findById(album.id()), found -> Assertions.assertNull(found));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldCheckExistence(UniAsserter asserter) {
        initTestData();

        var album = Album.create(new AlbumTitle("Existing Album"), testReleaseDate, testArtistCredit, null, null, null);

        // Save
        asserter.execute(() -> repository.save(album));

        // Check exists
        asserter.assertThat(() -> repository.existsById(album.id()), exists -> Assertions.assertTrue(exists));

        // Check non-existent
        var nonExistentId = new Album.Id("01234567-89ab-7def-0123-456789abcdef");
        asserter.assertThat(() -> repository.existsById(nonExistentId), exists -> Assertions.assertFalse(exists));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldCountAlbums(UniAsserter asserter) {
        initTestData();

        var album1 = Album.create(new AlbumTitle("Count Album 1"), testReleaseDate, testArtistCredit, null, null, null);
        var album2 = Album.create(new AlbumTitle("Count Album 2"), testReleaseDate, testArtistCredit, null, null, null);

        // Save albums
        asserter.execute(() -> repository.save(album1));
        asserter.execute(() -> repository.save(album2));

        // Verify count
        asserter.assertThat(() -> repository.count(), count -> Assertions.assertTrue(count >= 2));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByTitle(UniAsserter asserter) {
        initTestData();

        var title = new AlbumTitle("Unique Title for Search");
        var album = Album.create(title, testReleaseDate, testArtistCredit, null, null, null);

        // Save
        asserter.execute(() -> repository.save(album));

        // Find by title
        asserter.assertThat(() -> repository.findByTitle(title), found -> {
            Assertions.assertTrue(found.size() >= 1);
            Assertions.assertTrue(found.stream().anyMatch(a -> a.title().value().equals("Unique Title for Search")));
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByCatalogNumber(UniAsserter asserter) {
        initTestData();

        var catalogNumber = new CatalogNumber("UNIQUE-CAT-999");
        var album = Album.create(new AlbumTitle("Album with Unique Catalog"), testReleaseDate, testArtistCredit, null,
                catalogNumber, null);

        // Save
        asserter.execute(() -> repository.save(album));

        // Find by catalog number
        asserter.assertThat(() -> repository.findByCatalogNumber(catalogNumber), found -> {
            Assertions.assertNotNull(found);
            Assertions.assertEquals(catalogNumber, found.catalogNumber());
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldHandleNullInputs(UniAsserter asserter) {
        // Save null should fail
        asserter.assertFailedWith(() -> repository.save(null), IllegalArgumentException.class);

        // Find by null ID should return null
        asserter.assertThat(() -> repository.findById(null), found -> Assertions.assertNull(found));

        // Exists with null ID should return false
        asserter.assertThat(() -> repository.existsById(null), exists -> Assertions.assertFalse(exists));

        // Delete with null ID should not throw
        asserter.execute(() -> repository.deleteById(null));
    }
}
