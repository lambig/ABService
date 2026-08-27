package com.abservice.presentation.rest.album.request;

import org.jspecify.annotations.Nullable;

/**
 * トラック更新リクエスト（REST の公開入力契約）
 *
 * <p>
 * PUT風の全項目置換（{@code tunes}を除く）。外部からの未検証入力。値検証はアプリケーション層に委譲する。
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
 */
public record UpdateTrackRequest(
        @Nullable Integer trackNo,
        @Nullable String title,
        @Nullable String artistDisplayName,
        @Nullable String artistSortKey) {
}
