package com.abservice.application.query.albumarticle;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.infrastructure.persistence.entity.AlbumArticleTableRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AlbumArticleViewMapper（Entity→Read Model 変換）のテスト")
class AlbumArticleViewMapperTest {

    @Test
    @DisplayName("全項目が Read Model に写像される")
    void toViewShouldMapAllFields() {
        // Arrange
        final var entity = new AlbumArticleTableRecord();
        entity.setDomainId("0192f8a0-0000-7000-8000-000000000000");
        entity.setIntroLong("記事本文");
        entity.setIntroShort("お品書き用コメント");
        entity.setFirstEventSpace("東X-00b");
        entity.setLabelTag("NEW");

        // Act
        final var view = AlbumArticleViewMapper.toView(entity);

        // Assert
        assertThat(view.albumId()).isEqualTo("0192f8a0-0000-7000-8000-000000000000");
        assertThat(view.introLong()).isEqualTo("記事本文");
        assertThat(view.introShort()).isEqualTo("お品書き用コメント");
        assertThat(view.firstEventSpace()).isEqualTo("東X-00b");
        assertThat(view.labelTag()).isEqualTo("NEW");
    }

    @Test
    @DisplayName("nullable 項目が null のエンティティも写像できる")
    void toViewShouldMapNullableFields() {
        // Arrange
        final var entity = new AlbumArticleTableRecord();
        entity.setDomainId("0192f8a0-0000-7000-8000-000000000001");

        // Act
        final var view = AlbumArticleViewMapper.toView(entity);

        // Assert
        assertThat(view.introLong()).isNull();
        assertThat(view.introShort()).isNull();
        assertThat(view.firstEventSpace()).isNull();
        assertThat(view.labelTag()).isNull();
    }
}
