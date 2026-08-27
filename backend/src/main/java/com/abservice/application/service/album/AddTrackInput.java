package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * トラック追加コマンドの入力DTO
 *
 * @param albumId
 *            追加先のアルバムID
 * @param trackNo
 *            トラック番号
 * @param title
 *            トラックタイトル
 * @param artistDisplayName
 *            アーティスト表示名（nullable。未指定時はAlbumのartistCreditを継承）
 * @param artistSortKey
 *            アーティストソートキー（nullable）
 * @param tunes
 *            チューン構成（nullable。未指定は構成なしとして扱う）
 */
public record AddTrackInput(
        @Nullable String albumId,
        @Nullable Integer trackNo,
        @Nullable String title,
        @Nullable String artistDisplayName,
        @Nullable String artistSortKey,
        @Nullable List<TrackTuneInput> tunes) implements CommandService.Input {
}
