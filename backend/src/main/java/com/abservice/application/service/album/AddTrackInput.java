package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * トラック追加コマンドの入力DTO
 *
 * @param albumId
 *            追加先のアルバムID
 * @param trackNo
 *            トラック番号
 * @param title
 *            トラックタイトル
 * @param artistDisplayName
 *            アーティスト表示名（nullable。未指定時はAlbumのartistCreditを継承）
 * @param artistSortKey
 *            アーティストソートキー（nullable）
 * @param recordingDate
 *            録音日（ISO-8601形式の文字列。nullable）
 * @param recordingPlace
 *            録音場所（nullable）
 * @param isLive
 *            ライブ録音フラグ（nullable）
 */
public record AddTrackInput(
        @Nullable String albumId,
        @Nullable Integer trackNo,
        @Nullable String title,
        @Nullable String artistDisplayName,
        @Nullable String artistSortKey,
        @Nullable String recordingDate,
        @Nullable String recordingPlace,
        @Nullable Boolean isLive) implements CommandService.Input {
}
