package com.abservice.presentation.rest.audio.response;

import java.util.List;

/**
 * 端末資格情報の一覧（発行の新しい順）。
 *
 * @param devices
 *            全端末。トークンは含まない
 */
public record ListeningDeviceListResponse(List<ListeningDeviceResponse> devices) {
}
