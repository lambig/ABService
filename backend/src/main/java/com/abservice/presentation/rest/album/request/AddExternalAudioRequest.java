package com.abservice.presentation.rest.album.request;

import org.jspecify.annotations.Nullable;

/**
 * 外部音源追加リクエスト
 *
 * @param url
 *            外部音源の埋め込み元URL（許可ホストのみ。表示順は末尾に採番される）
 */
public record AddExternalAudioRequest(@Nullable String url) {
}
