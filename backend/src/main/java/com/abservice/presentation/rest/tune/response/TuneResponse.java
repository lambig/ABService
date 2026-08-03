package com.abservice.presentation.rest.tune.response;

import org.jspecify.annotations.Nullable;

/**
 * チューン詳細レスポンス（REST の公開出力契約）
 *
 * @param tuneId
 *            チューンID（UUIDv7形式の文字列）
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
public record TuneResponse(
        String tuneId,
        String title,
        String tuneKind,
        @Nullable String defaultComposerCredit,
        @Nullable String defaultArrangerCredit,
        @Nullable String originalWorkTitle,
        @Nullable String originalWorkCredit,
        @Nullable String tuneType,
        @Nullable String defaultKey,
        @Nullable Integer defaultTempo) {
}
