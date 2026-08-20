package com.abservice.presentation.rest.album.request;

import org.jspecify.annotations.Nullable;

/**
 * トラック追加リクエスト（REST の公開入力契約）
 *
 * <p>
 * 外部からの未検証入力。値検証はアプリケーション層（各値オブジェクトの {@code fromInput}）に委譲する。
 * </p>
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
public record AddTrackRequest(
        @Nullable Integer trackNo,
        @Nullable String title,
        @Nullable String artistDisplayName,
        @Nullable String artistSortKey,
        @Nullable String recordingDate,
        @Nullable String recordingPlace,
        @Nullable Boolean isLive) {
}
