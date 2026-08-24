package com.abservice.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.article.AlbumReferenceLostReason;
import com.abservice.domain.model.vo.article.ArticleTitle;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.service.ArticleAlbumAttachmentService.AlbumAttachment;
import com.abservice.domain.service.ArticlePublicationService.ArticlePublication;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("記事公開の操作オブジェクト（規則の単体評価と遷移）のテスト")
class ArticlePublicationServiceTest {

    private static final BusinessDateTime NOW = BusinessDateTime.of(Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    @DisplayName("参照を持たない記事は公開できる")
    void articleWithoutReferenceIsPublishable() {
        final var publication = new ArticlePublication(article(ArticleType.NOTE), null);

        assertThat(publication.asValidated().errors()).isEmpty();
        assertThat(publication.publish(NOW).isPublic()).isTrue();
    }

    @Test
    @DisplayName("公開中のアルバムを参照する記事は公開できる")
    void articleReferencingPublishedAlbumIsPublishable() {
        final var album = publishedAlbum();

        final var publication = new ArticlePublication(articleReferencing(album), album);

        assertThat(publication.asValidated().errors()).isEmpty();
        assertThat(publication.publish(NOW).isPublic()).isTrue();
    }

    @Test
    @DisplayName("非公開のアルバムを参照する記事は公開できない")
    void articleReferencingUnpublishedAlbumIsNotPublishable() {
        final var album = album();

        final var publication = new ArticlePublication(articleReferencing(album), album);

        assertThat(publication.asValidated().errors())
                .singleElement()
                .satisfies(error -> assertThat(error.code()).isEqualTo("ARTICLE_REFERENCED_ALBUM_NOT_PUBLISHED"));
        assertThatThrownBy(() -> publication.publish(NOW))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("参照先を引けなかった記事は公開できない")
    void articleWithMissingReferencedAlbumIsNotPublishable() {
        final var publication = new ArticlePublication(articleReferencing(publishedAlbum()), null);

        assertThat(publication.asValidated().errors())
                .singleElement()
                .satisfies(error -> assertThat(error.code()).isEqualTo("ARTICLE_REFERENCED_ALBUM_NOT_PUBLISHED"));
    }

    @Test
    @DisplayName("参照が失効した記事は公開できない")
    void articleWithLostReferenceIsNotPublishable() {
        final var publication = new ArticlePublication(articleWithLostReference(), null);

        assertThat(publication.asValidated().errors())
                .anySatisfy(error -> assertThat(error.code()).isEqualTo("ARTICLE_ALBUM_REFERENCE_LOST"));
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

    private static Article articleReferencing(Album album) {
        return new AlbumAttachment(article(ArticleType.ALBUM), album).attach(NOW);
    }

    private static Article articleWithLostReference() {
        return articleReferencing(album())
                .loseAlbumReference(
                        AlbumReferenceLostReason.ALBUM_DELETED,
                        NOW);
    }
}
