package com.abservice.presentation.rest.album.response;

/**
 * 管理向け外部音源1件（REST の公開出力契約）
 *
 * <p>
 * 公開向け（{@link PublicExternalAudioResponse}）との違いは外部音源IDを返すことで、管理画面が削除・並び替えの
 * 対象を同定するために使う。
 * </p>
 *
 * @param externalAudioId
 *            外部音源ID（UUIDv7形式の文字列）
 * @param displayOrder
 *            アルバム内での表示順（1, 2, 3, ...）
 * @param url
 *            埋め込み元URL
 */
public record AdminExternalAudioResponse(String externalAudioId, int displayOrder, String url) {
}
