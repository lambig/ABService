package com.abservice.presentation.rest.album.response;

import com.abservice.presentation.rest.album.response.AlbumResponse.ExternalAudioResponse;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * アルバム詳細レスポンス（REST の公開出力契約）
 *
 * <p>
 * 一覧（{@link AlbumResponse}）との違いは曲目（{@code tracks}）を持つことです。一覧は作品を選ぶための
 * 表示に留め曲目を返さないため、項目名自体を持たせません（`docs/DECISIONS.md` 20）。
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
 * @param coverImageUrl
 *            カバー画像の配信URL（nullable。サイト相対。登録時に渡すのは配信URLではなくアセットキー）
 * @param externalAudios
 *            外部音源（外部サービスの埋め込み元URL）の一覧。表示順の昇順
 * @param tracks
 *            曲目（トラック）の一覧。トラック番号の昇順
 */
public record AlbumDetailResponse(
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
        @Nullable String coverImageUrl,
        List<ExternalAudioResponse> externalAudios,
        List<TrackResponse> tracks) {

    /**
     * トラック1件（REST の公開出力契約）
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
    public record TrackResponse(
            String trackId,
            int trackNo,
            String title,
            @Nullable String artistDisplayName,
            @Nullable String artistSortKey,
            List<TrackTuneResponse> tunes) {
    }

    /**
     * トラック内のチューン構成1件（REST の公開出力契約）
     *
     * <p>
     * チューンIDは返しません。{@code Tune} マスタとの同定を行わないため、常に値を持たない項目になります。
     * </p>
     *
     * @param seq
     *            トラック内での登場順（1, 2, 3, ...）
     * @param tuneTitle
     *            チューン名（nullable）
     * @param composerCreditOverride
     *            作曲者クレジット（nullable）
     * @param arrangerCreditOverride
     *            アレンジャークレジット（nullable）
     * @param linkUrl
     *            リンクURL（nullable）
     */
    public record TrackTuneResponse(
            int seq,
            @Nullable String tuneTitle,
            @Nullable String composerCreditOverride,
            @Nullable String arrangerCreditOverride,
            @Nullable String linkUrl) {
    }
}
