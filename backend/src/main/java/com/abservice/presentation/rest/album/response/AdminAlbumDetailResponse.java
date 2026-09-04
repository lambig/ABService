package com.abservice.presentation.rest.album.response;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 管理向けアルバム詳細レスポンス（REST の公開出力契約）
 *
 * <p>
 * 管理画面の編集フォームが扱う項目をすべて持つ。公開向け（{@link PublicAlbumDetailResponse}）との違いは、
 * 編集者が入力するアーティストソートキーを返すことと、下書きを表す {@code publishedAt} の null を返し得ることである。
 * </p>
 *
 * <p>
 * ROUND-TRIP: 更新要求へ返す値は、要求が受け取る綴りで返す。カバー画像は配信URLではなくアセットキーで受け取るため、
 * 両方を返す。URLからキーを組み立て直すのは配信設定（{@code abservice.assets.public-base-path}）を要求元へ
 * 写すことであり、設定を変えた時点で黙って壊れる。
 * </p>
 *
 * @param albumId
 *            アルバムID（UUIDv7形式の文字列）
 * @param title
 *            アルバムタイトル
 * @param releaseDate
 *            リリース日（ISO-8601形式の文字列）
 * @param artistDisplayName
 *            アーティスト表示名
 * @param artistSortKey
 *            アーティストソートキー（nullable）
 * @param description
 *            作品の概要説明（nullable。null は説明なし）
 * @param descriptionFormat
 *            概要説明のマークアップ形式（列挙子名）
 * @param catalogNumber
 *            カタログナンバー（nullable）
 * @param isdn
 *            ISDN（nullable）
 * @param eventName
 *            初出イベント名（nullable）
 * @param eventDate
 *            初出イベント開催日（ISO-8601形式の文字列。nullable）
 * @param eventPlace
 *            初出イベント会場（nullable）
 * @param eventSpaceNumber
 *            初出イベントスペース番号（nullable）
 * @param eventNote
 *            初出イベント補足情報（nullable）
 * @param publishedAt
 *            公開日時（nullable。null は下書き。UTC）
 * @param coverImageKey
 *            カバー画像のアセットキー（nullable。更新要求（{@code UpdateAlbumRequest#coverImageKey}）へそのまま
 *            返す値。全項目置換のため、画像を変えない保存でもこれを送らないとカバー画像が外れる）
 * @param coverImageUrl
 *            カバー画像の配信URL（nullable。サイト相対。表示に使う。更新要求が受け取るのはこの値ではない）
 * @param externalAudios
 *            外部音源（外部サービスの埋め込み元URL）の一覧。表示順の昇順
 * @param tracks
 *            曲目（トラック）の一覧。トラック番号の昇順
 */
public record AdminAlbumDetailResponse(
        String albumId,
        String title,
        String releaseDate,
        String artistDisplayName,
        @Nullable String artistSortKey,
        @Nullable String description,
        String descriptionFormat,
        @Nullable String catalogNumber,
        @Nullable String isdn,
        @Nullable String eventName,
        @Nullable String eventDate,
        @Nullable String eventPlace,
        @Nullable String eventSpaceNumber,
        @Nullable String eventNote,
        @Nullable Instant publishedAt,
        @Nullable String coverImageKey,
        @Nullable String coverImageUrl,
        List<AdminExternalAudioResponse> externalAudios,
        List<AdminTrackResponse> tracks) {
}
