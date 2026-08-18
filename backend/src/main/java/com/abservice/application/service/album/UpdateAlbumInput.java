package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * アルバム更新コマンドの入力DTO
 *
 * <p>
 * 外部（REST 等）からの未検証入力を表現します。すべての値は文字列として受け取り、 検証と型への解釈は
 * {@link UpdateAlbumService} が {@code Result} 経由で行います。
 * </p>
 *
 * <p>
 * トラック（{@code tracks}）はUpdate対象外です（専用の{@code addTrack}等で別途行う）。
 * </p>
 *
 * @param albumId
 *            更新対象のアルバムID
 * @param title
 *            アルバムタイトル（必須・空不可）
 * @param releaseDate
 *            リリース日（ISO-8601形式の文字列。例: "2026-01-01"。必須）
 * @param artistDisplayName
 *            アーティスト表示名（必須・空不可）
 * @param artistSortKey
 *            アーティストソートキー（nullable。未指定の場合は表示名を使用）
 * @param catalogNumber
 *            カタログナンバー（nullable）
 * @param isdn
 *            ISDN（nullable。ハイフンは省略可）
 * @param event
 *            初出イベント情報（nullable）
 */
public record UpdateAlbumInput(
        @Nullable String albumId,
        @Nullable String title,
        @Nullable String releaseDate,
        @Nullable String artistDisplayName,
        @Nullable String artistSortKey,
        @Nullable String catalogNumber,
        @Nullable String isdn,
        @Nullable EventInput event) implements CommandService.Input {

    /**
     * 初出イベント情報の入力DTO
     *
     * @param name
     *            イベント名（必須・空不可）
     * @param date
     *            開催日（ISO-8601形式の文字列。nullable）
     * @param place
     *            会場（nullable）
     * @param spaceNumber
     *            スペース番号（nullable）
     * @param note
     *            補足情報（nullable）
     */
    public record EventInput(
            @Nullable String name,
            @Nullable String date,
            @Nullable String place,
            @Nullable String spaceNumber,
            @Nullable String note) {
    }
}
