package com.abservice.presentation.rest.album.response;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * 管理向けアルバム一覧の1件分レスポンス（REST の公開出力契約）
 *
 * <p>
 * 一覧の責務は作品を選ぶための表示であり、概要説明・外部音源・曲目・アーティストソートキーは詳細
 * （{@link AdminAlbumDetailResponse}）で返す。下書きを含むため {@code publishedAt} は null
 * になり得て、 これが公開状態を表す。
 * </p>
 *
 * @param albumId
 *            アルバムID（UUIDv7形式の文字列）
 * @param title
 *            アルバムタイトル
 * @param releaseDate
 *            リリース日（ISO-8601形式の文字列）
 * @param artistDisplayName
 *            アーティスト表示名
 * @param catalogNumber
 *            カタログナンバー（nullable）
 * @param isdn
 *            ISDN（nullable）
 * @param eventName
 *            初出イベント名（nullable）
 * @param eventDate
 *            初出イベント開催日（ISO-8601形式の文字列。nullable）
 * @param eventPlace
 *            初出イベント会場（nullable）
 * @param eventSpaceNumber
 *            初出イベントスペース番号（nullable）
 * @param eventNote
 *            初出イベント補足情報（nullable）
 * @param publishedAt
 *            公開日時（nullable。null は下書き。UTC）
 * @param coverImageUrl
 *            カバー画像の配信URL（nullable。サイト相対）
 */
public record AdminAlbumResponse(
        String albumId,
        String title,
        String releaseDate,
        String artistDisplayName,
        @Nullable String catalogNumber,
        @Nullable String isdn,
        @Nullable String eventName,
        @Nullable String eventDate,
        @Nullable String eventPlace,
        @Nullable String eventSpaceNumber,
        @Nullable String eventNote,
        @Nullable Instant publishedAt,
        @Nullable String coverImageUrl) {
}
