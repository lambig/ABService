package com.abservice.presentation.rest.albumarticle.request;

import org.jspecify.annotations.Nullable;

/**
 * アルバム記事更新リクエスト（REST の公開入力契約）
 *
 * <p>
 * PUT風の全項目置換。外部からの未検証入力。値検証はアプリケーション層（各値オブジェクトの {@code fromInput}）に委譲する。
 * </p>
 *
 * @param introLong
 *            記事本文としての紹介コメント（nullable）
 * @param introShort
 *            お品書き用のショートコメント（nullable）
 * @param firstEventSpace
 *            初出イベントスペース（nullable。例: "東X-00b"）
 * @param labelTag
 *            ラベルタグ（{@code com.abservice.domain.model.vo.album.LabelTag}
 *            の列挙子名。nullable）
 * @param distribution
 *            頒布情報（nullable）
 */
public record UpdateAlbumArticleRequest(
        @Nullable String introLong,
        @Nullable String introShort,
        @Nullable String firstEventSpace,
        @Nullable String labelTag,
        @Nullable DistributionRequest distribution) {

    /**
     * 頒布情報のリクエスト契約
     *
     * @param physicalPrice
     *            物理頒価（円。nullable）
     * @param downloadPrice
     *            DL版価格（円。nullable）
     * @param demoUrl
     *            デモ音源へのリンク（nullable）
     * @param note
     *            補足メモ（nullable）
     */
    public record DistributionRequest(
            @Nullable Integer physicalPrice,
            @Nullable Integer downloadPrice,
            @Nullable String demoUrl,
            @Nullable String note) {
    }
}
