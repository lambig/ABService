package com.abservice.presentation.rest.article.response;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * 記事詳細レスポンス（REST の公開出力契約）
 *
 * @param articleId
 *            記事ID（UUIDv7形式の文字列）
 * @param articleType
 *            記事種別（列挙子名）
 * @param albumId
 *            アルバムID（nullable）
 * @param title
 *            記事タイトル
 * @param body
 *            記事本文（nullable）
 * @param bodyFormat
 *            本文のマークアップ形式（列挙子名）
 * @param introShort
 *            一覧表示用のショート紹介文（nullable）
 * @param publishedAt
 *            公開日時（nullable。UTC）
 * @param updatedAtBusiness
 *            業務上の更新日時（nullable。UTC）
 * @param publicFlag
 *            公開フラグ
 * @param formerAlbumId
 *            失効した参照先アルバムのID（nullable。参照が失効している場合のみ）
 * @param albumReferenceLostAt
 *            アルバム参照が失効した日時（nullable。UTC）
 * @param albumReferenceLostReason
 *            失効の理由コード（nullable。表示文言はクライアントが決める）
 */
public record ArticleResponse(
        String articleId,
        String articleType,
        @Nullable String albumId,
        String title,
        @Nullable String body,
        String bodyFormat,
        @Nullable String introShort,
        @Nullable Instant publishedAt,
        @Nullable Instant updatedAtBusiness,
        boolean publicFlag,
        @Nullable String formerAlbumId,
        @Nullable Instant albumReferenceLostAt,
        @Nullable String albumReferenceLostReason) {
}
