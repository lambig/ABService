package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;

/**
 * トラック追加コマンドの出力DTO
 *
 * @param albumId
 *            追加先のアルバムID
 * @param trackId
 *            追加されたトラックのID
 * @param trackNo
 *            トラック番号
 * @param title
 *            トラックタイトル
 */
public record AddTrackOutput(
        String albumId,
        String trackId,
        int trackNo,
        String title) implements CommandService.Output {
}
