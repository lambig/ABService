package com.abservice.presentation.rest.security;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

/**
 * APIキーによる認証要求
 *
 * <p>
 * {@code Authorization: Bearer <APIキー>} から抽出した提示キーを運ぶ。検証は
 * {@link ApiKeyIdentityProvider} が担う。
 * </p>
 */
public class ApiKeyAuthenticationRequest extends BaseAuthenticationRequest {

    private final String apiKey;

    /**
     * @param apiKey
     *            リクエストが提示したAPIキー
     */
    public ApiKeyAuthenticationRequest(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * @return リクエストが提示したAPIキー
     */
    public String apiKey() {
        return apiKey;
    }
}
