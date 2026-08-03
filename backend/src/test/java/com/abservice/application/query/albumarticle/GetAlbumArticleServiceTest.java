package com.abservice.application.query.albumarticle;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.infrastructure.persistence.entity.AlbumArticleTableRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetAlbumArticleService.toResult（結果分岐）のテスト")
class GetAlbumArticleServiceTest {

    @Test
    @DisplayName("エンティティありはFoundを返す")
    void entityYieldsFound() {
        final var entity = new AlbumArticleTableRecord();
        entity.setDomainId("0192f8a0-0000-7000-8000-000000000000");
        entity.setIntroShort("お品書き用コメント");

        final var result = GetAlbumArticleService.toResult(entity);

        assertThat(result).isInstanceOf(GetAlbumArticleResult.Found.class);
        assertThat(((GetAlbumArticleResult.Found) result).article().introShort()).isEqualTo("お品書き用コメント");
    }

    @Test
    @DisplayName("nullはNotFoundを返す")
    void nullYieldsNotFound() {
        assertThat(GetAlbumArticleService.toResult(null)).isInstanceOf(GetAlbumArticleResult.NotFound.class);
    }
}
