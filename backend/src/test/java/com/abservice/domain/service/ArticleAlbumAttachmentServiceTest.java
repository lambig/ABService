package com.abservice.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.AlbumArticle;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.album.AlbumTitle;
import com.abservice.domain.model.vo.article.ArticleTitle;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.common.ArtistCredit;
import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.model.vo.common.MarkupContent;
import com.abservice.domain.service.ArticleAlbumAttachmentService.AlbumAttachment;
import com.abservice.domain.service.ArticlePublicationService.ArticlePublication;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("アルバム紐付けの操作オブジェクト（規則の単体評価と遷移）のテスト")
class ArticleAlbumAttachmentServiceTest {

    private static final BusinessDateTime NOW = BusinessDateTime.of(Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    @DisplayName("下書きの記事には非公開のアルバムを紐付けられる")
    void draftArticleAcceptsUnpublishedAlbum() {
        final var attachment = new AlbumAttachment(article(), album());

        assertThat(attachment.asValidated().errors()).isEmpty();
        assertThat(attachment.attach(NOW).albumReference().activeAlbumId()).isPresent();
    }

    @Test
    @DisplayName("公開中の記事には非公開のアルバムを紐付けられない")
    void publishedArticleRejectsUnpublishedAlbum() {
        final var attachment = new AlbumAttachment(publishedArticle(), album());

        assertThat(attachment.asValidated().errors())
                .singleElement()
                .satisfies(error -> assertThat(error.code()).isEqualTo("ARTICLE_PUBLISHED_ALBUM_NOT_PUBLISHED"));
        assertThatThrownBy(() -> attachment.attach(NOW))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("公開中の記事に公開中のアルバムは紐付けられる")
    void publishedArticleAcceptsPublishedAlbum() {
        final var attachment = new AlbumAttachment(publishedArticle(), publishedAlbum());

        assertThat(attachment.asValidated().errors()).isEmpty();
    }

    private static Album album() {
        return Album.create(
                AlbumTitle.of("紐付け整合テストアルバム"),
                BusinessDate.of(
                        2026,
                        1,
                        1),
                ArtistCredit.of("テストアーティスト"),
                MarkupContent.EMPTY,
                null,
                null,
                null,
                null);
    }

    private static Album publishedAlbum() {
        return album().publish(NOW);
    }

    private static AlbumArticle article() {
        return AlbumArticle.from(
                Article.create(
                        ArticleType.ALBUM,
                        null,
                        ArticleTitle.of("紐付け整合テスト記事"),
                        null,
                        null,
                        NOW))
                .orElseThrow();
    }

    private static AlbumArticle publishedArticle() {
        return AlbumArticle.from(new ArticlePublication(article(), null).publish(NOW))
                .orElseThrow();
    }
}
