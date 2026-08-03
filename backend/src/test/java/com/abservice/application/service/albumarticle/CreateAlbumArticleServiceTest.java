package com.abservice.application.service.albumarticle;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.application.service.albumarticle.CreateAlbumArticleInput.DistributionInput;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CreateAlbumArticleService.validate（入力検証の集約）のテスト")
class CreateAlbumArticleServiceTest {

    private static final String VALID_ALBUM_ID = Album.Id.generate().value();

    @Test
    @DisplayName("正常な入力は成功しAlbumArticleを生成する")
    void validInputSucceeds() {
        final var result = CreateAlbumArticleService.validate(
                new CreateAlbumArticleInput(
                        VALID_ALBUM_ID,
                        "記事本文",
                        "お品書き用コメント",
                        "東X-00b",
                        "NEW",
                        new DistributionInput(
                                1000,
                                500,
                                "https://example.com/demo",
                                "補足")));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve().id().value()).isEqualTo(VALID_ALBUM_ID);
        assertThat(result.resolve().introShort()).isEqualTo("お品書き用コメント");
        assertThat(result.resolve().labelTag().name()).isEqualTo("NEW");
        assertThat(result.resolve().distribution().getPhysicalPrice().amount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("albumIdが未指定ならエラー")
    void blankAlbumIdFails() {
        final var result = CreateAlbumArticleService.validate(
                new CreateAlbumArticleInput(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("ALBUM_ID_REQUIRED");
    }

    @Test
    @DisplayName("albumIdの形式が不正ならエラー")
    void invalidAlbumIdFails() {
        final var result = CreateAlbumArticleService.validate(
                new CreateAlbumArticleInput(
                        "not-a-uuid",
                        null,
                        null,
                        null,
                        null,
                        null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("ALBUM_ID_INVALID");
    }

    @Test
    @DisplayName("labelTag・distribution未指定でも成功しnullとして扱われる")
    void blankOptionalFieldsSucceedWithNulls() {
        final var result = CreateAlbumArticleService.validate(
                new CreateAlbumArticleInput(
                        VALID_ALBUM_ID,
                        null,
                        null,
                        null,
                        null,
                        null));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve().labelTag()).isNull();
        assertThat(result.resolve().distribution()).isNull();
    }

    @Test
    @DisplayName("labelTagが不正な値ならエラー")
    void invalidLabelTagFails() {
        final var result = CreateAlbumArticleService.validate(
                new CreateAlbumArticleInput(
                        VALID_ALBUM_ID,
                        null,
                        null,
                        null,
                        "BAD",
                        null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("LABEL_TAG_INVALID");
    }

    @Test
    @DisplayName("頒布情報の価格が負の値ならエラー")
    void negativeDistributionPriceFails() {
        final var result = CreateAlbumArticleService.validate(
                new CreateAlbumArticleInput(
                        VALID_ALBUM_ID,
                        null,
                        null,
                        null,
                        null,
                        new DistributionInput(
                                -100,
                                null,
                                null,
                                null)));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("AMOUNT_NEGATIVE");
    }

    @Test
    @DisplayName("頒布情報のデモURLが不正な形式ならエラー")
    void invalidDemoUrlFails() {
        final var result = CreateAlbumArticleService.validate(
                new CreateAlbumArticleInput(
                        VALID_ALBUM_ID,
                        null,
                        null,
                        null,
                        null,
                        new DistributionInput(
                                null,
                                null,
                                "not a url",
                                null)));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("URL_INVALID_FORMAT");
    }
}
