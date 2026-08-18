package com.abservice.application.service.albumarticle;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * アルバム記事更新コマンドの入力DTO
 *
 * <p>
 * 外部（REST 等）からの未検証入力を表現します。すべての値は文字列（または数値）として受け取り、 検証と型への解釈は
 * {@link UpdateAlbumArticleService} が {@code Result} 経由で行います。
 * </p>
 *
 * <p>
 * 入手経路（{@code acquisitionChannels}）はUpdate対象外です（専用の
 * {@code addAcquisitionChannel}等で別途行う）。
 * </p>
 *
 * @param albumId
 *            更新対象のアルバム記事ID（対応するAlbum集約のID）
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
public record UpdateAlbumArticleInput(
        @Nullable String albumId,
        @Nullable String introLong,
        @Nullable String introShort,
        @Nullable String firstEventSpace,
        @Nullable String labelTag,
        @Nullable DistributionInput distribution) implements CommandService.Input {

    /**
     * 頒布情報の入力DTO
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
    public record DistributionInput(
            @Nullable Integer physicalPrice,
            @Nullable Integer downloadPrice,
            @Nullable String demoUrl,
            @Nullable String note) {
    }
}
