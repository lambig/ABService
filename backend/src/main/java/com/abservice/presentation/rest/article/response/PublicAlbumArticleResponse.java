package com.abservice.presentation.rest.article.response;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * 公開向けアルバム紹介記事の一覧レスポンス（REST の公開出力契約）
 *
 * <p>
 * カードが参照先アルバムのカバー画像を出すため、一覧でも {@code albumId} を返す。画像そのものは記事一覧では揃えず、
 * クライアントがアルバム側から取得する。
 * </p>
 *
 * @param articleId
 *            記事ID（UUIDv7形式の文字列）
 * @param articleType
 *            記事種別（列挙子名。常に {@code ALBUM}）
 * @param title
 *            記事タイトル
 * @param introShort
 *            一覧表示用のショート紹介文（nullable）
 * @param publishedAt
 *            公開日時（UTC。公開向けは公開中のものだけを返すため常に値を持つ）
 * @param albumId
 *            参照先アルバムのID（nullable。参照を持たない場合）
 */
public record PublicAlbumArticleResponse(
        String articleId,
        String articleType,
        String title,
        @Nullable String introShort,
        Instant publishedAt,
        @Nullable String albumId) implements PublicArticleResponse {
}
