package com.abservice.application.query.article;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.infrastructure.persistence.entity.ArticleEntity;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ArticleViewMapper（Entity→Read Model 変換）のテスト")
class ArticleViewMapperTest {

    @Test
    @DisplayName("全項目が Read Model に写像される")
    void toViewShouldMapAllFields() {
        // Arrange
        final var publishedAt = Instant.parse("2026-01-01T00:00:00Z");
        final var updatedAt = Instant.parse("2026-02-01T00:00:00Z");
        final var entity = new ArticleEntity();
        entity.setDomainId("0192f8a0-0000-7000-8000-000000000000");
        entity.setArticleType("NOTE");
        entity.setAlbumId("0192f8a0-0000-7000-8000-000000000001");
        entity.setTitle("記事タイトル");
        entity.setBody("本文");
        entity.setBodyFormat("MARKDOWN");
        entity.setIntroShort("概要");
        entity.setPublishedAt(publishedAt);
        entity.setUpdatedAtBusiness(updatedAt);
        entity.setIsPublic(true);

        // Act
        final var view = ArticleViewMapper.toView(entity);

        // Assert
        assertThat(view.articleId()).isEqualTo("0192f8a0-0000-7000-8000-000000000000");
        assertThat(view.articleType()).isEqualTo("NOTE");
        assertThat(view.albumId()).isEqualTo("0192f8a0-0000-7000-8000-000000000001");
        assertThat(view.title()).isEqualTo("記事タイトル");
        assertThat(view.body()).isEqualTo("本文");
        assertThat(view.bodyFormat()).isEqualTo("MARKDOWN");
        assertThat(view.introShort()).isEqualTo("概要");
        assertThat(view.publishedAt()).isEqualTo(publishedAt);
        assertThat(view.updatedAtBusiness()).isEqualTo(updatedAt);
        assertThat(view.publicFlag()).isTrue();
    }

    @Test
    @DisplayName("nullable 項目が null のエンティティも写像できる")
    void toViewShouldMapNullableFields() {
        // Arrange
        final var entity = new ArticleEntity();
        entity.setDomainId("0192f8a0-0000-7000-8000-000000000002");
        entity.setArticleType("NEWS");
        entity.setTitle("ニュース");
        entity.setBodyFormat("PLAIN_TEXT");

        // Act
        final var view = ArticleViewMapper.toView(entity);

        // Assert
        assertThat(view.albumId()).isNull();
        assertThat(view.body()).isNull();
        assertThat(view.introShort()).isNull();
        assertThat(view.publishedAt()).isNull();
        assertThat(view.updatedAtBusiness()).isNull();
    }

    @Test
    @DisplayName("isPublic が null の場合は publicFlag=false")
    void toViewShouldTreatNullIsPublicAsFalse() {
        // Arrange
        final var entity = new ArticleEntity();
        entity.setDomainId("0192f8a0-0000-7000-8000-000000000003");
        entity.setArticleType("OTHER");
        entity.setTitle("その他");
        entity.setBodyFormat("PLAIN_TEXT");
        entity.setIsPublic(null);

        // Act
        final var view = ArticleViewMapper.toView(entity);

        // Assert
        assertThat(view.publicFlag()).isFalse();
    }
}
