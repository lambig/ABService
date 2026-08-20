package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;

/**
 * トラック更新コマンドの出力DTO
 *
 * @param albumId
 *            更新対象トラックが属するアルバムID
 * @param trackId
 *            更新されたトラックのID
 * @param trackNo
 *            トラック番号
 * @param title
 *            トラックタイトル
 */
public record UpdateTrackOutput(
        String albumId,
        String trackId,
        int trackNo,
        String title) implements CommandService.Output {
}
