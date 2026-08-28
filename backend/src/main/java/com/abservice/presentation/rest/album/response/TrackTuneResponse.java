package com.abservice.presentation.rest.album.response;

import org.jspecify.annotations.Nullable;

/**
 * トラック内のチューン構成1件（REST の公開出力契約）
 *
 * <p>
 * 項目の集合は公開向けと管理向けで同じため、要求元で型を分けない。公開サイトはチューン名とクレジットを曲目に並べ、管理画面は 同じ値を編集する。
 * </p>
 *
 * <p>
 * チューンIDは返さない。{@code Tune} マスタとの同定を行わないため、常に値を持たない項目になる。
 * </p>
 *
 * @param seq
 *            トラック内での登場順（1, 2, 3, ...）
 * @param tuneTitle
 *            チューン名（nullable）
 * @param composerCreditOverride
 *            作曲者クレジット（nullable）
 * @param arrangerCreditOverride
 *            アレンジャークレジット（nullable）
 * @param linkUrl
 *            リンクURL（nullable）
 */
public record TrackTuneResponse(
        int seq,
        @Nullable String tuneTitle,
        @Nullable String composerCreditOverride,
        @Nullable String arrangerCreditOverride,
        @Nullable String linkUrl) {
}
