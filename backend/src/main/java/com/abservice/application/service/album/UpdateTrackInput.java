package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * トラック更新コマンドの入力DTO
 *
 * @param albumId
 *            更新対象トラックが属するアルバムID
 * @param trackId
 *            更新対象のトラックID
 * @param trackNo
 *            トラック番号
 * @param title
 *            トラックタイトル
 * @param artistDisplayName
 *            アーティスト表示名（nullable。未指定時はAlbumのartistCreditを継承）
 * @param artistSortKey
 *            アーティストソートキー（nullable）
 */
public record UpdateTrackInput(
        @Nullable String albumId,
        @Nullable String trackId,
        @Nullable Integer trackNo,
        @Nullable String title,
        @Nullable String artistDisplayName,
        @Nullable String artistSortKey) implements CommandService.Input {
}
