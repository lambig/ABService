package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * アルバム作成コマンドの入力DTO
 *
 * <p>
 * 外部（REST 等）からの未検証入力を表現します。すべての値は文字列として受け取り、 検証と型への解釈は
 * {@link CreateAlbumService} が {@code Result} 経由で行います。
 * </p>
 *
 * <p>
 * トラックの追加は Album 集約の {@code addTrack} で別途行うため、本コマンドの対象外です（横展開フェーズで別コマンドとして実装）。
 * イベント頒布情報（{@code eventReleasedAt}）も同様に対象外です。
 * </p>
 *
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
 */
public record CreateAlbumInput(
        @Nullable String title,
        @Nullable String releaseDate,
        @Nullable String artistDisplayName,
        @Nullable String artistSortKey,
        @Nullable String catalogNumber,
        @Nullable String isdn) implements CommandService.Input {
}
