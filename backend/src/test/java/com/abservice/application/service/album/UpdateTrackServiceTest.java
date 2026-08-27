package com.abservice.application.service.album;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.model.aggregate.album.Track;
import com.abservice.domain.model.aggregate.album.TrackTune;
import com.abservice.domain.model.vo.album.TrackTitle;
import com.abservice.domain.model.vo.album.TrackTuneTitle;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpdateTrackService.validate（入力検証の集約とチューン構成の置換）のテスト")
class UpdateTrackServiceTest {

    private static Track existingTrackWithTune() {
        final var track = Track.create(
                1,
                new TrackTitle("旧タイトル"),
                null);
        return track.addTune(
                TrackTune.create(
                        1,
                        null,
                        TrackTuneTitle.of("旧チューン"),
                        null,
                        null,
                        null));
    }

    @Test
    @DisplayName("正常な入力は成功し、既存Trackのidを引き継いでチューン構成を入力の内容へ置き換える")
    void validInputSucceedsAndReplacesTunes() {
        final var existing = existingTrackWithTune();

        final var result = UpdateTrackService.validate(
                new UpdateTrackInput(
                        "album-id",
                        existing.id().value(),
                        2,
                        "新タイトル",
                        "アーティスト名",
                        null,
                        List.of(
                                new TrackTuneInput(
                                        1,
                                        "新チューン1",
                                        "Trad.",
                                        null,
                                        null),
                                new TrackTuneInput(
                                        2,
                                        "新チューン2",
                                        null,
                                        null,
                                        null))),
                existing);

        assertThat(result).isInstanceOf(Result.Success.class);
        final var updated = result.resolve();
        assertThat(updated.id()).isEqualTo(existing.id());
        assertThat(updated.trackNo()).isEqualTo(2);
        assertThat(updated.title().value()).isEqualTo("新タイトル");
        assertThat(updated.artistCredit().displayName().value()).isEqualTo("アーティスト名");
        assertThat(updated.getTunes()).hasSize(2);
        assertThat(updated.getTunes().getFirst().tuneTitle().value()).isEqualTo("新チューン1");
        assertThat(updated.getTunes().getFirst().composerCreditOverride().value()).isEqualTo("Trad.");
        assertThat(updated.getTunes().getLast().seq()).isEqualTo(2);
    }

    @Test
    @DisplayName("チューン構成が未指定なら構成なしへ置き換わる")
    void unspecifiedTunesClearsExistingTunes() {
        final var existing = existingTrackWithTune();

        final var result = UpdateTrackService.validate(
                new UpdateTrackInput(
                        "album-id",
                        existing.id().value(),
                        1,
                        "新タイトル",
                        null,
                        null,
                        null),
                existing);

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve().getTunes()).isEmpty();
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
                        null),
                existing);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("TRACK_NO_REQUIRED", "TRACK_TITLE_REQUIRED");
    }

    @Test
    @DisplayName("チューン構成のseqが重複していればエラーになる")
    void duplicatedTuneSeqFails() {
        final var existing = existingTrackWithTune();

        final var result = UpdateTrackService.validate(
                new UpdateTrackInput(
                        "album-id",
                        existing.id().value(),
                        1,
                        "新タイトル",
                        null,
                        null,
                        List.of(
                                new TrackTuneInput(
                                        1,
                                        "チューン1",
                                        null,
                                        null,
                                        null),
                                new TrackTuneInput(
                                        1,
                                        "チューン2",
                                        null,
                                        null,
                                        null))),
                existing);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("TUNE_SEQ_DUPLICATE");
    }
}
