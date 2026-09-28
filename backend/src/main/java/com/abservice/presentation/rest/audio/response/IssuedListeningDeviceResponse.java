package com.abservice.presentation.rest.audio.response;

/**
 * 発行応答。トークンはここでだけ渡し、以後の照会・一覧には出さない。
 *
 * @param device
 *            発行した端末の資格情報
 * @param token
 *            端末が保持するBearerトークン。管理APIキーではない
 */
public record IssuedListeningDeviceResponse(ListeningDeviceResponse device, String token) {
    @Override
    public String toString() {
        return "IssuedListeningDeviceResponse[device=%s, token=<redacted>]".formatted(device);
    }
}
