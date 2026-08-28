package com.abservice.presentation.rest.article.response;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * 管理向け記事一覧の1件分レスポンス（REST の公開出力契約）
 *
 * <p>
 * 管理画面の記事一覧が使う項目だけを持つ。一覧は記事を選ぶための表示であり、本文・ショート紹介文・アルバムへの参照は
 * 詳細（{@link AdminArticleDetailResponse}）で返す。参照に関わる項目名を持たないため、種別で型を分けない。
 * </p>
 *
 * @param articleId
 *            記事ID（UUIDv7形式の文字列）
 * @param articleType
 *            記事種別（列挙子名）
 * @param title
 *            記事タイトル
 * @param publishedAt
 *            公開日時（nullable。null は下書き。UTC）
 * @param updatedAtBusiness
 *            業務上の更新日時（nullable。UTC）
 * @param publicFlag
 *            公開フラグ
 */
public record AdminArticleResponse(
        String articleId,
        String articleType,
        String title,
        @Nullable Instant publishedAt,
        @Nullable Instant updatedAtBusiness,
        boolean publicFlag) {
}
