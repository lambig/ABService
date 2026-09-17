package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * アルバムとその初期トラック一覧をワンリクエストで登録するコマンドの入力DTO
 *
 * <p>
 * 作品は曲目・外部音源ごと1リクエストで登録する。作品の子は作品の外に存在できないため、書く経路は集約ルートに 1つだけ置く（#391）。登録後の変更は
 * {@link UpdateAlbumInput}（全項目置換）が受ける。
 * </p>
 *
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
 *            カバー画像のアセットキー（nullable。アップロード確定APIが返す {@code assetKey}）
 * @param description
 *            作品の概要説明（nullable。空白のみは説明なしとして扱う）
 * @param descriptionFormat
 *            概要説明のマークアップ形式（{@code com.abservice.domain.model.vo.common.MarkupFormat}
 *            の列挙子名。 {@code description} を指定する場合のみ必須）
 * @param event
 *            初出イベント情報（nullable）
 * @param basePrice
 *            頒布の基準額（nullable。未指定は額が決まっていない状態）
 * @param originalWorkNote
 *            原作の出典の記述（nullable。空白のみは記述なしとして扱う）
 * @param tracks
 *            曲目（nullable。未指定・空リストの場合は曲目なしで登録）
 * @param externalAudios
 *            外部音源（nullable。未指定・空リストの場合は音源なしで登録）
 */
public record RegisterAlbumWithTracksInput(
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
