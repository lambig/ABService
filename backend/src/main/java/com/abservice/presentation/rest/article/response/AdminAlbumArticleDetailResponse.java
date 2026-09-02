package com.abservice.presentation.rest.article.response;

import java.time.Instant;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.jspecify.annotations.Nullable;

/**
 * 管理向けアルバム紹介記事の詳細レスポンス（REST の公開出力契約）
 *
 * <p>
 * アルバムへの参照に関わる4項目を持つ唯一の種別。参照の状態（なし・有効・失効）は値の組み合わせで表す。
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
 * @param introShort
 *            一覧表示用のショート紹介文（空文字列は紹介文なし）
 * @param publishedAt
 *            公開日時（nullable。null は下書き。UTC）
 * @param updatedAtBusiness
 *            業務上の更新日時（nullable。UTC）
 * @param publicFlag
 *            公開フラグ
 * @param albumId
 *            参照先アルバムのID（nullable。参照を持たない、または失効している場合）
 * @param formerAlbumId
 *            失効した参照先アルバムのID（nullable。参照が失効している場合のみ）
 * @param albumReferenceLostAt
 *            アルバム参照が失効した日時（nullable。UTC）
 * @param albumReferenceLostReason
 *            失効の理由コード（nullable。表示文言はクライアントが決める）
 * @param tags
 *            記事に付いたタグの一覧（名前の昇順）
 */
@Schema(properties = @SchemaProperty(name = "articleType", enumeration = "ALBUM"))
public record AdminAlbumArticleDetailResponse(
        String articleId,
        String articleType,
        String title,
        String body,
        String bodyFormat,
        String introShort,
        @Nullable Instant publishedAt,
        @Nullable Instant updatedAtBusiness,
        boolean publicFlag,
        @Nullable String albumId,
        @Nullable String formerAlbumId,
        @Nullable Instant albumReferenceLostAt,
        @Nullable String albumReferenceLostReason,
        List<AdminArticleTagResponse> tags) implements AdminArticleDetailResponse {
}
