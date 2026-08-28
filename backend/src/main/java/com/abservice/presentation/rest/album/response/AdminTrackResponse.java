package com.abservice.presentation.rest.album.response;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 管理向けトラック1件（REST の公開出力契約）
 *
 * <p>
 * 編集フォームが扱う項目を持つ。公開向け（{@link PublicTrackResponse}）との違いはアーティストソートキーを返すことで、
 * これは編集者が入力する値のため管理向けにだけ現れる。
 * </p>
 *
 * @param trackId
 *            トラックID（UUIDv7形式の文字列）
 * @param trackNo
 *            アルバム内のトラック番号
 * @param title
 *            トラックタイトル
 * @param artistDisplayName
 *            トラック個別のアーティスト表示名（nullable。null はアルバムの名義を継承）
 * @param artistSortKey
 *            トラック個別のアーティストソートキー（nullable）
 * @param tunes
 *            チューン構成の一覧。登場順の昇順
 */
public record AdminTrackResponse(
        String trackId,
        int trackNo,
        String title,
        @Nullable String artistDisplayName,
        @Nullable String artistSortKey,
        List<TrackTuneResponse> tunes) {
}
