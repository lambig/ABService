package com.abservice.application.query.album.model;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * アルバム詳細の Read Model DTO
 *
 * <p>
 * Query 側（CQRS Read）が {@code infrastructure.persistence.datasource}
 * 経由で取得したアルバムを、
 * 読み取り専用の表現として保持します。ドメインオブジェクトではなく、照会結果をそのまま外部（presentation）へ渡すための平坦な DTO です。
 * </p>
 *
 * <p>
 * トラックは本 DTO には含めません（一覧・詳細ユースケースで拡張します）。外部音源はアルバム本体とは別クエリで 取得した結果を受け取って含めます（複数の
 * {@code @OneToMany} コレクションを1クエリで JOIN FETCH すると Hibernate の multiple-bag-fetch
 * 制約に抵触し、ページング付き一覧では JOIN FETCH 併用でページングが崩れる ため）。初出イベント情報（{@code eventName}
 * 以下）は {@code AlbumTableRecord} 自身の直接カラムのため、 JOINなしで含められます。
 * </p>
 *
 * @param albumId
 *            アルバムID（ドメインID・UUIDv7形式の文字列）
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
 *            公開日時（nullable。null は下書き。UTC の {@link Instant}）
 * @param coverImageUrl
 *            カバー画像の配信URL（nullable。保管キーと配信設定から組み立てた値で、DBに保存されるのはキーのみ）
 * @param externalAudios
 *            外部音源（外部サービスの埋め込み元URL）の一覧。表示順の昇順
 */
public record AlbumView(
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
        List<ExternalAudioView> externalAudios) {

    /**
     * 外部音源1件の Read Model
     *
     * @param externalAudioId
     *            外部音源ID（ドメインID・UUIDv7形式の文字列）
     * @param displayOrder
     *            アルバム内での表示順（1, 2, 3, ...）
     * @param url
     *            埋め込み元URL
     */
    public record ExternalAudioView(String externalAudioId, int displayOrder, String url) {
    }
}
