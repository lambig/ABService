package com.abservice.application.service.tune;

import com.abservice.application.service.CommandService;
import com.abservice.application.service.InputPolicy;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.credit.Credit;
import com.abservice.domain.model.vo.tune.TuneKind;
import com.abservice.domain.model.vo.tune.TuneTitle;
import com.abservice.lib.Result;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

import org.jspecify.annotations.Nullable;

/**
 * チューン作成コマンドの入力DTO
 *
 * <p>
 * 外部（REST 等）からの未検証入力を表現します。すべての値は文字列（またはInteger）として受け取り、 検証と型への解釈は 入力自身が
 * {@code Result} 経由で行います。
 * </p>
 *
 * @param title
 *            チューンタイトル（必須・空不可）
 * @param tuneKind
 *            チューン種別（{@code com.abservice.domain.model.vo.tune.TuneKind}
 *            の列挙子名。必須）
 * @param defaultComposerCredit
 *            デフォルト作曲者クレジット（nullable）
 * @param defaultArrangerCredit
 *            デフォルトアレンジャークレジット（nullable）
 * @param originalWorkTitle
 *            原曲タイトル（nullable）
 * @param originalWorkCredit
 *            原曲クレジット（nullable）
 * @param tuneType
 *            チューンタイプ（nullable。例: リール、ジグ）
 * @param defaultKey
 *            デフォルトキー（nullable）
 * @param defaultTempo
 *            デフォルトテンポ（nullable。単位はBPM）
 */
public record CreateTuneInput(
        @Nullable String title,
        @Nullable String tuneKind,
        @Nullable String defaultComposerCredit,
        @Nullable String defaultArrangerCredit,
        @Nullable String originalWorkTitle,
        @Nullable String originalWorkCredit,
        @Nullable String tuneType,
        @Nullable String defaultKey,
        @Nullable Integer defaultTempo) implements CommandService.Input<CreateTuneInput> {
    @Override
    public Policy<CreateTuneInput> policy() {
        return new InputPolicy<>(CreateTuneInput::parsed);
    }

    Result<Values> parsed() {
        return Result.zip(
                TuneTitle.fromInput(title())
                        .mapErrorFields(field -> "title"),
                TuneKind.fromInput(tuneKind()),
                resolveCredit(defaultComposerCredit())
                        .mapErrorFields(field -> "defaultComposerCredit"),
                TitleKindComposer::new)
                .flatMap(composer -> arranger(composer, defaultArrangerCredit()));
    }

    private static Result<Values> arranger(TitleKindComposer composer, @Nullable String credit) {
        return resolveCredit(credit)
                .mapErrorFields(field -> "defaultArrangerCredit")
                .map(
                        arranger -> new Values(
                                composer.title(),
                                composer.tuneKind(),
                                composer.defaultComposerCredit(),
                                arranger));
    }

    record Values(
            TuneTitle title,
            TuneKind tuneKind,
            Optional<Credit> composer,
            Optional<Credit> arranger) {
    }

    private record TitleKindComposer(
            TuneTitle title,
            TuneKind tuneKind,
            Optional<Credit> defaultComposerCredit) {
    }

    private static Result<Optional<Credit>> resolveCredit(@Nullable String value) {
        return Optional.ofNullable(value)
                .filter(StringUtils::isNotBlank)
                .map(
                        v -> Credit.fromInput(v)
                                .map(Optional::of))
                .orElseGet(() -> Result.<Optional<Credit>>success(Optional.empty()));
    }

}
