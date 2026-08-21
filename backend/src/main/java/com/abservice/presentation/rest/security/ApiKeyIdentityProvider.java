package com.abservice.presentation.rest.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * 設定されたAPIキーと提示キーを照合する IdentityProvider
 *
 * <p>
 * 一致した場合のみ {@link SecurityRoles#ADMIN} ロールを持つ SecurityIdentity を発行し、不一致は
 * {@link AuthenticationFailedException} として失敗させる。照合はタイミング攻撃を避けるため
 * {@link MessageDigest#isEqual} による定数時間比較で行う。
 * </p>
 */
@ApplicationScoped
public class ApiKeyIdentityProvider implements IdentityProvider<ApiKeyAuthenticationRequest> {

    /** SecurityIdentity の principal 名（個人利用前提のため管理者ひとりを表す固定名） */
    private static final String ADMIN_PRINCIPAL = "admin";

    private final String adminApiKey;

    /**
     * @param adminApiKey
     *            管理操作に要求するAPIキー（{@code abservice.auth.admin-api-key}）
     */
    public ApiKeyIdentityProvider(
            @ConfigProperty(name = "abservice.auth.admin-api-key") String adminApiKey) {
        this.adminApiKey = adminApiKey;
    }

    @Override
    public Class<ApiKeyAuthenticationRequest> getRequestType() {
        return ApiKeyAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(
            ApiKeyAuthenticationRequest request,
            AuthenticationRequestContext context) {
        return matchesAdminApiKey(request.apiKey())
                ? Uni.createFrom().item(adminIdentity())
                : Uni.createFrom().failure(new AuthenticationFailedException("Invalid API key"));
    }

    private boolean matchesAdminApiKey(String presented) {
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                adminApiKey.getBytes(StandardCharsets.UTF_8));
    }

    private static SecurityIdentity adminIdentity() {
        return QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(ADMIN_PRINCIPAL))
                .addRole(SecurityRoles.ADMIN)
                .build();
    }
}
