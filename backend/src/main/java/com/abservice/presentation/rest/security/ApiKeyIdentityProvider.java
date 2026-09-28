package com.abservice.presentation.rest.security;

import com.abservice.application.audio.ListeningDeviceToken;
import com.abservice.application.port.ListeningDevice;
import com.abservice.application.port.ListeningDevices;
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
import java.time.Instant;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * APIキー・期限付き管理トークン・端末トークンを照合する IdentityProvider
 *
 * <p>
 * 一致した場合のみ {@link SecurityRoles#ADMIN} または {@link SecurityRoles#LISTENER}
 * ロールを持つ SecurityIdentity を発行し、不一致は {@link AuthenticationFailedException}
 * として失敗させる。APIキーの照合は タイミング攻撃を避けるため {@link MessageDigest#isEqual}
 * による定数時間比較で行う。端末トークンは接頭辞で 見分け、digestをDBで照合する（期限・失効の判定はDB時計）。
 * </p>
 */
@ApplicationScoped
public class ApiKeyIdentityProvider implements IdentityProvider<ApiKeyAuthenticationRequest> {

    /** SecurityIdentity の principal 名（個人利用前提のため管理者ひとりを表す固定名） */
    private static final String ADMIN_PRINCIPAL = "admin";

    /** 端末の principal 名の接頭辞。端末IDで区別する */
    private static final String DEVICE_PRINCIPAL_PREFIX = "device:";

    /** 端末で認証した identity が持つ、端末IDの属性名 */
    static final String LISTENING_DEVICE_ID = "abservice.listening.device-id";

    /** 端末で認証した identity が持つ、資格情報の期限の属性名。端末へ発行する取得URLはこの期限を超えない */
    static final String LISTENING_DEVICE_EXPIRES_AT = "abservice.listening.device-expires-at";

    private final String adminApiKey;
    private final AdminSessions sessions;
    private final ListeningDevices devices;

    /**
     * @param adminApiKey
     *            管理操作に要求するAPIキー（{@code abservice.auth.admin-api-key}）
     * @param sessions
     *            期限付き管理セッション
     * @param devices
     *            試聴端末の資格情報
     */
    public ApiKeyIdentityProvider(
            @ConfigProperty(name = "abservice.auth.admin-api-key") String adminApiKey,
            AdminSessions sessions,
            ListeningDevices devices) {
        this.adminApiKey = adminApiKey;
        this.sessions = sessions;
        this.devices = devices;
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
                : ListeningDeviceToken.isWellFormed(request.apiKey())
                        ? authenticateDevice(request.apiKey())
                        : authenticateSession(request.apiKey());
    }

    private Uni<SecurityIdentity> authenticateDevice(String token) {
        return devices.findActiveByDigest(ListeningDeviceToken.digestOf(token))
                .map(
                        device -> device.map(ApiKeyIdentityProvider::deviceIdentity)
                                .orElseThrow(() -> new AuthenticationFailedException("Invalid credential")));
    }

    private Uni<SecurityIdentity> authenticateSession(String token) {
        return sessions.authenticatedDigest(token)
                .map(ApiKeyIdentityProvider::sessionIdentity)
                .map(Uni.createFrom()::item)
                .orElseGet(() -> Uni.createFrom().failure(new AuthenticationFailedException("Invalid credential")));
    }

    private static SecurityIdentity deviceIdentity(ListeningDevice device) {
        return QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(DEVICE_PRINCIPAL_PREFIX + device.id()))
                .addRole(SecurityRoles.LISTENER)
                .addAttribute(LISTENING_DEVICE_ID, device.id())
                .addAttribute(LISTENING_DEVICE_EXPIRES_AT, device.expiresAt())
                .build();
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

    /** 端末で認証した要求から端末IDを読む。管理者やセッションの要求は空。 */
    static Optional<Object> deviceIdOf(SecurityIdentity identity) {
        return Optional.ofNullable(identity.getAttribute(LISTENING_DEVICE_ID));
    }

    /**
     * 端末で認証した要求から資格情報の期限を読む。
     *
     * @param identity
     *            {@link SecurityRoles#LISTENER} を持つ identity
     * @return 端末トークンの期限
     * @throws IllegalStateException
     *             端末以外の identity（認可で端末に限った経路でだけ呼ぶ）
     */
    public static Instant deviceCredentialExpiresAt(SecurityIdentity identity) {
        return Optional.ofNullable(identity.getAttribute(LISTENING_DEVICE_EXPIRES_AT))
                .filter(Instant.class::isInstance)
                .map(Instant.class::cast)
                .orElseThrow(() -> new IllegalStateException("The request was not authenticated as a device"));
    }
}
