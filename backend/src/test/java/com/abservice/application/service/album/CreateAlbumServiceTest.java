package com.abservice.application.service.album;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CreateAlbumService.validate（入力検証の集約）のテスト")
class CreateAlbumServiceTest {

    @Test
    @DisplayName("正常な入力は成功しAlbumを生成する")
    void validInputSucceeds() {
        final var result = CreateAlbumService.validate(
                new CreateAlbumInput(
                        "アルバムタイトル",
                        "2026-01-01",
                        "アーティスト名",
                        null,
                        "ABC-0001",
                        null));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve().title().value()).isEqualTo("アルバムタイトル");
        assertThat(result.resolve().releaseDate().asLocalDate().toString()).isEqualTo("2026-01-01");
        assertThat(result.resolve().artistCredit().displayName().value()).isEqualTo("アーティスト名");
        assertThat(result.resolve().catalogNumber().value()).isEqualTo("ABC-0001");
        assertThat(result.resolve().isdn()).isNull();
    }

    @Test
    @DisplayName("タイトル・リリース日・アーティスト名が不正なら全てのエラーを集約する")
    void invalidRequiredFieldsAggregatesErrors() {
        final var result = CreateAlbumService.validate(
                new CreateAlbumInput(
                        "   ",
                        "not-a-date",
                        "   ",
                        null,
                        null,
                        null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains(
                        "ALBUM_TITLE_REQUIRED",
                        "ALBUM_RELEASE_DATE_INVALID",
                        "ARTIST_CREDIT_NAME_REQUIRED");
    }

    @Test
    @DisplayName("リリース日が未指定ならエラー")
    void blankReleaseDateFails() {
        final var result = CreateAlbumService.validate(
                new CreateAlbumInput(
                        "アルバムタイトル",
                        null,
                        "アーティスト名",
                        null,
                        null,
                        null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("ALBUM_RELEASE_DATE_REQUIRED");
    }

    @Test
    @DisplayName("カタログナンバー・ISDNが未入力でも成功しnullとして扱われる")
    void blankOptionalFieldsSucceedWithNulls() {
        final var result = CreateAlbumService.validate(
                new CreateAlbumInput(
                        "アルバムタイトル",
                        "2026-01-01",
                        "アーティスト名",
                        null,
                        "   ",
                        ""));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve().catalogNumber()).isNull();
        assertThat(result.resolve().isdn()).isNull();
    }

    @Test
    @DisplayName("ISDNのフォーマットが不正ならエラー")
    void invalidIsdnFails() {
        final var result = CreateAlbumService.validate(
                new CreateAlbumInput(
                        "アルバムタイトル",
                        "2026-01-01",
                        "アーティスト名",
                        null,
                        null,
                        "0000000000000"));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("ISDN_INVALID_FORMAT");
    }
}
