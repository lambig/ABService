package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * アルバムとその初期トラック一覧をワンリクエストで登録するコマンドの入力DTO
 *
 * <p>
 * 業務上はアルバム登録・トラック追加という2段階の操作だが、ユースケースとしては1リクエストで完結させたい場合に使用する。
 * 登録済みのアルバムへの個別のトラック追加・更新・削除・並び替えは{@code
 * AddTrackService}等（{@code POST /api/v1/albums/{albumId}/tracks}等）で行う。
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
 * @param tracks
 *            初期トラック一覧（nullable。未指定・空リストの場合はトラックなしで登録）
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
        @Nullable List<TrackInput> tracks) implements CommandService.Input {

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
     * @param note
     *            補足情報（nullable）
     */
    public record EventInput(
            @Nullable String name,
            @Nullable String date,
            @Nullable String place,
            @Nullable String spaceNumber,
            @Nullable String note) {
    }

    /**
     * 初期トラックの入力DTO
     *
     * @param trackNo
     *            トラック番号
     * @param title
     *            トラックタイトル
     * @param artistDisplayName
     *            アーティスト表示名（nullable。未指定時はAlbumのartistCreditを継承）
     * @param artistSortKey
     *            アーティストソートキー（nullable）
     * @param tunes
     *            チューン構成（nullable。未指定は構成なしとして扱う）
     */
    public record TrackInput(
            @Nullable Integer trackNo,
            @Nullable String title,
            @Nullable String artistDisplayName,
            @Nullable String artistSortKey,
            @Nullable List<TrackTuneInput> tunes) {
    }
}
