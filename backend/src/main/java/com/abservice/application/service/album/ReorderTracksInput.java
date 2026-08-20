package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * トラック順序変更コマンドの入力DTO
 *
 * @param albumId
 *            対象アルバムID
 * @param orderedTrackIds
 *            新しい順序で並べたトラックIDのリスト（アルバムが持つ全トラックを1件ずつ含む必要がある）
 */
public record ReorderTracksInput(
        @Nullable String albumId,
        @Nullable List<@Nullable String> orderedTrackIds) implements CommandService.Input {
}
