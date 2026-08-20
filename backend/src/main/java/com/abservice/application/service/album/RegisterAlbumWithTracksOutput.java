package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import java.util.List;

/**
 * アルバムとその初期トラック一覧をワンリクエストで登録するコマンドの出力DTO
 *
 * @param albumId
 *            生成されたアルバムのID（UUIDv7形式の文字列）
 * @param title
 *            アルバムタイトル
 * @param releaseDate
 *            リリース日（ISO-8601形式の文字列）
 * @param artistDisplayName
 *            アーティスト表示名
 * @param tracks
 *            登録されたトラックの一覧（トラック番号順）
 */
public record RegisterAlbumWithTracksOutput(
        String albumId,
        String title,
        String releaseDate,
        String artistDisplayName,
        List<TrackSummary> tracks) implements CommandService.Output {

    /**
     * 登録されたトラックの要約
     *
     * @param trackId
     *            トラックID
     * @param trackNo
     *            トラック番号
     * @param title
     *            トラックタイトル
     */
    public record TrackSummary(String trackId, int trackNo, String title) {
    }
}
