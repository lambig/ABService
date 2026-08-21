package com.abservice.presentation.rest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ApiKeyIdentityProvider（APIキー照合）のテスト")
class ApiKeyIdentityProviderTest {

    private static final String CONFIGURED_KEY = "configured-api-key";

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
    @DisplayName("要求型はAPIキー認証要求である")
    void requestTypeIsApiKeyAuthenticationRequest() {
        assertThat(new ApiKeyIdentityProvider(CONFIGURED_KEY).getRequestType())
                .isEqualTo(ApiKeyAuthenticationRequest.class);
    }

    private static SecurityIdentity authenticate(String presentedKey) {
        return new ApiKeyIdentityProvider(CONFIGURED_KEY)
                .authenticate(
                        new ApiKeyAuthenticationRequest(presentedKey),
                        CONTEXT)
                .await().indefinitely();
    }
}
