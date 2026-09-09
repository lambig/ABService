package com.abservice.presentation.rest.article.response;

/**
 * 記事更新レスポンス（REST の公開出力契約）
 *
 * @param articleId
 *            更新された記事のID（UUIDv7形式の文字列）
 * @param revision
 *            保存後の世代。同じ画面で続けて編集する場合、次の更新はこれを {@code expectedRevision} として送る
 *            （DECISIONS 30）
 * @param articleType
 *            記事種別（列挙子名）
 * @param title
 *            記事タイトル
 * @param publicFlag
 *            公開フラグ（Updateでは変更しない）
 */
public record UpdateArticleResponse(
        String articleId,
        int revision,
        String articleType,
        String title,
        boolean publicFlag) {
}
