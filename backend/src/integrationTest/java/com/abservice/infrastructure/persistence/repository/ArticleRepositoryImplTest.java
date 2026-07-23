package com.abservice.infrastructure.persistence.repository;

import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.entity.article.ArticleTag;
import com.abservice.domain.model.vo.article.ArticleTitle;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ArticleRepositoryImpl統合テスト（#39: タグのラウンドトリップ）
 */
@QuarkusTest
class ArticleRepositoryImplTest {

    @Inject
    private ArticleRepositoryImpl repository;

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
                null)
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
                null);

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
                null)
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
                null)
                .addTag(sharedTag, businessNow());
        final var article2 = Article.create(
                ArticleType.NOTE,
                null,
                new ArticleTitle("Shared Tag Article 2"),
                null,
                null)
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

    private static BusinessDateTime businessNow() {
        return BusinessDateTime.of(Instant.parse("2024-01-01T00:00:00Z"));
    }
}
