package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;

/**
 * トラック削除コマンドの出力DTO
 *
 * @param albumId
 *            削除対象トラックが属していたアルバムID
 * @param trackId
 *            削除されたトラックのID
 */
public record RemoveTrackOutput(String albumId, String trackId) implements CommandService.Output {
}
