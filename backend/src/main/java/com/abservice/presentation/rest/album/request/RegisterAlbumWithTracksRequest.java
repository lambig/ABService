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
 * @param description
 *            作品の概要説明（nullable。空白のみは説明なしとして扱う）
 * @param descriptionFormat
 *            概要説明のマークアップ形式（PLAIN_TEXT / MARKDOWN。{@code description}
 *            を指定する場合のみ必須）
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
public record RegisterAlbumWithTracksRequest(
        @Nullable String title,
        @Nullable String releaseDate,
        @Nullable String artistDisplayName,
        @Nullable String artistSortKey,
        @Nullable String catalogNumber,
        @Nullable String isdn,
        @Nullable String coverImageKey,
        @Nullable String description,
        @Nullable String descriptionFormat,
        @Nullable EventRequest event,
        @Nullable BasePriceRequest basePrice,
        @Nullable String originalWorkNote,
        @Nullable List<@Nullable TrackRequest> tracks,
        @Nullable List<@Nullable ExternalAudioRequest> externalAudios) {

    /**
     * 頒布の基準額のリクエスト契約
     *
     * @param amount
     *            金額（基準額を指定する場合は必須）
     * @param currency
     *            通貨コード（ISO 4217。nullable。未指定は円）
     */
    public record BasePriceRequest(
            @Nullable Integer amount,
            @Nullable String currency) {
    }

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
     * @param circleName
     *            頒布サークル名（nullable。名義と違うことがある）
     * @param note
     *            補足情報（nullable）
     */
    public record EventRequest(
            @Nullable String name,
            @Nullable String date,
            @Nullable String place,
            @Nullable String spaceNumber,
            @Nullable String circleName,
            @Nullable String note) {
    }

}
