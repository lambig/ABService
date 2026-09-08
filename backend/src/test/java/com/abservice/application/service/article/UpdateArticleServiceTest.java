package com.abservice.application.service.article;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.entity.article.ArticleTag;
import com.abservice.domain.model.vo.article.ArticleTitle;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.article.IntroShort;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.model.vo.common.MarkupContent;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpdateArticleService.validateAndApply（更新差分適用の集約）のテスト")
class UpdateArticleServiceTest {

    private static final BusinessDateTime NOW = BusinessDateTime.of(Instant.parse("2026-01-01T00:00:00Z"));

    /**
     * 編集を始めた時点の世代。
     *
     * <p>
     * 本テストの対象（{@code validateAndApply}）は世代を見ない。世代の突き合わせは記事を掴む側の責務で、
     * 経路ごと（未指定は400・不一致は409）に統合テストが固定している。
     * </p>
     */
    private static final Integer REVISION_UNUSED = 0;

    private static Article existingArticle() {
        return Article.create(
                ArticleType.NOTE,
                null,
                ArticleTitle.of("元のタイトル"),
                MarkupContent.markdown("元の本文"),
                IntroShort.of("元の概要"),
                NOW);
    }

    @Test
    @DisplayName("正常な入力は成功しCreate相当フィールドを置換する")
    void validInputSucceeds() {
        final var updated = UpdateArticleService.validateAndApply(
                existingArticle(),
                new UpdateArticleInput(
                        null,
                        REVISION_UNUSED,
                        "NOTE",
                        "新タイトル",
                        "新本文",
                        "MARKDOWN",
                        "新概要"),
                NOW).resolve();

        assertThat(updated.title().value()).isEqualTo("新タイトル");
        assertThat(updated.body().content()).isEqualTo("新本文");
        assertThat(updated.introShort().value()).isEqualTo("新概要");
    }

    @Test
    @DisplayName("id・公開状態・タグはUpdateの対象外のため既存の値を維持する")
    void publicFlagAndTagsAreUnaffected() {
        final var existing = existingArticle()
                .publish(NOW)
                .addTag(ArticleTag.create("タグ"));

        final var updated = UpdateArticleService.validateAndApply(
                existing,
                new UpdateArticleInput(
                        null,
                        REVISION_UNUSED,
                        "NOTE",
                        "新タイトル",
                        null,
                        null,
                        null),
                NOW).resolve();

        assertThat(updated.id()).isEqualTo(existing.id());
        assertThat(updated.isPublic()).isTrue();
        assertThat(updated.getTags()).hasSize(1);
    }

    @Test
    @DisplayName("記事種別とタイトルが不正なら両方のエラーを集約する")
    void invalidTypeAndTitleAggregatesErrors() {
        final var result = UpdateArticleService.validateAndApply(
                existingArticle(),
                new UpdateArticleInput(
                        null,
                        REVISION_UNUSED,
                        "BAD",
                        "   ",
                        null,
                        null,
                        null),
                NOW);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("ARTICLE_TITLE_REQUIRED", "ARTICLE_TYPE_INVALID");
    }

    @Test
    @DisplayName("本文ありで形式未指定なら形式必須エラー")
    void bodyWithoutFormatFails() {
        final var result = UpdateArticleService.validateAndApply(
                existingArticle(),
                new UpdateArticleInput(
                        null,
                        REVISION_UNUSED,
                        "NOTE",
                        "タイトル",
                        "本文",
                        null,
                        null),
                NOW);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("MARKUP_FORMAT_REQUIRED");
        assertThat(result.errors().stream().map(ErrorResult::field).toList())
                .containsExactly("bodyFormat");
    }

    @Test
    @DisplayName("本文が空白なら形式未指定でも成功し本文はEMPTY")
    void blankBodySucceedsWithoutFormat() {
        final var result = UpdateArticleService.validateAndApply(
                existingArticle(),
                new UpdateArticleInput(
                        null,
                        REVISION_UNUSED,
                        "NOTE",
                        "タイトル",
                        "   ",
                        null,
                        null),
                NOW);

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve().body()).isEqualTo(MarkupContent.EMPTY);
    }
}
