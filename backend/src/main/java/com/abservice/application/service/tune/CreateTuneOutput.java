package com.abservice.application.service.tune;

import com.abservice.application.service.CommandService;

/**
 * チューン作成コマンドの出力DTO
 *
 * <p>
 * 生成されたチューンのうち、呼び出し側（presentation 層）が応答に必要とする最小限の情報を返します。 ドメインオブジェクトを直接公開しません。
 * </p>
 *
 * @param tuneId
 *            生成されたチューンのID（UUIDv7形式の文字列）
 * @param title
 *            チューンタイトル
 * @param tuneKind
 *            チューン種別（列挙子名）
 */
public record CreateTuneOutput(
        String tuneId,
        String title,
        String tuneKind) implements CommandService.Output {
}
