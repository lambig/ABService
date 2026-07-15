package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;

/**
 * 記事作成コマンドの出力DTO
 *
 * <p>
 * 生成された記事のうち、呼び出し側（presentation 層）が応答に必要とする最小限の情報を返します。 ドメインオブジェクトを直接公開しません。
 * </p>
 *
 * @param articleId
 *            生成された記事のID（UUIDv7形式の文字列）
 * @param articleType
 *            記事種別（列挙子名）
 * @param title
 *            記事タイトル
 * @param publicFlag
 *            公開フラグ（新規作成時は非公開）
 */
public record CreateArticleOutput(
        String articleId,
        String articleType,
        String title,
        boolean publicFlag) implements CommandService.Output {
}
