package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * トラック削除コマンドの入力DTO
 *
 * @param albumId
 *            削除対象トラックが属するアルバムID
 * @param trackId
 *            削除対象のトラックID
 */
public record RemoveTrackInput(@Nullable String albumId, @Nullable String trackId) implements CommandService.Input {
}
