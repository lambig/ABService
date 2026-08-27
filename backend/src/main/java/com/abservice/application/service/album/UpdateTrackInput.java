package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * トラック更新コマンドの入力DTO
 *
 * @param albumId
 *            更新対象トラックが属するアルバムID
 * @param trackId
 *            更新対象のトラックID
 * @param trackNo
 *            トラック番号
 * @param title
 *            トラックタイトル
 * @param artistDisplayName
 *            アーティスト表示名（nullable。未指定時はAlbumのartistCreditを継承）
 * @param artistSortKey
 *            アーティストソートキー（nullable）
 * @param tunes
 *            チューン構成（nullable。未指定は構成なしとして扱う。既存の構成は保持されず、この内容へ置き換わる）
 */
public record UpdateTrackInput(
        @Nullable String albumId,
        @Nullable String trackId,
        @Nullable Integer trackNo,
        @Nullable String title,
        @Nullable String artistDisplayName,
        @Nullable String artistSortKey,
        @Nullable List<TrackTuneInput> tunes) implements CommandService.Input {
}
