package com.abservice.domain.model.aggregate.article;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.article.AlbumReferenceLostReason;
import com.abservice.domain.model.vo.article.ArticleTitle;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("記事とアルバムの公開整合の規則（Policy単体での評価）のテスト")
class ArticlePublicationPolicyTest {

    private static final BusinessDateTime NOW = BusinessDateTime.of(Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    @DisplayName("参照を持たない記事は公開できる")
    void articleWithoutReferenceIsPublishable() {
        final var result = ArticlePublicationPolicy.publishable()
                .check(new ArticlePublicationPolicy.PublicationTarget(article(ArticleType.NOTE), null));

        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("公開中のアルバムを参照する記事は公開できる")
    void articleReferencingPublishedAlbumIsPublishable() {
        final var album = publishedAlbum();

        final var result = ArticlePublicationPolicy.publishable()
                .check(
                        new ArticlePublicationPolicy.PublicationTarget(
                                articleReferencing(album),
                                album));

        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("非公開のアルバムを参照する記事は公開できない")
    void articleReferencingUnpublishedAlbumIsNotPublishable() {
        final var album = album();

        final var result = ArticlePublicationPolicy.publishable()
                .check(
                        new ArticlePublicationPolicy.PublicationTarget(
                                articleReferencing(album),
                                album));

        assertThat(result.errors())
                .singleElement()
                .satisfies(error -> assertThat(error.code()).isEqualTo("ARTICLE_REFERENCED_ALBUM_NOT_PUBLISHED"));
    }

    @Test
    @DisplayName("参照先を引けなかった記事は公開できない")
    void articleWithMissingReferencedAlbumIsNotPublishable() {
        final var result = ArticlePublicationPolicy.publishable()
                .check(
                        new ArticlePublicationPolicy.PublicationTarget(
                                articleReferencing(publishedAlbum()),
                                null));

        assertThat(result.errors())
                .singleElement()
                .satisfies(error -> assertThat(error.code()).isEqualTo("ARTICLE_REFERENCED_ALBUM_NOT_PUBLISHED"));
    }

    @Test
    @DisplayName("参照が失効した記事は公開できない")
    void articleWithLostReferenceIsNotPublishable() {
        final var result = ArticlePublicationPolicy.publishable()
                .check(
                        new ArticlePublicationPolicy.PublicationTarget(
                                articleWithLostReference(),
                                null));

        assertThat(result.errors())
                .anySatisfy(error -> assertThat(error.code()).isEqualTo("ARTICLE_ALBUM_REFERENCE_LOST"));
    }

    @Test
    @DisplayName("下書きの記事には非公開のアルバムを紐付けられる")
    void draftArticleAcceptsUnpublishedAlbum() {
        final var result = ArticlePublicationPolicy.attachable()
                .check(
                        new ArticlePublicationPolicy.AttachmentTarget(
                                article(ArticleType.ALBUM),
                                album()));

        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("公開中の記事には非公開のアルバムを紐付けられない")
    void publishedArticleRejectsUnpublishedAlbum() {
        final var result = ArticlePublicationPolicy.attachable()
                .check(
                        new ArticlePublicationPolicy.AttachmentTarget(
                                publishedArticle(),
                                album()));

        assertThat(result.errors())
                .singleElement()
                .satisfies(error -> assertThat(error.code()).isEqualTo("ARTICLE_PUBLISHED_ALBUM_NOT_PUBLISHED"));
    }

    @Test
    @DisplayName("公開中の記事に公開中のアルバムは紐付けられる")
    void publishedArticleAcceptsPublishedAlbum() {
        final var result = ArticlePublicationPolicy.attachable()
                .check(
                        new ArticlePublicationPolicy.AttachmentTarget(
                                publishedArticle(),
                                publishedAlbum()));

        assertThat(result.errors()).isEmpty();
    }

    private static Album album() {
        return Album.create(
                AlbumTitle.of("公開整合テストアルバム"),
                BusinessDate.of(
                        2026,
                        1,
                        1),
                ArtistCredit.of("テストアーティスト"),
                null,
                null,
                null,
                null);
    }

    private static Album publishedAlbum() {
        return album().publish(NOW);
    }

    private static Article article(ArticleType articleType) {
        return Article.create(
                articleType,
                null,
                ArticleTitle.of("公開整合テスト記事"),
                null,
                null);
    }

    private static Article publishedArticle() {
        return article(ArticleType.ALBUM).publish(NOW);
    }

    private static Article articleReferencing(Album album) {
        return article(ArticleType.ALBUM).setAlbumId(album.id(), NOW);
    }

    private static Article articleWithLostReference() {
        return articleReferencing(album())
                .loseAlbumReference(
                        AlbumReferenceLostReason.ALBUM_DELETED,
                        NOW);
    }
}
