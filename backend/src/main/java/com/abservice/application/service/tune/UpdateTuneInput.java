package com.abservice.application.service.tune;

import com.abservice.application.service.CommandService;
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
        @Nullable Integer defaultTempo) implements CommandService.Input {
}
