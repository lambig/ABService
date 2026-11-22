package com.abservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.application.service.article.RemoveArticleTagInput;
import com.abservice.application.service.article.RemoveArticleTagService;
import com.abservice.application.service.site.UpsertSiteContentInput;
import com.abservice.application.service.site.UpsertSiteContentService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.lib.ErrorResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContentCommandInputContractTest {
    @Test
    @DisplayName("タグ解除はIDの複数エラーを購読時に返し、DB取得へ進まない")
    void rejectsTagIdsBeforeReading() {
        final var service = new RemoveArticleTagService(null);
        final var pending = service.execute(new RemoveArticleTagInput("bad", "also-bad"));
        assertThatThrownBy(() -> pending.await().indefinitely())
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        error -> assertThat(error.errors()).extracting(ErrorResult::field)
                                .containsExactly("articleId", "tagId"));
    }

    @Test
    @DisplayName("サイト文言はキーと書式のエラーをまとめて返し、DB取得へ進まない")
    void rejectsSiteValuesBeforeReading() {
        final var service = new UpsertSiteContentService(null);
        final var pending = service.execute(
                new UpsertSiteContentInput(
                        " ",
                        "body",
                        "INVALID"));
        assertThatThrownBy(() -> pending.await().indefinitely())
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        error -> assertThat(error.errors()).extracting(ErrorResult::field)
                                .contains("key", "contentFormat"));
    }

    @Test
    @DisplayName("正常なキー・本文・書式を自己検証できる")
    void acceptsSiteValues() {
        final var input = new UpsertSiteContentInput(
                "site.title",
                "title",
                "PLAIN_TEXT");
        assertThat(input.asValidated().await().indefinitely()).isSameAs(input);
    }
}
