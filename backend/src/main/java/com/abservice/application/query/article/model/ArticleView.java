package com.abservice.application.query.article.model;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * 記事詳細の Read Model DTO
 *
 * <p>
 * Query 側（CQRS Read）が {@code infrastructure.persistence.datasource}
 * 経由で取得した記事を、読み取り専用の
 * 表現として保持します。ドメインオブジェクトではなく、照会結果をそのまま外部（presentation）へ渡すための平坦な DTO です。
 * </p>
 *
 * <p>
 * タグは記事タグ連携（#39）が未実装のため本 DTO には含めません（連携実装時に追加）。
 * </p>
 *
 * @param articleId
 *            記事ID（ドメインID・UUIDv7形式の文字列）
 * @param articleType
 *            記事種別（列挙子名）
 * @param albumId
 *            アルバムID（nullable。アルバム記事の場合のみ）
 * @param title
 *            記事タイトル
 * @param body
 *            記事本文（nullable）
 * @param bodyFormat
 *            本文のマークアップ形式（列挙子名）
 * @param introShort
 *            一覧表示用のショート紹介文（nullable）
 * @param publishedAt
 *            公開日時（nullable。UTC の {@link Instant}）
 * @param updatedAtBusiness
 *            業務上の更新日時（nullable。UTC の {@link Instant}）
 * @param publicFlag
 *            公開フラグ
 */
public record ArticleView(
        String articleId,
        String articleType,
        @Nullable String albumId,
        String title,
        @Nullable String body,
        String bodyFormat,
        @Nullable String introShort,
        @Nullable Instant publishedAt,
        @Nullable Instant updatedAtBusiness,
        boolean publicFlag) {
}
