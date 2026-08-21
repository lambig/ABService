package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 外部音源順序変更コマンドの入力DTO
 *
 * @param albumId
 *            対象アルバムID
 * @param orderedExternalAudioIds
 *            新しい順序で並べた外部音源IDのリスト（アルバムが持つ全件を1件ずつ含む必要がある）
 */
public record ReorderExternalAudiosInput(
        @Nullable String albumId,
        @Nullable List<@Nullable String> orderedExternalAudioIds) implements CommandService.Input {
}
