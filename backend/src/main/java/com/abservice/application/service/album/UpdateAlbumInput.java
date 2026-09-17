package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * アルバム更新コマンドの入力DTO
 *
 * <p>
 * 外部（REST 等）からの未検証入力を表現します。すべての値は文字列として受け取り、 検証と型への解釈は
 * {@link UpdateAlbumService} が {@code Result} 経由で行います。
 * </p>
 *
 * <p>
 * 曲目（{@code tracks}）と外部音源（{@code externalAudios}）も本体と同じ全項目置換の対象です。作品の子は作品の外に
 * 存在できないため、更新の経路も集約ルートに1つだけ置きます（#391）。送られなかった既存の行は消えます。
 * </p>
 *
 * @param albumId
 *            更新対象のアルバムID
 * @param expectedRevision
 *            編集を始めた時点の世代（必須）。保存の直前に読んだ世代と違えば、その間に別の操作が保存しているため
 *            競合として拒む（#287）。全項目置換のため、これを持たない更新は他の保存を黙って消す
 * @param title
 *            アルバムタイトル（必須・空不可）
 * @param releaseDate
 *            リリース日（ISO-8601形式の文字列。例: "2026-01-01"。必須）
 * @param artistDisplayName
 *            アーティスト表示名（必須・空不可）
 * @param artistSortKey
 *            アーティストソートキー（nullable。未指定の場合は表示名を使用）
 * @param catalogNumber
 *            カタログナンバー（nullable）
 * @param isdn
 *            ISDN（nullable。ハイフンは省略可）
 * @param coverImageKey
 *            カバー画像のアセットキー（nullable。未指定はカバー画像なしへの置換）
 * @param description
 *            作品の概要説明（nullable。未指定は説明なしへの置換）
 * @param descriptionFormat
 *            概要説明のマークアップ形式（{@code com.abservice.domain.model.vo.common.MarkupFormat}
 *            の列挙子名。 {@code description} を指定する場合のみ必須）
 * @param event
 *            初出イベント情報（nullable）
 * @param basePrice
 *            頒布の基準額（nullable。未指定は額が決まっていない状態への置換）
 * @param originalWorkNote
 *            原作の出典の記述（nullable。未指定は記述なしへの置換）
 * @param tracks
 *            曲目（nullable。未指定・空リストは曲目なしへの置換）
 * @param externalAudios
 *            外部音源（nullable。未指定・空リストは音源なしへの置換）
 */
public record UpdateAlbumInput(
        @Nullable String albumId,
        @Nullable Integer expectedRevision,
        @Nullable String title,
        @Nullable String releaseDate,
        @Nullable String artistDisplayName,
        @Nullable String artistSortKey,
        @Nullable String catalogNumber,
        @Nullable String isdn,
        @Nullable String coverImageKey,
        @Nullable String description,
        @Nullable String descriptionFormat,
        @Nullable EventInput event,
        @Nullable BasePriceInput basePrice,
        @Nullable String originalWorkNote,
        @Nullable List<@Nullable TrackInput> tracks,
        @Nullable List<@Nullable ExternalAudioInput> externalAudios) implements CommandService.Input {

    /**
     * 頒布の基準額の入力DTO
     *
     * @param amount
     *            金額（基準額を指定する場合は必須）
     * @param currency
     *            通貨コード（ISO 4217。nullable。未指定は円）
     */
    public record BasePriceInput(
            @Nullable Integer amount,
            @Nullable String currency) {
    }

    /**
     * 初出イベント情報の入力DTO
     *
     * @param name
     *            イベント名（必須・空不可）
     * @param date
     *            開催日（ISO-8601形式の文字列。nullable）
     * @param place
     *            会場（nullable）
     * @param spaceNumber
     *            スペース番号（nullable）
     * @param circleName
     *            頒布サークル名（nullable）
     * @param note
     *            補足情報（nullable）
     */
    public record EventInput(
            @Nullable String name,
            @Nullable String date,
            @Nullable String place,
            @Nullable String spaceNumber,
            @Nullable String circleName,
            @Nullable String note) {
    }
}
