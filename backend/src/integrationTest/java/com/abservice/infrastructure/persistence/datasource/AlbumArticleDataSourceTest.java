package com.abservice.infrastructure.persistence.datasource;

import com.abservice.infrastructure.persistence.entity.AlbumAcquisitionChannelTableRecord;
import com.abservice.infrastructure.persistence.entity.AlbumArticleTableRecord;
import com.abservice.infrastructure.persistence.entity.AlbumDistributionTableRecord;
import com.abservice.infrastructure.persistence.entity.AlbumTableRecord;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AlbumArticleDataSource統合テスト（Phase 9）
 *
 * <p>
 * ドメイン層・Repository層を経由せず、DataSource自身のクエリメソッド（JOIN
 * FETCH・ページング・削除/存在確認）を直接検証します。 {@code AlbumArticleTableRecord} は {@code Album}
 * と共有主キー（{@code @MapsId}）のため、必ず対応する {@code AlbumTableRecord}
 * を先に用意し、{@code album.setAlbumArticle(article)} の cascade で一括永続化します。
 * </p>
 */
@QuarkusTest
class AlbumArticleDataSourceTest {

    @Inject
    private AlbumArticleDataSource dataSource;

    @Inject
    private AlbumDataSource albumDataSource;

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

    private static AlbumArticleTableRecord linkArticle(AlbumTableRecord album, AlbumArticleTableRecord article) {
        article.setAlbum(album);
        article.setDomainId(album.getDomainId());
        album.setAlbumArticle(article);
        return article;
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByDomainId(UniAsserter asserter) {
        final var album = newAlbum("Find By Domain Id Album");
        final var article = linkArticle(album, new AlbumArticleTableRecord().setIntroShort("Short intro"));

        asserter.execute(() -> albumDataSource.persist(album));

        asserter.assertThat(() -> dataSource.findByDomainId(article.getDomainId()), found -> {
            assertThat(found).isNotNull();
            assertThat(found.getDomainId()).isEqualTo(article.getDomainId());
            assertThat(found.getIntroShort()).isEqualTo("Short intro");
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldReturnNullWhenDomainIdNotFound(UniAsserter asserter) {
        asserter.assertThat(
                () -> dataSource.findByDomainId(UUID.randomUUID().toString()),
                found -> assertThat(found).isNull());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByAlbumIdWithoutDistributionOrChannels(UniAsserter asserter) {
        final var album = newAlbum("Find By Album Id Album");
        final var article = linkArticle(
                album,
                new AlbumArticleTableRecord().setIntroLong("Long intro").setFirstEventSpace("East A-01"));

        asserter.execute(() -> albumDataSource.persist(album));

        asserter.assertThat(() -> dataSource.findByAlbumId(article.getDomainId()), found -> {
            assertThat(found).isNotNull();
            assertThat(found.getIntroLong()).isEqualTo("Long intro");
            assertThat(found.getFirstEventSpace()).isEqualTo("East A-01");
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindAllEager(UniAsserter asserter) {
        final var album = newAlbum("Find All Eager Album");
        final var article = linkArticle(album, new AlbumArticleTableRecord().setIntroShort("Eager Search"));

        asserter.execute(() -> albumDataSource.persist(album));

        asserter.assertThat(
                () -> dataSource.findAllEager(),
                found -> assertThat(found.stream().anyMatch(a -> a.getDomainId().equals(article.getDomainId())))
                        .isTrue());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByIds(UniAsserter asserter) {
        final var album1 = newAlbum("Find By Ids Album 1");
        final var article1 = linkArticle(album1, new AlbumArticleTableRecord());
        final var album2 = newAlbum("Find By Ids Album 2");
        final var article2 = linkArticle(album2, new AlbumArticleTableRecord());

        asserter.execute(() -> albumDataSource.persist(album1));
        asserter.execute(() -> albumDataSource.persist(album2));

        asserter.assertThat(
                () -> dataSource.findByIds(List.of(article1.getDomainId(), article2.getDomainId())),
                found -> assertThat(found).extracting(AlbumArticleTableRecord::getDomainId)
                        .containsExactlyInAnyOrder(article1.getDomainId(), article2.getDomainId()));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByLabelTag(UniAsserter asserter) {
        final var album = newAlbum("Label Search Album");
        final var article = linkArticle(album, new AlbumArticleTableRecord().setLabelTag("NEW"));

        asserter.execute(() -> albumDataSource.persist(album));

        asserter.assertThat(
                () -> dataSource.findByLabelTag("NEW"),
                found -> assertThat(found.stream().anyMatch(a -> a.getDomainId().equals(article.getDomainId())))
                        .isTrue());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByFirstEventSpaceContaining(UniAsserter asserter) {
        final var album = newAlbum("Space Search Album");
        final var article = linkArticle(
                album,
                new AlbumArticleTableRecord().setFirstEventSpace("UniqueDsSpace-01"));

        asserter.execute(() -> albumDataSource.persist(album));

        asserter.assertThat(() -> dataSource.findByFirstEventSpaceContaining("UniqueDsSpace"), found -> {
            assertThat(found).isNotEmpty();
            assertThat(found.stream().anyMatch(a -> a.getDomainId().equals(article.getDomainId()))).isTrue();
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindWithDistribution(UniAsserter asserter) {
        final var album = newAlbum("With Distribution Album");
        final var article = linkArticle(album, new AlbumArticleTableRecord());
        final var distribution = new AlbumDistributionTableRecord().setAlbum(album).setPhysicalPrice(1000);
        album.setAlbumDistribution(distribution);

        asserter.execute(() -> albumDataSource.persist(album));

        asserter.assertThat(
                () -> dataSource.findWithDistribution(),
                found -> assertThat(found.stream().anyMatch(a -> a.getDomainId().equals(article.getDomainId())))
                        .isTrue());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindWithAcquisitionChannels(UniAsserter asserter) {
        final var album = newAlbum("With Channels Album");
        final var article = linkArticle(album, new AlbumArticleTableRecord());
        final var channel = new AlbumAcquisitionChannelTableRecord()
                .setAlbum(album)
                .setDomainId(UUID.randomUUID().toString())
                .setChannelType("EVENT")
                .setName("Search Channel");
        album.getAcquisitionChannels().add(channel);

        asserter.execute(() -> albumDataSource.persist(album));

        asserter.assertThat(
                () -> dataSource.findWithAcquisitionChannels(),
                found -> assertThat(found.stream().anyMatch(a -> a.getDomainId().equals(article.getDomainId())))
                        .isTrue());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindAlbumWithArticleRelationsByDomainId(UniAsserter asserter) {
        final var album = newAlbum("Article Relations Album");
        linkArticle(album, new AlbumArticleTableRecord().setIntroShort("Relations intro"));

        asserter.execute(() -> albumDataSource.persist(album));

        asserter.assertThat(() -> dataSource.findAlbumWithArticleRelationsByDomainId(album.getDomainId()), found -> {
            assertThat(found).isNotNull();
            assertThat(found.getAlbumArticle()).isNotNull();
            assertThat(found.getAlbumArticle().getIntroShort()).isEqualTo("Relations intro");
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldPageResultsWithPagedQuery(UniAsserter asserter) {
        final var album1 = newAlbum("Paged Query Album 1");
        linkArticle(album1, new AlbumArticleTableRecord());
        final var album2 = newAlbum("Paged Query Album 2");
        linkArticle(album2, new AlbumArticleTableRecord());
        final var album3 = newAlbum("Paged Query Album 3");
        linkArticle(album3, new AlbumArticleTableRecord());

        asserter.execute(() -> albumDataSource.persist(album1));
        asserter.execute(() -> albumDataSource.persist(album2));
        asserter.execute(() -> albumDataSource.persist(album3));

        asserter.execute(
                () -> dataSource.pagedQuery(0, 1).count()
                        .invoke(total -> assertThat(total >= 3).isTrue()));

        asserter.execute(
                () -> dataSource.pagedQuery(0, 2).list()
                        .invoke(page -> assertThat(page).hasSizeLessThanOrEqualTo(2)));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldDeleteByAlbumId(UniAsserter asserter) {
        final var album = newAlbum("Delete Article Album");
        final var article = linkArticle(album, new AlbumArticleTableRecord());

        asserter.execute(() -> albumDataSource.persist(album));

        asserter.assertThat(
                () -> dataSource.deleteByAlbumId(article.getDomainId()),
                deleted -> assertThat(deleted).isTrue());

        asserter.assertThat(
                () -> dataSource.deleteByAlbumId(article.getDomainId()),
                deleted -> assertThat(deleted).isFalse());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldDeleteByAlbumIds(UniAsserter asserter) {
        final var album1 = newAlbum("Bulk Delete Article Album 1");
        final var article1 = linkArticle(album1, new AlbumArticleTableRecord());
        final var album2 = newAlbum("Bulk Delete Article Album 2");
        final var article2 = linkArticle(album2, new AlbumArticleTableRecord());

        asserter.execute(() -> albumDataSource.persist(album1));
        asserter.execute(() -> albumDataSource.persist(album2));

        asserter.execute(
                () -> dataSource.deleteByAlbumIds(List.of(article1.getDomainId(), article2.getDomainId())));

        asserter.assertThat(
                () -> dataSource.findByDomainId(article1.getDomainId()),
                found -> assertThat(found).isNull());
        asserter.assertThat(
                () -> dataSource.findByDomainId(article2.getDomainId()),
                found -> assertThat(found).isNull());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldCheckExistsByAlbumId(UniAsserter asserter) {
        final var album = newAlbum("Existence Check Article Album");
        final var article = linkArticle(album, new AlbumArticleTableRecord());

        asserter.execute(() -> albumDataSource.persist(album));

        asserter.assertThat(
                () -> dataSource.existsByAlbumId(article.getDomainId()),
                exists -> assertThat(exists).isTrue());

        asserter.assertThat(
                () -> dataSource.existsByAlbumId(UUID.randomUUID().toString()),
                exists -> assertThat(exists).isFalse());
    }
}
