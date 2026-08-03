package com.abservice.presentation.rest.tune.request;

import org.jspecify.annotations.Nullable;

/**
 * チューン作成リクエスト（REST の公開入力契約）
 *
 * <p>
 * 外部からの未検証入力。値検証はアプリケーション層（各値オブジェクトの {@code fromInput}）に委譲する。
 * </p>
 *
 * @param title
 *            チューンタイトル
 * @param tuneKind
 *            チューン種別（列挙子名）
 * @param defaultComposerCredit
 *            デフォルト作曲者クレジット（nullable）
 * @param defaultArrangerCredit
 *            デフォルトアレンジャークレジット（nullable）
 * @param originalWorkTitle
 *            原曲タイトル（nullable）
 * @param originalWorkCredit
 *            原曲クレジット（nullable）
 * @param tuneType
 *            チューンタイプ（nullable）
 * @param defaultKey
 *            デフォルトキー（nullable）
 * @param defaultTempo
 *            デフォルトテンポ（nullable。単位はBPM）
 */
public record CreateTuneRequest(
        @Nullable String title,
        @Nullable String tuneKind,
        @Nullable String defaultComposerCredit,
        @Nullable String defaultArrangerCredit,
        @Nullable String originalWorkTitle,
        @Nullable String originalWorkCredit,
        @Nullable String tuneType,
        @Nullable String defaultKey,
        @Nullable Integer defaultTempo) {
}
