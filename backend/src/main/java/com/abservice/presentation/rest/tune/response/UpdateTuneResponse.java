package com.abservice.presentation.rest.tune.response;

/**
 * チューン更新レスポンス（REST の公開出力契約）
 *
 * @param tuneId
 *            更新されたチューンのID（UUIDv7形式の文字列）
 * @param title
 *            チューンタイトル
 * @param tuneKind
 *            チューン種別（列挙子名）
 */
public record UpdateTuneResponse(
        String tuneId,
        String title,
        String tuneKind) {
}
