package com.abservice.presentation.rest.article.response;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * アルバムへの参照を持たない記事の詳細レスポンス（REST の公開出力契約）
 *
 * <p>
 * {@code NOTE} / {@code NEWS} / {@code EVENT} / {@code OTHER}
 * が対象。いずれも項目名の集合が同一のため、 種別ごとに型を分けない。アルバム参照に関わる項目名はここに現れない（その概念を持たないため）。
 * </p>
 *
 * @param articleId
 *            記事ID（UUIDv7形式の文字列）
 * @param articleType
 *            記事種別（列挙子名）
 * @param title
 *            記事タイトル
 * @param body
 *            記事本文（空文字列は本文なし。nullは返さない）
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
 */
public record PlainArticleResponse(
        String articleId,
        String articleType,
        String title,
        String body,
        String bodyFormat,
        @Nullable String introShort,
        @Nullable Instant publishedAt,
        @Nullable Instant updatedAtBusiness,
        boolean publicFlag) implements ArticleResponse {
}
