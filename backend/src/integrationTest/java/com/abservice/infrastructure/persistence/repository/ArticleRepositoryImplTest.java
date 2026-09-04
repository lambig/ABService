package com.abservice.infrastructure.persistence.repository;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.entity.article.ArticleTag;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.article.ArticleTitle;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.article.IntroShort;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.model.vo.common.MarkupContent;
import com.abservice.test.CleanDatabase;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ArticleRepositoryImpl統合テスト（#39: タグのラウンドトリップに加え、CRUD・検索全般を網羅）
 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
class ArticleRepositoryImplTest {

    @Inject
    private ArticleRepositoryImpl repository;

    @Inject
    private AlbumRepositoryImpl albumRepository;

    @Inject
    private Mutiny.SessionFactory sessionFactory;

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldRestoreTagsOnRoundTrip(UniAsserter asserter) {
        final var tag1 = ArticleTag.create("Rock");
        final var tag2 = ArticleTag.create("Live");
        final var article = Article.create(
                ArticleType.NOTE,
                null,
                new ArticleTitle("Tagged Article"),
                null,
                null,
                businessNow())
                .addTag(tag1, businessNow())
                .addTag(tag2, businessNow());

        asserter.execute(() -> repository.save(article));

        asserter.assertThat(
                () -> repository.findById(article.id()),
                found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getTags()).hasSize(2);
                    assertThat(found.getTags())
                            .extracting(ArticleTag::getName)
                            .containsExactlyInAnyOrder("Rock", "Live");
                });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldHaveEmptyTagsWhenNoneSet(UniAsserter asserter) {
        final var article = Article.create(
                ArticleType.NOTE,
                null,
                new ArticleTitle("No Tags Article"),
                null,
                null,
                businessNow());

        asserter.execute(() -> repository.save(article));

        asserter.assertThat(
                () -> repository.findById(article.id()),
                found -> assertThat(found.getTags()).isEmpty());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldAddAndRemoveTagsOnResave(UniAsserter asserter) {
        final var keepTag = ArticleTag.create("Keep");
        final var removeTag = ArticleTag.create("Remove");
        final var original = Article.create(
                ArticleType.NOTE,
                null,
                new ArticleTitle("Reconcile Tags Article"),
                null,
                null,
                businessNow())
                .addTag(keepTag, businessNow())
                .addTag(removeTag, businessNow());

        asserter.execute(() -> repository.save(original));

        asserter.execute(
                () -> repository.findById(original.id())
                        .flatMap(
                                loaded -> {
                                    final var newTag = ArticleTag.create("New");
                                    final var next = loaded.removeTag(removeTag.id(), businessNow())
                                            .addTag(newTag, businessNow());
                                    return repository.save(next);
                                }));

        asserter.assertThat(
                () -> repository.findById(original.id()),
                found -> {
                    assertThat(found.getTags()).hasSize(2);
                    assertThat(found.getTags())
                            .extracting(ArticleTag::getName)
                            .containsExactlyInAnyOrder("Keep", "New");
                });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldShareTagAcrossArticles(UniAsserter asserter) {
        final var sharedTag = ArticleTag.create("Shared");
        final var article1 = Article.create(
                ArticleType.NOTE,
                null,
                new ArticleTitle("Shared Tag Article 1"),
                null,
                null,
                businessNow())
                .addTag(sharedTag, businessNow());
        final var article2 = Article.create(
                ArticleType.NOTE,
                null,
                new ArticleTitle("Shared Tag Article 2"),
                null,
                null,
                businessNow())
                .addTag(sharedTag, businessNow());

        asserter.execute(() -> repository.save(article1));
        asserter.execute(() -> repository.save(article2));

        asserter.assertThat(
                () -> repository.findById(article1.id()),
                found -> assertThat(found.getTags())
                        .extracting(ArticleTag::getName)
                        .containsExactly("Shared"));
        asserter.assertThat(
                () -> repository.findById(article2.id()),
                found -> assertThat(found.getTags())
                        .extracting(ArticleTag::getName)
                        .containsExactly("Shared"));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldSaveAndFindArticle(UniAsserter asserter) {
        final var article = Article.create(
                ArticleType.NOTE,
                null,
                new ArticleTitle("Test Article"),
                MarkupContent.markdown("Test Body"),
                IntroShort.of("Test Intro"),
                businessNow());

        asserter.assertThat(() -> repository.save(article), saved -> {
            assertThat(saved).isNotNull();
            assertThat(saved.id()).isEqualTo(article.id());
            assertThat(saved.title().value()).isEqualTo("Test Article");
            assertThat(saved.body().content()).isEqualTo("Test Body");
            assertThat(saved.introShort().value()).isEqualTo("Test Intro");
        });

        asserter.assertThat(() -> repository.findById(article.id()), found -> {
            assertThat(found).isNotNull();
            assertThat(found.id()).isEqualTo(article.id());
            assertThat(found.title().value()).isEqualTo("Test Article");
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldUpdateExistingArticle(UniAsserter asserter) {
        final var article = Article.create(
                ArticleType.NOTE,
                null,
                new ArticleTitle("Original Title"),
                null,
                null,
                businessNow());

        asserter.assertThat(
                () -> repository.save(article),
                saved -> assertThat(saved.title().value()).isEqualTo("Original Title"));

        final var updated = article.changeTitle(new ArticleTitle("Updated Title"), businessNow());

        asserter.assertThat(() -> repository.save(updated), result -> {
            assertThat(result.id()).isEqualTo(article.id());
            assertThat(result.title().value()).isEqualTo("Updated Title");
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldDeleteArticle(UniAsserter asserter) {
        final var article = Article.create(
                ArticleType.NOTE,
                null,
                new ArticleTitle("Article to Delete"),
                null,
                null,
                businessNow());

        asserter.assertThat(() -> repository.save(article), saved -> assertThat(saved).isNotNull());

        asserter.execute(() -> repository.deleteById(article.id()));

        asserter.assertThat(() -> repository.findById(article.id()), found -> assertThat(found).isNull());
    }

    /**
     * #251: 子を持つ記事の削除。
     *
     * <p>
     * 記事の削除も集約を通す。タグの張り付き（{@code article_tag_link}）が残らないことを見る。タグそのもの
     * （{@code article_tag}）は記事の内側ではないため、記事を消しても残る。母集団はこのテストが作ったものだけ
     * （{@link CleanDatabase}）。
     * </p>
     */
    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldDeleteArticleWithTagLinks(UniAsserter asserter) {
        final var article = Article.create(
                ArticleType.NOTE,
                null,
                new ArticleTitle("Article with tags"),
                null,
                null,
                businessNow())
                .addTag(ArticleTag.create("Rock"), businessNow())
                .addTag(ArticleTag.create("Live"), businessNow());

        asserter.execute(() -> repository.save(article));

        asserter.assertThat(() -> countRows("article_tag_link"), count -> assertThat(count).isEqualTo(2L));

        asserter.execute(() -> repository.deleteById(article.id()));

        asserter.assertThat(() -> repository.findById(article.id()), found -> assertThat(found).isNull());
        asserter.assertThat(() -> countRows("article"), count -> assertThat(count).isEqualTo(0L));
        asserter.assertThat(() -> countRows("article_tag_link"), count -> assertThat(count).isEqualTo(0L));
        asserter.assertThat(() -> countRows("article_tag"), count -> assertThat(count).isEqualTo(2L));
    }

    private Uni<Long> countRows(String table) {
        return sessionFactory.withSession(
                session -> session
                        .createNativeQuery("SELECT count(*) FROM " + table, Long.class)
                        .getSingleResult());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldCheckExistence(UniAsserter asserter) {
        final var article = Article.create(
                ArticleType.NOTE,
                null,
                new ArticleTitle("Existing Article"),
                null,
                null,
                businessNow());

        asserter.execute(() -> repository.save(article));

        asserter.assertThat(() -> repository.existsById(article.id()), exists -> assertThat(exists).isTrue());

        final var nonExistentId = Article.Id.of("01234567-89ab-7def-0123-456789abcdef");
        asserter.assertThat(() -> repository.existsById(nonExistentId), exists -> assertThat(exists).isFalse());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldCountArticles(UniAsserter asserter) {
        final var article1 = Article.create(
                ArticleType.NOTE,
                null,
                new ArticleTitle("Count Article 1"),
                null,
                null,
                businessNow());
        final var article2 = Article.create(
                ArticleType.NOTE,
                null,
                new ArticleTitle("Count Article 2"),
                null,
                null,
                businessNow());

        asserter.execute(() -> repository.save(article1));
        asserter.execute(() -> repository.save(article2));

        asserter.assertThat(() -> repository.count(), count -> assertThat(count >= 2).isTrue());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByArticleType(UniAsserter asserter) {
        final var article = Article.create(
                ArticleType.NEWS,
                null,
                new ArticleTitle("News Search Article"),
                null,
                null,
                businessNow());

        asserter.execute(() -> repository.save(article));

        asserter.assertThat(
                () -> repository.findByArticleType(ArticleType.NEWS),
                found -> assertThat(found.stream().anyMatch(a -> a.id().equals(article.id()))).isTrue());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByAlbumId(UniAsserter asserter) {
        final var album = Album.create(
                new AlbumTitle("Article Search Album"),
                BusinessDate.of(
                        2024,
                        1,
                        1),
                ArtistCredit.of("Test Artist"),
                MarkupContent.EMPTY,
                null,
                null,
                null,
                null);
        final var article = Article.create(
                ArticleType.ALBUM,
                album.id(),
                new ArticleTitle("Album Linked Article"),
                null,
                null,
                businessNow());

        asserter.execute(() -> albumRepository.save(album));
        asserter.execute(() -> repository.save(article));

        asserter.assertThat(
                () -> repository.findByAlbumId(album.id()),
                found -> assertThat(found).extracting(Article::id).containsExactly(article.id()));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindAllArticlesReferencingSameAlbum(UniAsserter asserter) {
        final var album = Album.create(
                new AlbumTitle("Multi Reference Album"),
                BusinessDate.of(
                        2024,
                        1,
                        1),
                ArtistCredit.of("Test Artist"),
                MarkupContent.EMPTY,
                null,
                null,
                null,
                null);
        final var first = Article.create(
                ArticleType.ALBUM,
                album.id(),
                new ArticleTitle("First Linked Article"),
                null,
                null,
                businessNow());
        final var second = Article.create(
                ArticleType.ALBUM,
                album.id(),
                new ArticleTitle("Second Linked Article"),
                null,
                null,
                businessNow());

        asserter.execute(() -> albumRepository.save(album));
        asserter.execute(() -> repository.saveAll(List.of(first, second)));

        asserter.assertThat(
                () -> repository.findByAlbumId(album.id()),
                found -> assertThat(found).extracting(Article::id)
                        .containsExactlyInAnyOrder(first.id(), second.id()));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByPublicFlag(UniAsserter asserter) {
        final var article = Article.create(
                ArticleType.NOTE,
                null,
                new ArticleTitle("Published Search Article"),
                null,
                null,
                businessNow())
                .publish(businessNow());

        asserter.execute(() -> repository.save(article));

        asserter.assertThat(
                () -> repository.findByPublicFlag(true),
                found -> assertThat(found.stream().anyMatch(a -> a.id().equals(article.id()))).isTrue());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByPublishedAtBetween(UniAsserter asserter) {
        final var article = Article.create(
                ArticleType.NOTE,
                null,
                new ArticleTitle("Published Range Article"),
                null,
                null,
                businessNow())
                .publish(businessNow());

        asserter.execute(() -> repository.save(article));

        final var startDate = BusinessDateTime.of(Instant.parse("2023-01-01T00:00:00Z"));
        final var endDate = BusinessDateTime.of(Instant.parse("2025-01-01T00:00:00Z"));

        asserter.assertThat(
                () -> repository.findByPublishedAtBetween(startDate, endDate),
                found -> assertThat(found.stream().anyMatch(a -> a.id().equals(article.id()))).isTrue());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFindByTitleContaining(UniAsserter asserter) {
        final var article = Article.create(
                ArticleType.NOTE,
                null,
                new ArticleTitle("UniqueKeywordForSearch Article"),
                null,
                null,
                businessNow());

        asserter.execute(() -> repository.save(article));

        asserter.assertThat(() -> repository.findByTitleContaining("UniqueKeywordForSearch"), found -> {
            assertThat(found).isNotEmpty();
            assertThat(found.stream().anyMatch(a -> a.id().equals(article.id()))).isTrue();
        });
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldHandleNullInputs(UniAsserter asserter) {
        // save(null) は明示的な引数チェックにより失敗Uniを返す（他集約とは異なり同期throwではない）
        asserter.assertFailedWith(() -> repository.save(null), IllegalArgumentException.class);

        asserter.assertThat(() -> repository.findById(null), found -> assertThat(found).isNull());

        asserter.assertThat(() -> repository.existsById(null), exists -> assertThat(exists).isFalse());

        asserter.execute(() -> repository.deleteById(null));
    }

    private static BusinessDateTime businessNow() {
        return BusinessDateTime.of(Instant.parse("2024-01-01T00:00:00Z"));
    }
}
