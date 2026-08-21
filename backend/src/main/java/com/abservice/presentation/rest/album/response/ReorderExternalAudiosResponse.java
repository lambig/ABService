package com.abservice.presentation.rest.album.response;

import java.util.List;

/**
 * 外部音源順序変更レスポンス（REST の公開出力契約）
 *
 * @param albumId
 *            対象アルバムID
 * @param externalAudios
 *            変更後の順序で並んだ外部音源（表示順の昇順）
 */
public record ReorderExternalAudiosResponse(String albumId, List<ExternalAudioOrderEntryResponse> externalAudios) {

    /**
     * 順序変更後の1件の要約
     *
     * @param externalAudioId
     *            外部音源ID
     * @param displayOrder
     *            変更後の表示順
     */
    public record ExternalAudioOrderEntryResponse(String externalAudioId, int displayOrder) {
    }
}
