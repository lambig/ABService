package com.abservice.application.service.album;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.model.aggregate.album.TrackTune;
import com.abservice.domain.model.vo.album.TrackTitle;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpdateTrackService.validate（入力検証の集約とtunes引き継ぎ）のテスト")
class UpdateTrackServiceTest {

    private static Track existingTrackWithTune() {
        final var track = Track.create(
                1,
                new TrackTitle("旧タイトル"),
                null,
                null,
                null,
                null);
        return track.addTune(
                TrackTune.create(
                        1,
                        null,
                        null,
                        null,
                        null));
    }

    @Test
    @DisplayName("正常な入力は成功し、既存Trackのidとtunesを引き継いだTrackを生成する")
    void validInputSucceedsAndPreservesIdAndTunes() {
        final var existing = existingTrackWithTune();

        final var result = UpdateTrackService.validate(
                new UpdateTrackInput(
                        "album-id",
                        existing.id().value(),
                        2,
                        "新タイトル",
                        "アーティスト名",
                        null,
                        "2026-01-01",
                        "会場",
                        true),
                existing);

        assertThat(result).isInstanceOf(Result.Success.class);
        final var updated = result.resolve();
        assertThat(updated.id()).isEqualTo(existing.id());
        assertThat(updated.trackNo()).isEqualTo(2);
        assertThat(updated.title().value()).isEqualTo("新タイトル");
        assertThat(updated.artistCredit().displayName().value()).isEqualTo("アーティスト名");
        assertThat(updated.recordingDate().asLocalDate().toString()).isEqualTo("2026-01-01");
        assertThat(updated.getTunes()).hasSize(1);
        assertThat(updated.getTunes().getFirst()).isEqualTo(existing.getTunes().getFirst());
    }

    @Test
    @DisplayName("トラック番号・タイトルが不正なら全てのエラーを集約する")
    void invalidRequiredFieldsAggregatesErrors() {
        final var existing = existingTrackWithTune();

        final var result = UpdateTrackService.validate(
                new UpdateTrackInput(
                        "album-id",
                        existing.id().value(),
                        null,
                        "   ",
                        null,
                        null,
                        null,
                        null,
                        null),
                existing);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("TRACK_NO_REQUIRED", "TRACK_TITLE_REQUIRED");
    }

    @Test
    @DisplayName("録音日の形式が不正ならエラー")
    void invalidRecordingDateFails() {
        final var existing = existingTrackWithTune();

        final var result = UpdateTrackService.validate(
                new UpdateTrackInput(
                        "album-id",
                        existing.id().value(),
                        1,
                        "新タイトル",
                        null,
                        null,
                        "not-a-date",
                        null,
                        null),
                existing);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("TRACK_RECORDING_DATE_INVALID");
    }
}
