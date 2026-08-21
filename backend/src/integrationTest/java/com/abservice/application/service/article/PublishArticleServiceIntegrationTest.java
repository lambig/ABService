package com.abservice.application.service.article;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.exception.BusinessRuleViolationException;
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
 * PublishArticleServiceの集約横断ビジネスルール（非公開Albumを参照する記事は公開できない）の統合テスト
 *
 * <p>
 * ここではリポジトリを直接使って前提データ（アルバム記事）を組み立て、
 * {@code ArticleAlbumReferencePolicy}経由の検証ロジックを単体・高速に検証する。REST経由での
 * 同等の疎通確認（{@code PUT .../album}によるアルバム紐付けを含む）は
 * {@code ArticleRestIntegrationTest}が検証する。
 * </p>
 */
@QuarkusTest
class PublishArticleServiceIntegrationTest {

    private static final BusinessDateTime NOW = BusinessDateTime.of(Instant.parse("2024-01-01T00:00:00Z"));

    @Inject
    private PublishArticleService publishArticleService;

    @Inject
    private ArticleRepositoryImpl articleRepository;

    @Inject
    private AlbumRepositoryImpl albumRepository;

    private static Album newDraftAlbum(String title) {
        return Album.create(
                new AlbumTitle(title),
                BusinessDate.of(
                        LocalDate.of(
                                2024,
                                1,
                                1)),
                ArtistCredit.of("Cross Aggregate Test Artist"),
                null,
                null,
                null,
                null);
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldFailWhenReferencedAlbumIsDraft(UniAsserter asserter) {
        final var album = newDraftAlbum("Draft Referenced Album");
        asserter.execute(() -> albumRepository.save(album));

        final var article = Article.create(
                ArticleType.ALBUM,
                album.id(),
                new ArticleTitle("Album Intro Article"),
                null,
                null);
        asserter.execute(() -> articleRepository.save(article));

        asserter.assertFailedWith(
                () -> publishArticleService.execute(new PublishArticleInput(article.id().value())),
                BusinessRuleViolationException.class);
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldPublishWhenReferencedAlbumIsPublished(UniAsserter asserter) {
        final var album = newDraftAlbum("Published Referenced Album").publish(NOW);
        asserter.execute(() -> albumRepository.save(album));

        final var article = Article.create(
                ArticleType.ALBUM,
                album.id(),
                new ArticleTitle("Album Intro Article"),
                null,
                null);
        asserter.execute(() -> articleRepository.save(article));

        asserter.assertThat(
                () -> publishArticleService.execute(new PublishArticleInput(article.id().value())),
                output -> assertThat(output.publicFlag()).isTrue());
    }

    @Test
    @TestReactiveTransaction
    @RunOnVertxContext
    void shouldPublishNoteArticleWithoutAlbumCheck(UniAsserter asserter) {
        final var article = Article.create(
                ArticleType.NOTE,
                null,
                new ArticleTitle("Plain Note Article"),
                null,
                null);
        asserter.execute(() -> articleRepository.save(article));

        asserter.assertThat(
                () -> publishArticleService.execute(new PublishArticleInput(article.id().value())),
                output -> assertThat(output.publicFlag()).isTrue());
    }
}
