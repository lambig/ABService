package com.abservice.application.service.album;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.application.service.album.CreateAlbumInput.EventInput;
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
                        null,
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
                        "",
                        null));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve().catalogNumber()).isNull();
        assertThat(result.resolve().isdn()).isNull();
        assertThat(result.resolve().eventReleasedAt()).isNull();
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
                        "0000000000000",
                        null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("ISDN_INVALID_FORMAT");
    }

    @Test
    @DisplayName("初出イベント情報を指定すると成功しeventReleasedAtに反映される")
    void validEventSucceeds() {
        final var result = CreateAlbumService.validate(
                new CreateAlbumInput(
                        "アルバムタイトル",
                        "2026-01-01",
                        "アーティスト名",
                        null,
                        null,
                        null,
                        new EventInput(
                                "コミックマーケット104",
                                "2026-01-01",
                                "東京ビッグサイト",
                                "東ホ-01a",
                                "新譜あります")));

        assertThat(result).isInstanceOf(Result.Success.class);
        final var event = result.resolve().eventReleasedAt();
        assertThat(event).isNotNull();
        assertThat(event.name().value()).isEqualTo("コミックマーケット104");
        assertThat(event.date().asLocalDate().toString()).isEqualTo("2026-01-01");
        assertThat(event.place()).isEqualTo("東京ビッグサイト");
        assertThat(event.spaceNumber()).isEqualTo("東ホ-01a");
        assertThat(event.note()).isEqualTo("新譜あります");
    }

    @Test
    @DisplayName("初出イベントの日付形式が不正ならエラー")
    void invalidEventDateFails() {
        final var result = CreateAlbumService.validate(
                new CreateAlbumInput(
                        "アルバムタイトル",
                        "2026-01-01",
                        "アーティスト名",
                        null,
                        null,
                        null,
                        new EventInput(
                                "コミックマーケット104",
                                "not-a-date",
                                null,
                                null,
                                null)));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("ALBUM_EVENT_DATE_INVALID");
    }

    @Test
    @DisplayName("必須項目とISDNの両方が不正なら両方のエラーを集約する（zipによる独立検証の集約）")
    void invalidRequiredFieldAndIsdnAggregatesErrorsAcrossGroups() {
        final var result = CreateAlbumService.validate(
                new CreateAlbumInput(
                        "   ",
                        "2026-01-01",
                        "アーティスト名",
                        null,
                        null,
                        "0000000000000",
                        null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("ALBUM_TITLE_REQUIRED", "ISDN_INVALID_FORMAT");
    }

    @Test
    @DisplayName("初出イベント名が未指定ならエラー")
    void blankEventNameFails() {
        final var result = CreateAlbumService.validate(
                new CreateAlbumInput(
                        "アルバムタイトル",
                        "2026-01-01",
                        "アーティスト名",
                        null,
                        null,
                        null,
                        new EventInput(
                                "   ",
                                null,
                                null,
                                null,
                                null)));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("EVENT_NAME_REQUIRED");
    }
}
