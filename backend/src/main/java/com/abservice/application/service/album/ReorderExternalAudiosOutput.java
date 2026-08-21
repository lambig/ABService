package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import java.util.List;

/**
 * 外部音源順序変更コマンドの出力DTO
 *
 * @param albumId
 *            対象アルバムID
 * @param externalAudios
 *            変更後の順序で並んだ外部音源（表示順の昇順）
 */
public record ReorderExternalAudiosOutput(
        String albumId,
        List<ExternalAudioOrderEntry> externalAudios) implements CommandService.Output {

    /**
     * 順序変更後の1件の要約
     *
     * @param externalAudioId
     *            外部音源ID
     * @param displayOrder
     *            変更後の表示順
     */
    public record ExternalAudioOrderEntry(String externalAudioId, int displayOrder) {
    }
}
