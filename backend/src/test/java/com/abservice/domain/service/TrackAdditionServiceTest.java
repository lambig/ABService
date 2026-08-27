package com.abservice.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.service.TrackAdditionService.TrackFields;
import com.abservice.domain.service.TrackAdditionService.TuneFields;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.List;
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
                        null,
                        null));

        assertThat(result).isInstanceOf(Result.Success.class);
        final var track = result.resolve();
        assertThat(track.trackNo()).isEqualTo(1);
        assertThat(track.title().value()).isEqualTo("トラックタイトル");
        assertThat(track.artistCredit().displayName().value()).isEqualTo("アーティスト名");
        assertThat(track.getTunes()).isEmpty();
    }

    @Test
    @DisplayName("チューン構成を含む入力は成功し、順序どおりのチューン構成を持つTrackを生成する")
    void inputWithTunesSucceeds() {
        final var result = TrackAdditionService.validate(
                new TrackFields(
                        1,
                        "トラックタイトル",
                        null,
                        null,
                        List.of(
                                new TuneFields(
                                        1,
                                        "チューン1",
                                        "Trad.",
                                        null,
                                        null),
                                new TuneFields(
                                        2,
                                        "チューン2",
                                        null,
                                        "Arranger",
                                        "https://example.com"))));

        assertThat(result).isInstanceOf(Result.Success.class);
        final var tunes = result.resolve().getTunes();
        assertThat(tunes).hasSize(2);
        assertThat(tunes.getFirst().seq()).isEqualTo(1);
        assertThat(tunes.getFirst().tuneTitle().value()).isEqualTo("チューン1");
        assertThat(tunes.getFirst().composerCreditOverride().value()).isEqualTo("Trad.");
        assertThat(tunes.getFirst().tuneId()).isNull();
        assertThat(tunes.getLast().arrangerCreditOverride().value()).isEqualTo("Arranger");
        assertThat(tunes.getLast().linkUrl().value()).isEqualTo("https://example.com");
    }

    @Test
    @DisplayName("トラック番号・タイトルが不正なら全てのエラーを集約する")
    void invalidRequiredFieldsAggregatesErrors() {
        final var result = TrackAdditionService.validate(
                new TrackFields(
                        null,
                        "   ",
                        null,
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
                        null,
                        null));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve().artistCredit()).isNull();
    }

    @Test
    @DisplayName("チューン構成のseqが重複していればエラーになる")
    void duplicatedTuneSeqFails() {
        final var result = TrackAdditionService.validate(
                new TrackFields(
                        1,
                        "トラックタイトル",
                        null,
                        null,
                        List.of(
                                new TuneFields(
                                        1,
                                        "チューン1",
                                        null,
                                        null,
                                        null),
                                new TuneFields(
                                        1,
                                        "チューン2",
                                        null,
                                        null,
                                        null))));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("TUNE_SEQ_DUPLICATE");
    }

    @Test
    @DisplayName("チューン構成のseqが0以下ならエラーになる")
    void nonPositiveTuneSeqFails() {
        final var result = TrackAdditionService.validate(
                new TrackFields(
                        1,
                        "トラックタイトル",
                        null,
                        null,
                        List.of(
                                new TuneFields(
                                        0,
                                        "チューン1",
                                        null,
                                        null,
                                        null))));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("SEQ_NOT_POSITIVE");
    }

    @Test
    @DisplayName("チューン構成の各行のエラーは集約される")
    void tuneRowErrorsAreAggregated() {
        final var result = TrackAdditionService.validate(
                new TrackFields(
                        1,
                        "トラックタイトル",
                        null,
                        null,
                        List.of(
                                new TuneFields(
                                        null,
                                        "チューン1",
                                        null,
                                        null,
                                        null),
                                new TuneFields(
                                        2,
                                        "a".repeat(256),
                                        null,
                                        null,
                                        null))));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("SEQ_REQUIRED", "TRACK_TUNE_TITLE_TOO_LONG");
    }
}
