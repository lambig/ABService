package com.abservice.infrastructure.persistence.datasource;

/**
 * アルバムの外部音源の照会結果1行
 *
 * <p>
 * Query側（CQRS Read）がアルバム本体とは別クエリで外部音源を読むための平坦な投影です。エンティティを返すと
 * 親アルバムが遅延プロキシになり、所属アルバムを知るためにプロキシの初期化が必要になる（Reactiveでは
 * セッション内でも同期初期化できない）ため、所属アルバムの内部IDを列として直接受け取ります。
 * </p>
 *
 * @param albumId
 *            所属アルバムの内部ID
 * @param externalAudioId
 *            外部音源のドメインID（UUIDv7形式の文字列）
 * @param displayOrder
 *            アルバム内での表示順（1, 2, 3, ...）
 * @param url
 *            埋め込み元URL
 */
public record AlbumExternalAudioRow(Long albumId, String externalAudioId, Integer displayOrder, String url) {
}
