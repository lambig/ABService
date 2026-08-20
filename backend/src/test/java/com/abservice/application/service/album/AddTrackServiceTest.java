package com.abservice.application.service.album;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AddTrackService.validate（入力検証の集約）のテスト")
class AddTrackServiceTest {

    @Test
    @DisplayName("正常な入力は成功しTrackを生成する")
    void validInputSucceeds() {
        final var result = AddTrackService.validate(
                new AddTrackInput(
                        "album-id",
                        1,
                        "トラックタイトル",
                        "アーティスト名",
                        null,
                        "2026-01-01",
                        "会場",
                        true));

        assertThat(result).isInstanceOf(Result.Success.class);
        final var track = result.resolve();
        assertThat(track.trackNo()).isEqualTo(1);
        assertThat(track.title().value()).isEqualTo("トラックタイトル");
        assertThat(track.artistCredit().displayName().value()).isEqualTo("アーティスト名");
        assertThat(track.recordingDate().asLocalDate().toString()).isEqualTo("2026-01-01");
        assertThat(track.recordingPlace()).isEqualTo("会場");
        assertThat(track.isLive()).isTrue();
        assertThat(track.getTunes()).isEmpty();
    }

    @Test
    @DisplayName("トラック番号・タイトルが不正なら全てのエラーを集約する")
    void invalidRequiredFieldsAggregatesErrors() {
        final var result = AddTrackService.validate(
                new AddTrackInput(
                        "album-id",
                        null,
                        "   ",
                        null,
                        null,
                        null,
                        null,
                        null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("TRACK_NO_REQUIRED", "TRACK_TITLE_REQUIRED");
    }

    @Test
    @DisplayName("アーティスト名・録音日が未指定でも成功しnullとして扱われる")
    void blankOptionalFieldsSucceedWithNulls() {
        final var result = AddTrackService.validate(
                new AddTrackInput(
                        "album-id",
                        1,
                        "トラックタイトル",
                        "   ",
                        null,
                        "",
                        null,
                        null));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve().artistCredit()).isNull();
        assertThat(result.resolve().recordingDate()).isNull();
    }

    @Test
    @DisplayName("録音日の形式が不正ならエラー")
    void invalidRecordingDateFails() {
        final var result = AddTrackService.validate(
                new AddTrackInput(
                        "album-id",
                        1,
                        "トラックタイトル",
                        null,
                        null,
                        "not-a-date",
                        null,
                        null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("TRACK_RECORDING_DATE_INVALID");
    }

    @Test
    @DisplayName("録音日が不正な場合、trackNo等の必須項目検証には進まず録音日のエラーのみが返る（2段階検証の仕様）")
    void invalidRecordingDateShortCircuitsBeforeRequiredFieldCheck() {
        final var result = AddTrackService.validate(
                new AddTrackInput(
                        "album-id",
                        null,
                        "トラックタイトル",
                        null,
                        null,
                        "not-a-date",
                        null,
                        null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .containsExactly("TRACK_RECORDING_DATE_INVALID");
    }
}
