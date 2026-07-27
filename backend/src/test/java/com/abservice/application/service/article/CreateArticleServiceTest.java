package com.abservice.application.service.article;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.model.vo.article.MarkupContent;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CreateArticleService.validate（入力検証の集約）のテスト")
class CreateArticleServiceTest {

    @Test
    @DisplayName("正常な入力は成功しArticleを生成する")
    void validInputSucceeds() {
        final var result = CreateArticleService.validate(
                new CreateArticleInput(
                        "NOTE",
                        "タイトル",
                        "本文",
                        "MARKDOWN",
                        "概要"));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve().title().value()).isEqualTo("タイトル");
        assertThat(result.resolve().articleType().name()).isEqualTo("NOTE");
    }

    @Test
    @DisplayName("記事種別とタイトルが不正なら両方のエラーを集約する")
    void invalidTypeAndTitleAggregatesErrors() {
        final var result = CreateArticleService.validate(
                new CreateArticleInput(
                        "BAD",
                        "   ",
                        null,
                        null,
                        null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("ARTICLE_TITLE_REQUIRED", "ARTICLE_TYPE_INVALID");
    }

    @Test
    @DisplayName("本文ありで形式未指定なら形式必須エラー")
    void bodyWithoutFormatFails() {
        final var result = CreateArticleService.validate(
                new CreateArticleInput(
                        "NOTE",
                        "タイトル",
                        "本文",
                        null,
                        null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("MARKUP_FORMAT_REQUIRED");
    }

    @Test
    @DisplayName("本文が空白なら形式未指定でも成功し本文はEMPTY")
    void blankBodySucceedsWithoutFormat() {
        final var result = CreateArticleService.validate(
                new CreateArticleInput(
                        "NOTE",
                        "タイトル",
                        "   ",
                        null,
                        null));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve().body()).isEqualTo(MarkupContent.EMPTY);
    }
}
