package com.abservice.infrastructure.persistence.datasource;

import com.abservice.application.query.SortSpec;
import com.abservice.infrastructure.persistence.entity.ArticleAlbumReferenceTableRecord;
import com.abservice.infrastructure.persistence.entity.ArticleTableRecord;
import com.abservice.test.CleanDatabase;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ArticleDataSource統合テスト（Phase 9）
 *
 * <p>
 * ドメイン層・Repository層を経由せず、DataSource自身のクエリメソッド（JOIN
 * FETCH・ページング・削除/存在確認）を直接検証します。
 * </p>
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
class ArticleDataSourceTest {

    @Inject
    private ArticleDataSource dataSource;

    private static ArticleTableRecord newArticle(String title) {
        return new ArticleTableRecord()
                .setDomainId(UUID.randomUUID().toString())
                .setArticleType("NOTE")
                .setTitle(title)
                .setBodyFormat("PLAIN_TEXT")
                .setIsPublic(false);
    }

    private static ArticleTableRecord newAlbumArticle(String title, String albumId) {
        final var entity = newArticle(title)
                .setArticleType("ALBUM");
        return entity.setAlbumReference(
                new ArticleAlbumReferenceTableRecord()
                        .setArticle(entity)
                        .setAlbumId(albumId));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByDomainId(UniAsserter asserter) {
        final var entity = newArticle("Find By Domain Id");

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
        final var entity = newArticle("Find Public By Domain Id").setIsPublic(true);

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(() -> dataSource.findByDomainId(entity.getDomainId(), Visibility.PUBLIC_ONLY), found -> {
            assertThat(found).isNotNull();
            assertThat(found.getDomainId()).isEqualTo(entity.getDomainId());
            assertThat(found.getTitle()).isEqualTo("Find Public By Domain Id");
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldReturnNullFromFindPublicByDomainIdWhenNotPublic(UniAsserter asserter) {
        final var entity = newArticle("Draft Not Findable As Public");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(
                () -> dataSource.findByDomainId(entity.getDomainId(), Visibility.PUBLIC_ONLY),
                found -> assertThat(found).isNull());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByIds(UniAsserter asserter) {
        final var entity1 = newArticle("Find By Ids 1");
        final var entity2 = newArticle("Find By Ids 2");

        asserter.execute(() -> dataSource.persist(entity1));
        asserter.execute(() -> dataSource.persist(entity2));

        asserter.assertThat(
                () -> dataSource.findByIds(List.of(entity1.getDomainId(), entity2.getDomainId())),
                found -> assertThat(found).extracting(ArticleTableRecord::getDomainId)
                        .containsExactlyInAnyOrder(entity1.getDomainId(), entity2.getDomainId()));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByArticleType(UniAsserter asserter) {
        final var entity = newArticle("News Type Search").setArticleType("NEWS");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(
                () -> dataSource.findByArticleType("NEWS"),
                found -> assertThat(found.stream().anyMatch(a -> a.getDomainId().equals(entity.getDomainId())))
                        .isTrue());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByAlbumId(UniAsserter asserter) {
        final var albumId = UUID.randomUUID().toString();
        final var entity = newAlbumArticle("Album Linked Article", albumId);

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(
                () -> dataSource.findByAlbumId(albumId),
                found -> assertThat(found).extracting(ArticleTableRecord::getDomainId)
                        .containsExactly(entity.getDomainId()));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindAllArticlesReferencingSameAlbum(UniAsserter asserter) {
        final var albumId = UUID.randomUUID().toString();
        final var first = newAlbumArticle("First Article Of Album", albumId);
        final var second = newAlbumArticle("Second Article Of Album", albumId);

        asserter.execute(() -> dataSource.persist(first));
        asserter.execute(() -> dataSource.persist(second));

        asserter.assertThat(
                () -> dataSource.findByAlbumId(albumId),
                found -> assertThat(found).extracting(ArticleTableRecord::getDomainId)
                        .containsExactlyInAnyOrder(first.getDomainId(), second.getDomainId()));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByPublicFlag(UniAsserter asserter) {
        final var entity = newArticle("Public Flag Search").setIsPublic(true);

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(
                () -> dataSource.findByPublicFlag(true),
                found -> assertThat(found.stream().anyMatch(a -> a.getDomainId().equals(entity.getDomainId())))
                        .isTrue());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByPublishedAtBetween(UniAsserter asserter) {
        final var publishedAt = Instant.parse("2024-06-01T00:00:00Z");
        final var entity = newArticle("Published Range Search")
                .setIsPublic(true)
                .setPublishedAt(publishedAt);

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(
                () -> dataSource.findByPublishedAtBetween(
                        Instant.parse("2024-01-01T00:00:00Z"),
                        Instant.parse("2025-01-01T00:00:00Z")),
                found -> assertThat(found.stream().anyMatch(a -> a.getDomainId().equals(entity.getDomainId())))
                        .isTrue());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByTitleContaining(UniAsserter asserter) {
        final var entity = newArticle("UniqueTitleKeyword-DataSource Article");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(() -> dataSource.findByTitleContaining("UniqueTitleKeyword-DataSource"), found -> {
            assertThat(found).isNotEmpty();
            assertThat(found.stream().anyMatch(a -> a.getDomainId().equals(entity.getDomainId()))).isTrue();
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldPageResultsWithPagedQuery(UniAsserter asserter) {
        final var entity1 = newArticle("Paged Query Article 1");
        final var entity2 = newArticle("Paged Query Article 2");
        final var entity3 = newArticle("Paged Query Article 3");

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
    void shouldOnlyPageThroughPublicArticlesWithPagedPublicQuery(UniAsserter asserter) {
        final var publicEntity = newArticle("Paged Public Query Article").setIsPublic(true);
        final var draftEntity = newArticle("Paged Public Query Draft Article");

        asserter.execute(() -> dataSource.persist(publicEntity));
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
                    assertThat(found.stream().anyMatch(a -> a.getDomainId().equals(publicEntity.getDomainId())))
                            .isTrue();
                    assertThat(found.stream().anyMatch(a -> a.getDomainId().equals(draftEntity.getDomainId())))
                            .isFalse();
                });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldDeleteByArticleId(UniAsserter asserter) {
        final var entity = newArticle("Article to Delete");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(
                () -> dataSource.deleteByArticleId(entity.getDomainId()),
                deleted -> assertThat(deleted).isTrue());

        asserter.assertThat(
                () -> dataSource.deleteByArticleId(entity.getDomainId()),
                deleted -> assertThat(deleted).isFalse());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldDeleteByArticleIds(UniAsserter asserter) {
        final var entity1 = newArticle("Bulk Delete Article 1");
        final var entity2 = newArticle("Bulk Delete Article 2");

        asserter.execute(() -> dataSource.persist(entity1));
        asserter.execute(() -> dataSource.persist(entity2));

        asserter.execute(() -> dataSource.deleteByArticleIds(List.of(entity1.getDomainId(), entity2.getDomainId())));

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
    void shouldCheckExistsByArticleId(UniAsserter asserter) {
        final var entity = newArticle("Existence Check Article");

        asserter.execute(() -> dataSource.persist(entity));

        asserter.assertThat(
                () -> dataSource.existsByArticleId(entity.getDomainId()),
                exists -> assertThat(exists).isTrue());

        asserter.assertThat(
                () -> dataSource.existsByArticleId(UUID.randomUUID().toString()),
                exists -> assertThat(exists).isFalse());
    }
}
