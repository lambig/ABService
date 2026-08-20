package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import java.util.List;

/**
 * トラック順序変更コマンドの出力DTO
 *
 * @param albumId
 *            対象アルバムID
 * @param tracks
 *            変更後の順序で並んだトラック（トラック番号順）
 */
public record ReorderTracksOutput(String albumId, List<TrackOrderEntry> tracks) implements CommandService.Output {

    /**
     * 順序変更後の1トラックの要約
     *
     * @param trackId
     *            トラックID
     * @param trackNo
     *            変更後のトラック番号
     */
    public record TrackOrderEntry(String trackId, int trackNo) {
    }
}
