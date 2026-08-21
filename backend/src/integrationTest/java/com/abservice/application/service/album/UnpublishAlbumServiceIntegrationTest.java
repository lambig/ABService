package com.abservice.application.service.album;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.article.ArticleTitle;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.infrastructure.persistence.repository.AlbumRepositoryImpl;
import com.abservice.infrastructure.persistence.repository.ArticleRepositoryImpl;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * UnpublishAlbumServiceのカスケード非公開化（当該アルバムを参照する公開中の記事も連動して非公開化する）の統合テスト
 *
 * <p>
 * アルバム記事（{@code albumId}を持つArticle）の作成はREST未提供のため、ここではリポジトリを直接使って
 * 前提データを組み立てる。単純なアルバム非公開化・404はREST経由で {@code AlbumRestIntegrationTest}が検証する。
 * </p>
 */
@QuarkusTest
class UnpublishAlbumServiceIntegrationTest {

    private static final BusinessDateTime NOW = BusinessDateTime.of(Instant.parse("2024-01-01T00:00:00Z"));

    @Inject
    private UnpublishAlbumService unpublishAlbumService;

    @Inject
    private AlbumRepositoryImpl albumRepository;

    @Inject
    private ArticleRepositoryImpl articleRepository;

    private static Album newPublishedAlbum(String title) {
        return Album.create(
                new AlbumTitle(title),
                BusinessDate.of(
                        LocalDate.of(
                                2024,
                                1,
                                1)),
                ArtistCredit.of("Cascade Test Artist"),
                null,
                null,
                null,
                null)
                .publish(NOW);
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldCascadeUnpublishPublicArticleReferencingAlbum(UniAsserter asserter) {
        final var album = newPublishedAlbum("Cascade Target Album");
        asserter.execute(() -> albumRepository.save(album));

        final var article = Article.create(
                ArticleType.ALBUM,
                album.id(),
                new ArticleTitle("Cascade Target Article"),
                null,
                null)
                .publish(NOW);
        asserter.execute(() -> articleRepository.save(article));

        asserter.assertThat(
                () -> unpublishAlbumService.execute(new UnpublishAlbumInput(album.id().value())),
                output -> {
                    assertThat(output.published()).isFalse();
                    assertThat(output.cascadeUnpublishedArticles()).hasSize(1);
                    assertThat(output.cascadeUnpublishedArticles().getFirst().articleId())
                            .isEqualTo(article.id().value());
                });

        asserter.assertThat(
                () -> articleRepository.findById(article.id()),
                found -> assertThat(found.isPublic()).isFalse());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldNotCascadeWhenNoArticleReferencesAlbum(UniAsserter asserter) {
        final var album = newPublishedAlbum("No Reference Album");
        asserter.execute(() -> albumRepository.save(album));

        asserter.assertThat(
                () -> unpublishAlbumService.execute(new UnpublishAlbumInput(album.id().value())),
                output -> assertThat(output.cascadeUnpublishedArticles()).isEmpty());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldNotCascadeWhenReferencingArticleIsAlreadyDraft(UniAsserter asserter) {
        final var album = newPublishedAlbum("Draft Article Reference Album");
        asserter.execute(() -> albumRepository.save(album));

        final var article = Article.create(
                ArticleType.ALBUM,
                album.id(),
                new ArticleTitle("Still Draft Article"),
                null,
                null);
        asserter.execute(() -> articleRepository.save(article));

        asserter.assertThat(
                () -> unpublishAlbumService.execute(new UnpublishAlbumInput(album.id().value())),
                output -> assertThat(output.cascadeUnpublishedArticles()).isEmpty());
    }
}
