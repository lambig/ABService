package com.abservice.presentation.rest.album.response;

/**
 * 外部音源1件（REST の公開出力契約）
 *
 * <p>
 * 項目の集合は公開向けと管理向けで同じため、要求元で型を分けない。返すのは詳細だけで、一覧は外部音源を持たない。
 * </p>
 *
 * @param externalAudioId
 *            外部音源ID（UUIDv7形式の文字列）
 * @param displayOrder
 *            アルバム内での表示順（1, 2, 3, ...）
 * @param url
 *            埋め込み元URL
 */
public record ExternalAudioResponse(String externalAudioId, int displayOrder, String url) {
}
