package com.abservice.presentation.rest.album.response;

import java.util.List;

/**
 * アルバムとその初期トラック一覧のワンリクエスト登録レスポンス（REST の公開出力契約）
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
public record RegisterAlbumWithTracksResponse(
        String albumId,
        String title,
        String releaseDate,
        String artistDisplayName,
        List<TrackSummaryResponse> tracks) {

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
    public record TrackSummaryResponse(String trackId, int trackNo, String title) {
    }
}
