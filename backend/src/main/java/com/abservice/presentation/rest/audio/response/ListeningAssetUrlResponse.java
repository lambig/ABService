package com.abservice.presentation.rest.audio.response;

import com.abservice.application.query.audio.ResolveListeningAssetUrlService;
import java.time.Instant;

/**
 * 端末が音源を取得するための期限付きURL。Manifest には載せず、要求ごとに解決する。
 *
 * @param assetId
 *            Manifest の assetId
 * @param url
 *            署名付きの取得URL。期限まで有効で、サーバー側から取り消せない
 * @param expiresAt
 *            URLの有効期限（UTC）。端末の資格情報の期限を超えない
 */
public record ListeningAssetUrlResponse(String assetId, String url, Instant expiresAt) {
    public static ListeningAssetUrlResponse of(ResolveListeningAssetUrlService.Result result) {
        return new ListeningAssetUrlResponse(
                result.assetId().toString(),
                result.download().url(),
                result.download().expiresAt());
    }
}
