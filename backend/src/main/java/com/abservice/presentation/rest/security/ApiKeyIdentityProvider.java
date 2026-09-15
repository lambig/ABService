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
 * APIキーまたは期限付き管理トークンを照合する IdentityProvider
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
    private final AdminSessions sessions;

    /**
     * @param adminApiKey
     *            管理操作に要求するAPIキー（{@code abservice.auth.admin-api-key}）
     * @param sessions
     *            期限付き管理セッション
     */
    public ApiKeyIdentityProvider(
            @ConfigProperty(name = "abservice.auth.admin-api-key") String adminApiKey,
            AdminSessions sessions) {
        this.adminApiKey = adminApiKey;
        this.sessions = sessions;
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
                : authenticateSession(request.apiKey());
    }

    private Uni<SecurityIdentity> authenticateSession(String token) {
        return sessions.authenticatedDigest(token)
                .map(ApiKeyIdentityProvider::sessionIdentity)
                .map(Uni.createFrom()::item)
                .orElseGet(() -> Uni.createFrom().failure(new AuthenticationFailedException("Invalid credential")));
    }

    private static SecurityIdentity sessionIdentity(String digest) {
        return QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(ADMIN_PRINCIPAL))
                .addRole(SecurityRoles.ADMIN)
                .addAttribute(AdminSessions.SESSION_DIGEST, digest)
                .build();
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
                .addAttribute(AdminSessions.API_KEY_IDENTITY, Boolean.TRUE)
                .build();
    }
}
