package com.abservice.presentation.rest.tune.response;

/**
 * チューン作成レスポンス（REST の公開出力契約）
 *
 * @param tuneId
 *            生成されたチューンのID（UUIDv7形式の文字列）
 * @param title
 *            チューンタイトル
 * @param tuneKind
 *            チューン種別（列挙子名）
 */
public record CreateTuneResponse(
        String tuneId,
        String title,
        String tuneKind) {
}
