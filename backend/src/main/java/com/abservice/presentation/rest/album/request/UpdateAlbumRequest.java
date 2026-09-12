package com.abservice.presentation.rest.album.request;

import org.jspecify.annotations.Nullable;

/**
 * アルバム更新リクエスト（REST の公開入力契約）
 *
 * <p>
 * PUT風の全項目置換。外部からの未検証入力。値検証はアプリケーション層（各値オブジェクトの {@code fromInput}）に委譲する。
 * </p>
 *
 * @param expectedRevision
 *            編集を始めた時点の世代（必須。管理詳細の {@code revision} をそのまま返す）。保存の直前に読んだ世代と 違えば
 *            409 になる。全項目置換のため、これを持たない更新は編集の間に入った別の保存を消す（#287）
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
 *            カバー画像のアセットキー（nullable。未指定はカバー画像なしへの置換）
 * @param description
 *            作品の概要説明（nullable。未指定は説明なしへの置換）
 * @param descriptionFormat
 *            概要説明のマークアップ形式（PLAIN_TEXT / MARKDOWN。{@code description}
 *            を指定する場合のみ必須）
 * @param event
 *            初出イベント情報（nullable）
 * @param basePrice
 *            頒布の基準額（nullable。未指定は額が決まっていない状態への置換）
 * @param originalWorkNote
 *            原作の出典の記述（nullable。未指定は記述なしへの置換）
 */
public record UpdateAlbumRequest(
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
        @Nullable EventRequest event,
        @Nullable BasePriceRequest basePrice,
        @Nullable String originalWorkNote) {

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
}
