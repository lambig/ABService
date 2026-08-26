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
import com.abservice.domain.model.vo.common.MarkupContent;
import com.abservice.infrastructure.persistence.repository.AlbumRepositoryImpl;
import com.abservice.infrastructure.persistence.repository.ArticleRepositoryImpl;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * UnpublishAlbumServiceのカスケード非公開化（当該アルバムを参照する公開中の記事も連動して非公開化する）の統合テスト
 *
 * <p>
 * 1つのアルバムは複数の記事から参照されうるため、公開・下書きの組み合わせを直接組み立てられるよう、前提データは
 * リポジトリ経由で用意する。単純なアルバム非公開化・404はREST経由で {@code AlbumRestIntegrationTest}が検証する。
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
                MarkupContent.EMPTY,
                null,
                null,
                null,
                null)
                .publish(NOW);
    }

    private static Article newArticleOf(Album album, String title) {
        return Article.create(
                ArticleType.ALBUM,
                album.id(),
                new ArticleTitle(title),
                null,
                null);
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldCascadeUnpublishPublicArticleReferencingAlbum(UniAsserter asserter) {
        final var album = newPublishedAlbum("Cascade Target Album");
        asserter.execute(() -> albumRepository.save(album));

        final var article = newArticleOf(album, "Cascade Target Article").publish(NOW);
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
    void shouldCascadeUnpublishEveryPublicArticleReferencingSameAlbum(UniAsserter asserter) {
        final var album = newPublishedAlbum("Multi Cascade Target Album");
        asserter.execute(() -> albumRepository.save(album));

        final var first = newArticleOf(album, "First Cascade Target Article").publish(NOW);
        final var second = newArticleOf(album, "Second Cascade Target Article").publish(NOW);
        asserter.execute(() -> articleRepository.saveAll(List.of(first, second)));

        asserter.assertThat(
                () -> unpublishAlbumService.execute(new UnpublishAlbumInput(album.id().value())),
                output -> assertThat(output.cascadeUnpublishedArticles())
                        .extracting(UnpublishAlbumOutput.CascadeUnpublishedArticle::articleId)
                        .containsExactlyInAnyOrder(first.id().value(), second.id().value()));

        asserter.assertThat(
                () -> articleRepository.findByAlbumId(album.id()),
                found -> assertThat(found).extracting(Article::isPublic).containsOnly(false));
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldCascadeOnlyPublicArticleWhenDraftReferencesSameAlbum(UniAsserter asserter) {
        final var album = newPublishedAlbum("Mixed State Reference Album");
        asserter.execute(() -> albumRepository.save(album));

        final var published = newArticleOf(album, "Published Article Of Mixed Album").publish(NOW);
        final var draft = newArticleOf(album, "Draft Article Of Mixed Album");
        asserter.execute(() -> articleRepository.saveAll(List.of(published, draft)));

        asserter.assertThat(
                () -> unpublishAlbumService.execute(new UnpublishAlbumInput(album.id().value())),
                output -> assertThat(output.cascadeUnpublishedArticles())
                        .extracting(UnpublishAlbumOutput.CascadeUnpublishedArticle::articleId)
                        .containsExactly(published.id().value()));

        asserter.assertThat(
                () -> articleRepository.findById(draft.id()),
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

        final var article = newArticleOf(album, "Still Draft Article");
        asserter.execute(() -> articleRepository.save(article));

        asserter.assertThat(
                () -> unpublishAlbumService.execute(new UnpublishAlbumInput(album.id().value())),
                output -> assertThat(output.cascadeUnpublishedArticles()).isEmpty());
    }
}
