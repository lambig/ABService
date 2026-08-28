package com.abservice.presentation.rest.album.response;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 公開向けアルバム詳細レスポンス（REST の公開出力契約）
 *
 * <p>
 * 公開アルバムページは作品の事実詳細をまとめるストック情報であり、概要説明・外部音源・曲目をここで返す。一覧
 * （{@link PublicAlbumResponse}）は作品を選ぶための表示に留めるため、これらの項目名を持たない。
 * </p>
 *
 * <p>
 * アーティストソートキーは並べ替えのための値で、公開サイトは表示にも並びにも使わないため項目名自体を持たない。
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
 *            公開日時（UTC。公開向けは公開中のものだけを返すため常に値を持つ）
 * @param coverImageUrl
 *            カバー画像の配信URL（nullable。サイト相対）
 * @param externalAudios
 *            外部音源（外部サービスの埋め込み元URL）の一覧。表示順の昇順
 * @param tracks
 *            曲目（トラック）の一覧。トラック番号の昇順
 */
public record PublicAlbumDetailResponse(
        String albumId,
        String title,
        String releaseDate,
        String artistDisplayName,
        @Nullable String description,
        String descriptionFormat,
        @Nullable String catalogNumber,
        @Nullable String isdn,
        @Nullable String eventName,
        @Nullable String eventDate,
        @Nullable String eventPlace,
        @Nullable String eventSpaceNumber,
        @Nullable String eventNote,
        Instant publishedAt,
        @Nullable String coverImageUrl,
        List<ExternalAudioResponse> externalAudios,
        List<PublicTrackResponse> tracks) {
}
