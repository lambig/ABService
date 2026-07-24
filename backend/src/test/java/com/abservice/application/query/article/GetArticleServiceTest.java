package com.abservice.application.query.article;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.infrastructure.persistence.entity.ArticleTableRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetArticleService.toResult（結果分岐）のテスト")
class GetArticleServiceTest {

    @Test
    @DisplayName("エンティティありはFoundを返す")
    void entityYieldsFound() {
        final var entity = new ArticleTableRecord();
        entity.setDomainId("0192f8a0-0000-7000-8000-000000000000");
        entity.setArticleType("NOTE");
        entity.setTitle("タイトル");
        entity.setBodyFormat("PLAIN_TEXT");

        final var result = GetArticleService.toResult(entity);

        assertThat(result).isInstanceOf(GetArticleResult.Found.class);
        assertThat(((GetArticleResult.Found) result).article().title()).isEqualTo("タイトル");
    }

    @Test
    @DisplayName("nullはNotFoundを返す")
    void nullYieldsNotFound() {
        assertThat(GetArticleService.toResult(null)).isInstanceOf(GetArticleResult.NotFound.class);
    }
}
