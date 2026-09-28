package com.abservice.presentation.rest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.application.audio.ListeningDeviceToken;
import com.abservice.application.port.ListeningDevice;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ApiKeyIdentityProvider（APIキー・端末トークン照合）のテスト")
class ApiKeyIdentityProviderTest {

    private static final String CONFIGURED_KEY = "configured-api-key";
    private static final ListeningDeviceToken DEVICE_TOKEN = ListeningDeviceToken.issue();
    private static final UUID DEVICE_ID = UUID.randomUUID();

    private static final AuthenticationRequestContext CONTEXT = supplier -> Uni.createFrom()
            .item(supplier.get());

    @Test
    @DisplayName("設定値と一致するキーは管理者ロールを持つidentityを発行する")
    void matchingKeyYieldsAdminIdentity() {
        final var identity = authenticate(CONFIGURED_KEY);

        assertThat(identity.getPrincipal().getName()).isEqualTo("admin");
        assertThat(identity.getRoles()).containsExactly(SecurityRoles.ADMIN);
        assertThat(identity.hasRole(SecurityRoles.ADMIN)).isTrue();
    }

    @Test
    @DisplayName("設定値と一致しないキーは認証失敗にする")
    void mismatchingKeyFails() {
        assertThatThrownBy(() -> authenticate("wrong-api-key"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("前方一致するだけの長いキーは認証失敗にする")
    void prefixOfConfiguredKeyFails() {
        assertThatThrownBy(() -> authenticate(CONFIGURED_KEY + "-extra"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("空のキーは認証失敗にする")
    void emptyKeyFails() {
        assertThatThrownBy(() -> authenticate("")).isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("有効な端末トークンは端末ロールだけを持ち管理者ロールを持たない")
    void activeDeviceTokenYieldsListenerIdentity() {
        final var identity = authenticate(DEVICE_TOKEN.value());

        assertThat(identity.getRoles()).containsExactly(SecurityRoles.LISTENER);
        assertThat(identity.hasRole(SecurityRoles.ADMIN)).isFalse();
        assertThat(identity.getPrincipal().getName()).isEqualTo("device:" + DEVICE_ID);
        assertThat(ApiKeyIdentityProvider.deviceIdOf(identity)).contains(DEVICE_ID);
    }

    @Test
    @DisplayName("digestが一致しない端末トークンは認証失敗にする")
    void unknownDeviceTokenFails() {
        assertThatThrownBy(() -> authenticate(ListeningDeviceToken.issue().value()))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("管理者やセッションのidentityは端末IDを持たない")
    void adminIdentityHasNoDeviceId() {
        assertThat(ApiKeyIdentityProvider.deviceIdOf(authenticate(CONFIGURED_KEY))).isEmpty();
    }

    @Test
    @DisplayName("要求型はAPIキー認証要求である")
    void requestTypeIsApiKeyAuthenticationRequest() {
        assertThat(provider().getRequestType()).isEqualTo(ApiKeyAuthenticationRequest.class);
    }

    private static SecurityIdentity authenticate(String presentedKey) {
        return provider()
                .authenticate(
                        new ApiKeyAuthenticationRequest(presentedKey),
                        CONTEXT)
                .await().indefinitely();
    }

    private static ApiKeyIdentityProvider provider() {
        return new ApiKeyIdentityProvider(
                CONFIGURED_KEY,
                new AdminSessions(),
                new InMemoryListeningDevices(
                        Map.of(
                                DEVICE_TOKEN.digest(),
                                new ListeningDevice(
                                        DEVICE_ID,
                                        "unit",
                                        Instant.EPOCH,
                                        Instant.MAX,
                                        null))));
    }
}
