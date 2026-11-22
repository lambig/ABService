package com.abservice.application.service.tune;

import com.abservice.application.service.CommandService;
import com.abservice.application.service.InputPolicy;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.tune.Tune;
import com.abservice.lib.Result;
import io.smallrye.mutiny.Uni;
import org.jspecify.annotations.Nullable;

/**
 * チューン更新コマンドの入力DTO
 *
 * <p>
 * 外部（REST 等）からの未検証入力を表現します。すべての値は文字列（またはInteger）として受け取り、 検証と型への解釈は
 * {@link UpdateTuneService} が {@code Result} 経由で行います。
 * </p>
 *
 * @param tuneId
 *            更新対象のチューンID
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
public record UpdateTuneInput(
        @Nullable String tuneId,
        @Nullable String title,
        @Nullable String tuneKind,
        @Nullable String defaultComposerCredit,
        @Nullable String defaultArrangerCredit,
        @Nullable String originalWorkTitle,
        @Nullable String originalWorkCredit,
        @Nullable String tuneType,
        @Nullable String defaultKey,
        @Nullable Integer defaultTempo) implements CommandService.Input<UpdateTuneInput> {
    @Override
    public Policy<UpdateTuneInput> policy() {
        return new InputPolicy<>(UpdateTuneInput::validatedValues);
    }

    private Result<CreateTuneInput.Values> validatedValues() {
        return requested()
                .flatMap(_ -> bodyValues());
    }

    Result<CreateTuneInput.Values> bodyValues() {
        return new CreateTuneInput(
                title(),
                tuneKind(),
                defaultComposerCredit(),
                defaultArrangerCredit(),
                originalWorkTitle(),
                originalWorkCredit(),
                tuneType(),
                defaultKey(),
                defaultTempo())
                .parsed();
    }

    Uni<Tune.Id> asRequested() {
        return Uni.createFrom()
                .item(
                        () -> requested()
                                .resolve(ValidationException::new));
    }

    private Result<Tune.Id> requested() {
        return Tune.Id.fromInput(tuneId())
                .withErrorField("tuneId");
    }
}
