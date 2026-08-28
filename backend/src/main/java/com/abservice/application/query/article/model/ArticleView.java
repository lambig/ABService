package com.abservice.application.query.article.model;

import java.time.Instant;
import java.util.List;
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
 * タグを埋めるのは詳細照会だけです。一覧照会では空になります（一覧はタグを出さず、タグを引く JOIN も発行しません）。
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
 *            記事本文（空文字列は本文なし。nullは持たない）
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
 * @param formerAlbumId
 *            失効した参照先アルバムのID（nullable。参照が失効している場合のみ）
 * @param albumReferenceLostAt
 *            アルバム参照が失効した日時（nullable。UTC の {@link Instant}）
 * @param albumReferenceLostReason
 *            失効の理由コード（nullable。表示文言は利用側が決める）
 * @param tags
 *            記事に付いたタグの一覧。名前の昇順（詳細照会のみ。一覧照会では空）
 */
public record ArticleView(
        String articleId,
        String articleType,
        @Nullable String albumId,
        String title,
        String body,
        String bodyFormat,
        @Nullable String introShort,
        @Nullable Instant publishedAt,
        @Nullable Instant updatedAtBusiness,
        boolean publicFlag,
        @Nullable String formerAlbumId,
        @Nullable Instant albumReferenceLostAt,
        @Nullable String albumReferenceLostReason,
        List<ArticleTagView> tags) {
}
