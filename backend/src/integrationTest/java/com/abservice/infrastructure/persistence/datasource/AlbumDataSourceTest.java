package com.abservice.infrastructure.persistence.datasource;

import com.abservice.application.query.SortSpec;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import com.abservice.infrastructure.persistence.entity.TrackTableRecord;
import com.abservice.test.CleanDatabase;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AlbumDataSource統合テスト（Phase 9）
 *
 * <p>
 * ドメイン層・Repository層を経由せず、DataSource自身のクエリメソッド（JOIN
 * FETCH・ページング・削除/存在確認）を直接検証します。
 * </p>
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
class AlbumDataSourceTest {

    @Inject
    private AlbumDataSource dataSource;

    private static AlbumTableRecord newAlbum(String title) {
        return new AlbumTableRecord()
                .setDomainId(UUID.randomUUID().toString())
                .setTitle(title)
                .setReleaseDate(
                        LocalDate.of(
                                2024,
                                1,
                                1))
                .setArtistDisplayName("Test Artist");
    }

    private static TrackTableRecord newTrack(int trackNo, String title) {
        return new TrackTableRecord()
                .setDomainId(UUID.randomUUID().toString())
                .setTrackNo(trackNo)
                .setTitle(title);
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByDomainId(UniAsserter asserter) {
        final var entity = newAlbum("Find By Domain Id");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(() -> dataSource.findByDomainId(entity.getDomainId(), Visibility.ALL), found -> {
            assertThat(found).isNotNull();
            assertThat(found.getDomainId()).isEqualTo(entity.getDomainId());
            assertThat(found.getTitle()).isEqualTo("Find By Domain Id");
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldReturnNullWhenDomainIdNotFound(UniAsserter asserter) {
        asserter.assertThat(
                () -> dataSource.findByDomainId(UUID.randomUUID().toString(), Visibility.ALL),
                found -> assertThat(found).isNull());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindPublicByDomainId(UniAsserter asserter) {
        final var entity = newAlbum("Find Public By Domain Id").setPublishedAt(Instant.parse("2024-06-01T00:00:00Z"));

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(() -> dataSource.findByDomainId(entity.getDomainId(), Visibility.PUBLIC_ONLY), found -> {
            assertThat(found).isNotNull();
            assertThat(found.getDomainId()).isEqualTo(entity.getDomainId());
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldReturnNullFromFindPublicByDomainIdWhenNotPublished(UniAsserter asserter) {
        final var entity = newAlbum("Draft Not Findable As Public");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(
                () -> dataSource.findByDomainId(entity.getDomainId(), Visibility.PUBLIC_ONLY),
                found -> assertThat(found).isNull());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldPersistAndFindByIdWithTracks(UniAsserter asserter) {
        final var album = newAlbum("Album with Tracks");
        album.getTracks().add(newTrack(1, "Track 1").setAlbum(album));
        album.getTracks().add(newTrack(2, "Track 2").setAlbum(album));

        asserter.execute(() -> dataSource.persistAlbumWithRelations(album));

        asserter.assertThat(() -> dataSource.findByIdWithTracks(album.getDomainId()), found -> {
            assertThat(found).isNotNull();
            assertThat(found.getTracks()).hasSize(2);
            assertThat(found.getTracks()).extracting(TrackTableRecord::getTitle)
                    .containsExactlyInAnyOrder("Track 1", "Track 2");
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByIdsWithTracks(UniAsserter asserter) {
        final var album1 = newAlbum("Multi Find Album 1");
        album1.getTracks().add(newTrack(1, "Album1 Track").setAlbum(album1));
        final var album2 = newAlbum("Multi Find Album 2");
        album2.getTracks().add(newTrack(1, "Album2 Track").setAlbum(album2));

        asserter.execute(() -> dataSource.persistAlbumWithRelations(album1));
        asserter.execute(() -> dataSource.persistAlbumWithRelations(album2));

        asserter.assertThat(
                () -> dataSource.findByIdsWithTracks(List.of(album1.getDomainId(), album2.getDomainId())),
                found -> {
                    assertThat(found).hasSize(2);
                    assertThat(found.stream().flatMap(a -> a.getTracks().stream()))
                            .extracting(TrackTableRecord::getTitle)
                            .containsExactlyInAnyOrder("Album1 Track", "Album2 Track");
                });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByTitle(UniAsserter asserter) {
        final var entity = newAlbum("Unique Title for DataSource Search");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(() -> dataSource.findByTitle("Unique Title for DataSource Search"), found -> {
            assertThat(found).isNotEmpty();
            assertThat(found.stream().anyMatch(a -> a.getDomainId().equals(entity.getDomainId()))).isTrue();
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByArtistDisplayName(UniAsserter asserter) {
        final var entity = newAlbum("Artist Search Album").setArtistDisplayName("Unique Artist For DataSource");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(() -> dataSource.findByArtistDisplayName("Unique Artist For DataSource"), found -> {
            assertThat(found).isNotEmpty();
            assertThat(found.stream().anyMatch(a -> a.getDomainId().equals(entity.getDomainId()))).isTrue();
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByEventName(UniAsserter asserter) {
        final var entity = newAlbum("Event Search Album").setEventName("Unique Event For DataSource");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(() -> dataSource.findByEventName("Unique Event For DataSource"), found -> {
            assertThat(found).isNotEmpty();
            assertThat(found.stream().anyMatch(a -> a.getDomainId().equals(entity.getDomainId()))).isTrue();
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByCatalogNumber(UniAsserter asserter) {
        final var entity = newAlbum("Catalog Search Album").setCatalogNumber("UNIQUE-DS-CAT-001");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(() -> dataSource.findByCatalogNumber("UNIQUE-DS-CAT-001"), found -> {
            assertThat(found).isNotNull();
            assertThat(found.getDomainId()).isEqualTo(entity.getDomainId());
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByReleaseYear(UniAsserter asserter) {
        final var entity = newAlbum("Release Year Search Album").setReleaseDate(
                LocalDate.of(
                        2030,
                        6,
                        15));

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(() -> dataSource.findByReleaseYear(2030), found -> {
            assertThat(found).isNotEmpty();
            assertThat(found.stream().anyMatch(a -> a.getDomainId().equals(entity.getDomainId()))).isTrue();
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldPageResultsWithPagedQuery(UniAsserter asserter) {
        final var entity1 = newAlbum("Paged Query Album 1");
        final var entity2 = newAlbum("Paged Query Album 2");
        final var entity3 = newAlbum("Paged Query Album 3");

        asserter.execute(() -> dataSource.persist(entity1));
        asserter.execute(() -> dataSource.persist(entity2));
        asserter.execute(() -> dataSource.persist(entity3));

        asserter.execute(
                () -> dataSource.pagedQuery(
                        0,
                        1,
                        Visibility.ALL,
                        SortSpec.defaultOrder(),
                        null,
                        null).count()
                        .invoke(total -> assertThat(total >= 3).isTrue()));

        asserter.execute(
                () -> dataSource.pagedQuery(
                        0,
                        2,
                        Visibility.ALL,
                        SortSpec.defaultOrder(),
                        null,
                        null).list()
                        .invoke(page -> assertThat(page).hasSizeLessThanOrEqualTo(2)));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldOnlyPageThroughPublishedAlbumsWithPagedPublicQuery(UniAsserter asserter) {
        final var publishedEntity = newAlbum("Paged Public Query Album")
                .setPublishedAt(Instant.parse("2024-06-01T00:00:00Z"));
        final var draftEntity = newAlbum("Paged Public Query Draft Album");

        asserter.execute(() -> dataSource.persist(publishedEntity));
        asserter.execute(() -> dataSource.persist(draftEntity));

        asserter.assertThat(
                () -> dataSource.pagedQuery(
                        0,
                        100,
                        Visibility.PUBLIC_ONLY,
                        SortSpec.defaultOrder(),
                        null,
                        null).list(),
                found -> {
                    assertThat(found.stream().anyMatch(a -> a.getDomainId().equals(publishedEntity.getDomainId())))
                            .isTrue();
                    assertThat(found.stream().anyMatch(a -> a.getDomainId().equals(draftEntity.getDomainId())))
                            .isFalse();
                });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFilterByTitlePartiallyIgnoringCaseWithPagedQuery(UniAsserter asserter) {
        final var target = newAlbum("Filtered Session Album");
        final var other = newAlbum("Unrelated Album");

        asserter.execute(() -> dataSource.persist(target));
        asserter.execute(() -> dataSource.persist(other));

        asserter.assertThat(
                () -> dataSource.pagedQuery(
                        0,
                        100,
                        Visibility.ALL,
                        SortSpec.defaultOrder(),
                        "session",
                        null).list(),
                found -> {
                    assertThat(contains(found, target)).isTrue();
                    assertThat(contains(found, other)).isFalse();
                });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFilterByCatalogNumberWithPagedQuery(UniAsserter asserter) {
        final var target = newAlbum("Catalog Filter Album").setCatalogNumber("ABS-0101");
        final var other = newAlbum("Catalog Filter Other Album").setCatalogNumber("ABS-0202");

        asserter.execute(() -> dataSource.persist(target));
        asserter.execute(() -> dataSource.persist(other));

        asserter.assertThat(
                () -> dataSource.pagedQuery(
                        0,
                        100,
                        Visibility.ALL,
                        SortSpec.defaultOrder(),
                        null,
                        "abs-0101").list(),
                found -> {
                    assertThat(contains(found, target)).isTrue();
                    assertThat(contains(found, other)).isFalse();
                });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldCombineTitleAndCatalogNumberFiltersWithAnd(UniAsserter asserter) {
        final var both = newAlbum("Combined Filter Album").setCatalogNumber("ABS-0303");
        final var titleOnly = newAlbum("Combined Filter Album").setCatalogNumber("ABS-0404");

        asserter.execute(() -> dataSource.persist(both));
        asserter.execute(() -> dataSource.persist(titleOnly));

        asserter.assertThat(
                () -> dataSource.pagedQuery(
                        0,
                        100,
                        Visibility.ALL,
                        SortSpec.defaultOrder(),
                        "combined filter",
                        "ABS-0303").list(),
                found -> {
                    assertThat(contains(found, both)).isTrue();
                    assertThat(contains(found, titleOnly)).isFalse();
                });
    }

    /**
     * 利用者が打った語に含まれる LIKE のワイルドカードを、文字そのものとして扱うことを確かめる。
     *
     * <p>
     * エスケープしないと {@code _} が任意の1文字に化け、{@code ABS-0505} を狙った検索が {@code ABS_0505}
     * 以外にも当たる。
     * </p>
     *
     * @param asserter
     *            リアクティブなアサーション
     */
    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldTreatLikeWildcardsAsLiteralInFilters(UniAsserter asserter) {
        final var underscored = newAlbum("Wildcard Album").setCatalogNumber("ABS_0505");
        final var hyphenated = newAlbum("Wildcard Album").setCatalogNumber("ABS-0505");

        asserter.execute(() -> dataSource.persist(underscored));
        asserter.execute(() -> dataSource.persist(hyphenated));

        asserter.assertThat(
                () -> dataSource.pagedQuery(
                        0,
                        100,
                        Visibility.ALL,
                        SortSpec.defaultOrder(),
                        null,
                        "ABS_0505").list(),
                found -> {
                    assertThat(contains(found, underscored)).isTrue();
                    assertThat(contains(found, hyphenated)).isFalse();
                });
    }

    private static boolean contains(List<AlbumTableRecord> found, AlbumTableRecord expected) {
        return found.stream().anyMatch(a -> a.getDomainId().equals(expected.getDomainId()));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldDeleteByAlbumId(UniAsserter asserter) {
        final var entity = newAlbum("Album to Delete");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(
                () -> dataSource.deleteByAlbumId(entity.getDomainId()),
                deleted -> assertThat(deleted).isTrue());

        asserter.assertThat(
                () -> dataSource.deleteByAlbumId(entity.getDomainId()),
                deleted -> assertThat(deleted).isFalse());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldDeleteByAlbumIds(UniAsserter asserter) {
        final var entity1 = newAlbum("Bulk Delete Album 1");
        final var entity2 = newAlbum("Bulk Delete Album 2");

        asserter.execute(() -> dataSource.persist(entity1));
        asserter.execute(() -> dataSource.persist(entity2));

        asserter.execute(() -> dataSource.deleteByAlbumIds(List.of(entity1.getDomainId(), entity2.getDomainId())));

        asserter.assertThat(
                () -> dataSource.findByDomainId(entity1.getDomainId(), Visibility.ALL),
                found -> assertThat(found).isNull());
        asserter.assertThat(
                () -> dataSource.findByDomainId(entity2.getDomainId(), Visibility.ALL),
                found -> assertThat(found).isNull());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldCheckExistsByAlbumId(UniAsserter asserter) {
        final var entity = newAlbum("Existence Check Album");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(
                () -> dataSource.existsByAlbumId(entity.getDomainId()),
                exists -> assertThat(exists).isTrue());

        asserter.assertThat(
                () -> dataSource.existsByAlbumId(UUID.randomUUID().toString()),
                exists -> assertThat(exists).isFalse());
    }
}
