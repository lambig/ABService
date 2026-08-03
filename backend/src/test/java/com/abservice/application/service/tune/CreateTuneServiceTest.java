package com.abservice.application.service.tune;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CreateTuneService.validate（入力検証の集約）のテスト")
class CreateTuneServiceTest {

    @Test
    @DisplayName("正常な入力は成功しTuneを生成する")
    void validInputSucceeds() {
        final var result = CreateTuneService.validate(
                new CreateTuneInput(
                        "アイリッシュ・ワッシャーウーマン",
                        "TRAD",
                        "Trad.",
                        null,
                        null,
                        null,
                        "ジグ",
                        "D",
                        120));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve().title().value()).isEqualTo("アイリッシュ・ワッシャーウーマン");
        assertThat(result.resolve().tuneKind().name()).isEqualTo("TRAD");
        assertThat(result.resolve().defaultComposerCredit().value()).isEqualTo("Trad.");
        assertThat(result.resolve().defaultArrangerCredit()).isNull();
    }

    @Test
    @DisplayName("タイトルと種別が不正なら両方のエラーを集約する")
    void invalidTitleAndKindAggregatesErrors() {
        final var result = CreateTuneService.validate(
                new CreateTuneInput(
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

    @Test
    @DisplayName("クレジット未入力は成功しdefaultComposerCredit/defaultArrangerCreditはnull")
    void blankCreditsSucceedWithNullCredits() {
        final var result = CreateTuneService.validate(
                new CreateTuneInput(
                        "曲名",
                        "ORIGINAL",
                        "   ",
                        "",
                        null,
                        null,
                        null,
                        null,
                        null));

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve().defaultComposerCredit()).isNull();
        assertThat(result.resolve().defaultArrangerCredit()).isNull();
    }

    @Test
    @DisplayName("クレジットが最大長を超えるとエラー")
    void tooLongCreditFails() {
        final var result = CreateTuneService.validate(
                new CreateTuneInput(
                        "曲名",
                        "ORIGINAL",
                        "a".repeat(256),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<?>) result).errors().stream().map(ErrorResult::code).toList())
                .contains("CREDIT_TOO_LONG");
    }
}
