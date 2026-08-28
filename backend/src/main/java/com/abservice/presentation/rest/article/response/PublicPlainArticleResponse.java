package com.abservice.presentation.rest.article.response;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * 公開向けの、アルバムへの参照を持たない記事の一覧レスポンス（REST の公開出力契約）
 *
 * <p>
 * {@code NOTE} / {@code NEWS} / {@code EVENT} / {@code OTHER}
 * が対象。いずれも項目名の集合が同一のため、種別ごとに型を分けない。
 * </p>
 *
 * @param articleId
 *            記事ID（UUIDv7形式の文字列）
 * @param articleType
 *            記事種別（列挙子名）
 * @param title
 *            記事タイトル
 * @param introShort
 *            一覧表示用のショート紹介文（nullable）
 * @param publishedAt
 *            公開日時（UTC。実際には null にならない）
 */
public record PublicPlainArticleResponse(
        String articleId,
        String articleType,
        String title,
        @Nullable String introShort,
        @Nullable Instant publishedAt) implements PublicArticleResponse {
}
