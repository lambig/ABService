package com.abservice.application.service.tune;

import com.abservice.application.service.CommandService;

/**
 * チューン更新コマンドの出力DTO
 *
 * <p>
 * 更新されたチューンのうち、呼び出し側（presentation 層）が応答に必要とする最小限の情報を返します。 ドメインオブジェクトを直接公開しません。
 * </p>
 *
 * @param tuneId
 *            チューンID（UUIDv7形式の文字列）
 * @param title
 *            チューンタイトル
 * @param tuneKind
 *            チューン種別（列挙子名）
 */
public record UpdateTuneOutput(
        String tuneId,
        String title,
        String tuneKind) implements CommandService.Output {
}
