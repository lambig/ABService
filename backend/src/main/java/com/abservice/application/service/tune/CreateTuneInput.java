package com.abservice.application.service.tune;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * チューン作成コマンドの入力DTO
 *
 * <p>
 * 外部（REST 等）からの未検証入力を表現します。すべての値は文字列（またはInteger）として受け取り、 検証と型への解釈は
 * {@link CreateTuneService} が {@code Result} 経由で行います。
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
        @Nullable Integer defaultTempo) implements CommandService.Input {
}
