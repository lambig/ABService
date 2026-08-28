package com.abservice.presentation.rest.article.response;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 公開向けアルバム紹介記事の詳細レスポンス（REST の公開出力契約）
 *
 * <p>
 * 参照先アルバムへの導線を出すための {@code albumId} を持つ唯一の種別。参照の失効は公開中の記事には起こり得ないため
 * （非公開化されてから失効する）、失効に関わる項目名は持たない。
 * </p>
 *
 * @param articleId
 *            記事ID（UUIDv7形式の文字列）
 * @param articleType
 *            記事種別（列挙子名。常に {@code ALBUM}）
 * @param title
 *            記事タイトル
 * @param body
 *            記事本文（空文字列は本文なし。nullは返さない）
 * @param bodyFormat
 *            本文のマークアップ形式（列挙子名）
 * @param publishedAt
 *            公開日時（UTC。公開向けは公開中のものだけを返すため常に値を持つ）
 * @param albumId
 *            参照先アルバムのID（nullable。参照を持たない場合）
 * @param tags
 *            記事に付いたタグ名の一覧（名前の昇順）
 */
public record PublicAlbumArticleDetailResponse(
        String articleId,
        String articleType,
        String title,
        String body,
        String bodyFormat,
        Instant publishedAt,
        @Nullable String albumId,
        List<String> tags) implements PublicArticleDetailResponse {
}
