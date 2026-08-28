package com.abservice.presentation.rest.album.response;

/**
 * 公開向け外部音源1件（REST の公開出力契約）
 *
 * <p>
 * 公開サイトの責務は外部音源を表示順に埋め込むことだけのため、埋め込み元URLと表示順だけを返す。外部音源IDは編集対象を
 * 同定するための値であり、項目名自体を持たない。
 * </p>
 *
 * @param displayOrder
 *            アルバム内での表示順（1, 2, 3, ...）
 * @param url
 *            埋め込み元URL
 */
public record PublicExternalAudioResponse(int displayOrder, String url) {
}
