package com.abservice.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.service.TrackAdditionService.TrackFields;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TrackAdditionService.validate（入力検証の集約）のテスト")
class TrackAdditionServiceTest {

    @Test
    @DisplayName("正常な入力は成功しTrackを生成する")
    void validInputSucceeds() {
        final var result = TrackAdditionService.validate(
                new TrackFields(
                        1,
                        "トラックタイトル",
                        "アーティスト名",
                        null));

        assertThat(result).isInstanceOf(Result.Success.class);
        final var track = result.resolve();
        assertThat(track.trackNo()).isEqualTo(1);
        assertThat(track.title().value()).isEqualTo("トラックタイトル");
        assertThat(track.artistCredit().displayName().value()).isEqualTo("アーティスト名");
        assertThat(track.getTunes()).isEmpty();
    }

    @Test
    @DisplayName("トラック番号・タイトルが不正なら全てのエラーを集約する")
    void invalidRequiredFieldsAggregatesErrors() {
        final var result = TrackAdditionService.validate(
                new TrackFields(
                        null,
                        "   ",
                        null,
                        null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("TRACK_NO_REQUIRED", "TRACK_TITLE_REQUIRED");
    }

    @Test
    @DisplayName("アーティスト名が未指定でも成功しnullとして扱われる")
    void blankOptionalFieldsSucceedWithNulls() {
        final var result = TrackAdditionService.validate(
                new TrackFields(
                        1,
                        "トラックタイトル",
                        "   ",
                        null));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve().artistCredit()).isNull();
    }
}
