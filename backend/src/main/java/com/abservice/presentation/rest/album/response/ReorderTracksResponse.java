package com.abservice.presentation.rest.album.response;

import java.util.List;

/**
 * トラック順序変更レスポンス（REST の公開出力契約）
 *
 * @param albumId
 *            対象アルバムID
 * @param tracks
 *            変更後の順序で並んだトラック（トラック番号順）
 */
public record ReorderTracksResponse(String albumId, List<TrackOrderEntryResponse> tracks) {

    /**
     * 順序変更後の1トラックの要約
     *
     * @param trackId
     *            トラックID
     * @param trackNo
     *            変更後のトラック番号
     */
    public record TrackOrderEntryResponse(String trackId, int trackNo) {
    }
}
