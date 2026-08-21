package com.abservice.presentation.rest.album.request;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * アルバムとその初期トラック一覧のワンリクエスト登録リクエスト（REST の公開入力契約）
 *
 * <p>
 * 外部からの未検証入力。値検証はアプリケーション層に委譲する。
 * </p>
 *
 * @param title
 *            アルバムタイトル
 * @param releaseDate
 *            リリース日（ISO-8601形式の文字列。例: "2026-01-01"）
 * @param artistDisplayName
 *            アーティスト表示名
 * @param artistSortKey
 *            アーティストソートキー（nullable）
 * @param catalogNumber
 *            カタログナンバー（nullable）
 * @param isdn
 *            ISDN（nullable。ハイフンは省略可）
 * @param coverImageKey
 *            カバー画像のアセットキー（nullable。{@code POST
 *            /api/v1/assets/{assetKey}/confirm} が返す {@code assetKey}）
 * @param event
 *            初出イベント情報（nullable）
 * @param tracks
 *            初期トラック一覧（nullable。未指定・空リストの場合はトラックなしで登録）
 */
public record RegisterAlbumWithTracksRequest(
        @Nullable String title,
        @Nullable String releaseDate,
        @Nullable String artistDisplayName,
        @Nullable String artistSortKey,
        @Nullable String catalogNumber,
        @Nullable String isdn,
        @Nullable String coverImageKey,
        @Nullable EventRequest event,
        @Nullable List<TrackRequest> tracks) {

    /**
     * 初出イベント情報のリクエスト契約
     *
     * @param name
     *            イベント名
     * @param date
     *            開催日（ISO-8601形式の文字列。nullable）
     * @param place
     *            会場（nullable）
     * @param spaceNumber
     *            スペース番号（nullable）
     * @param note
     *            補足情報（nullable）
     */
    public record EventRequest(
            @Nullable String name,
            @Nullable String date,
            @Nullable String place,
            @Nullable String spaceNumber,
            @Nullable String note) {
    }

    /**
     * 初期トラックのリクエスト契約
     *
     * @param trackNo
     *            トラック番号
     * @param title
     *            トラックタイトル
     * @param artistDisplayName
     *            アーティスト表示名（nullable。未指定時はAlbumのartistCreditを継承）
     * @param artistSortKey
     *            アーティストソートキー（nullable）
     * @param recordingDate
     *            録音日（ISO-8601形式の文字列。nullable）
     * @param recordingPlace
     *            録音場所（nullable）
     * @param isLive
     *            ライブ録音フラグ（nullable）
     */
    public record TrackRequest(
            @Nullable Integer trackNo,
            @Nullable String title,
            @Nullable String artistDisplayName,
            @Nullable String artistSortKey,
            @Nullable String recordingDate,
            @Nullable String recordingPlace,
            @Nullable Boolean isLive) {
    }
}
