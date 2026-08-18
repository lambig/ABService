package com.abservice.application.service.tune;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.tune.TuneKind;
import com.abservice.domain.model.vo.tune.TuneTitle;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpdateTuneService.validateAndApply（更新差分適用の集約）のテスト")
class UpdateTuneServiceTest {

    private static Tune existingTune() {
        return Tune.create(
                TuneTitle.of("元のタイトル"),
                TuneKind.TRAD,
                Credit.of("元の作曲者"),
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    @DisplayName("正常な入力は成功し全フィールドを置換する")
    void validInputSucceeds() {
        final var updated = UpdateTuneService.validateAndApply(
                existingTune(),
                new UpdateTuneInput(
                        null,
                        "新タイトル",
                        "ORIGINAL",
                        "新作曲者",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null))
                .resolve();

        assertThat(updated.title().value()).isEqualTo("新タイトル");
        assertThat(updated.tuneKind()).isEqualTo(TuneKind.ORIGINAL);
        assertThat(updated.defaultComposerCredit().value()).isEqualTo("新作曲者");
    }

    @Test
    @DisplayName("既存のIDは維持される")
    void idIsPreserved() {
        final var existing = existingTune();

        final var updated = UpdateTuneService.validateAndApply(
                existing,
                new UpdateTuneInput(
                        null,
                        "新タイトル",
                        "TRAD",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null))
                .resolve();

        assertThat(updated.id()).isEqualTo(existing.id());
    }

    @Test
    @DisplayName("タイトルとチューン種別が不正なら両方のエラーを集約する")
    void invalidTitleAndKindAggregatesErrors() {
        final var result = UpdateTuneService.validateAndApply(
                existingTune(),
                new UpdateTuneInput(
                        null,
                        "   ",
                        "BAD",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("TUNE_TITLE_REQUIRED", "TUNE_KIND_INVALID");
    }
}
