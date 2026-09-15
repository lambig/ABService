package com.abservice.presentation.rest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("管理セッションの期限・失効・権限境界")
class AdminSessionsTest {

    private static final String API_KEY = "test-session-exchange-key";
    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");
    private static final AuthenticationRequestContext CONTEXT = supplier -> Uni.createFrom().item(supplier.get());

    @Test
    @DisplayName("キーと異なる不透明トークンを発行し管理権限を認める")
    void issuesOpaqueTokenWithFixedExpiration() {
        final var sessions = new AdminSessions(Clock.fixed(NOW, ZoneOffset.UTC));
        final var provider = new ApiKeyIdentityProvider(API_KEY, sessions);
        final var issued = sessions.exchange(authenticate(provider, API_KEY));

        assertThat(issued.token()).matches("abs_session_[0-9a-f]{64}").doesNotContain(API_KEY);
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(authenticate(provider, issued.token()).getRoles()).containsExactly(SecurityRoles.ADMIN);
        assertThat(issued.toString()).doesNotContain(issued.token());
    }

    @Test
    @DisplayName("有効期限直前は利用できるが境界時刻から無効になり利用で延長されない")
    void expiresAtTheBoundaryWithoutSliding() {
        final var clock = new AdjustableClock();
        final var sessions = new AdminSessions(clock);
        final var provider = new ApiKeyIdentityProvider(API_KEY, sessions);
        final var issued = sessions.exchange(authenticate(provider, API_KEY));

        clock.advance(Duration.ofMinutes(30).minusNanos(1));
        assertThat(sessions.authenticatedDigest(issued.token())).isPresent();
        clock.advance(Duration.ofNanos(1));
        assertThatThrownBy(() -> authenticate(provider, issued.token()))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("トークンから新しいトークンを交換できず期限を延長できない")
    void sessionCannotMintAnotherSession() {
        final var sessions = new AdminSessions();
        final var provider = new ApiKeyIdentityProvider(API_KEY, sessions);
        final var issued = sessions.exchange(authenticate(provider, API_KEY));
        final var identity = authenticate(provider, issued.token());

        assertThatThrownBy(() -> sessions.exchange(identity)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("失効したトークンを拒否し別セッションとAPIキーは有効なままにする")
    void revocationAffectsOnlyTheCallingSession() {
        final var sessions = new AdminSessions();
        final var provider = new ApiKeyIdentityProvider(API_KEY, sessions);
        final var keyIdentity = authenticate(provider, API_KEY);
        final var first = sessions.exchange(keyIdentity);
        final var second = sessions.exchange(keyIdentity);

        sessions.revoke(authenticate(provider, first.token()));

        assertThatThrownBy(() -> authenticate(provider, first.token()))
                .isInstanceOf(AuthenticationFailedException.class);
        assertThat(authenticate(provider, second.token()).hasRole(SecurityRoles.ADMIN)).isTrue();
        assertThat(authenticate(provider, API_KEY).hasRole(SecurityRoles.ADMIN)).isTrue();
    }

    @Test
    @DisplayName("セッションを持たないAPIキーによる破棄は拒否する")
    void keyCannotSelectASessionToRevoke() {
        final var sessions = new AdminSessions();
        final var provider = new ApiKeyIdentityProvider(API_KEY, sessions);
        final var identity = authenticate(provider, API_KEY);

        assertThatThrownBy(() -> sessions.revoke(identity)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("キー更新と再起動後は旧キーと旧トークンの双方を拒否する")
    void restartWithRotatedKeyInvalidatesOldCredentials() {
        final var sessions = new AdminSessions();
        final var provider = new ApiKeyIdentityProvider(API_KEY, sessions);
        final var issued = sessions.exchange(authenticate(provider, API_KEY));
        final var restarted = new ApiKeyIdentityProvider("rotated-session-exchange-key", new AdminSessions());

        assertThatThrownBy(() -> authenticate(restarted, API_KEY))
                .isInstanceOf(AuthenticationFailedException.class);
        assertThatThrownBy(() -> authenticate(restarted, issued.token()))
                .isInstanceOf(AuthenticationFailedException.class);
        assertThat(authenticate(restarted, "rotated-session-exchange-key").hasRole(SecurityRoles.ADMIN)).isTrue();
    }

    @Test
    @DisplayName("トークンの末尾変更・途中欠落・未知のトークンを拒否する")
    void rejectsTamperedAndUnknownTokens() {
        final var sessions = new AdminSessions();
        final var provider = new ApiKeyIdentityProvider(API_KEY, sessions);
        final var issued = sessions.exchange(authenticate(provider, API_KEY));

        assertThat(sessions.authenticatedDigest(issued.token() + "0")).isEmpty();
        assertThat(sessions.authenticatedDigest(issued.token().substring(1))).isEmpty();
        assertThat(sessions.authenticatedDigest("abs_session_" + "0".repeat(64))).isEmpty();
        assertThat(sessions.authenticatedDigest(API_KEY)).isEmpty();
    }

    @Test
    @DisplayName("保持数の上限に達したら最古のセッションを失効させ新しいセッションを残す")
    void boundsStorageByExpiringTheOldestSession() {
        final var clock = new AdjustableClock();
        final var sessions = new AdminSessions(clock);
        final var provider = new ApiKeyIdentityProvider(API_KEY, sessions);
        final var identity = authenticate(provider, API_KEY);
        final var oldest = sessions.exchange(identity);
        clock.advance(Duration.ofSeconds(1));
        final var others = Stream.generate(() -> sessions.exchange(identity))
                .limit(AdminSessions.MAX_SESSIONS).toList();

        assertThat(sessions.authenticatedDigest(oldest.token())).isEmpty();
        assertThat(others).allSatisfy(issued -> assertThat(sessions.authenticatedDigest(issued.token())).isPresent());
    }

    @Test
    @DisplayName("期限切れを除去して新規発行しても有効なセッションは残る")
    void discardsExpiredEntriesOnIssue() {
        final var clock = new AdjustableClock();
        final var sessions = new AdminSessions(clock);
        final var provider = new ApiKeyIdentityProvider(API_KEY, sessions);
        final var identity = authenticate(provider, API_KEY);
        final var expired = sessions.exchange(identity);
        clock.advance(Duration.ofMinutes(30));
        final var current = sessions.exchange(identity);

        assertThat(sessions.authenticatedDigest(expired.token())).isEmpty();
        assertThat(sessions.authenticatedDigest(current.token())).isPresent();
    }

    @Test
    @DisplayName("同時発行と失効が競合しても失効済みセッションを復活させない")
    void concurrentIssueDoesNotRestoreRevokedSession() {
        final var sessions = new AdminSessions();
        final var provider = new ApiKeyIdentityProvider(API_KEY, sessions);
        final var keyIdentity = authenticate(provider, API_KEY);
        final var revoked = sessions.exchange(keyIdentity);
        final var revokedIdentity = authenticate(provider, revoked.token());
        final var issues = Stream.generate(() -> CompletableFuture.supplyAsync(() -> sessions.exchange(keyIdentity)))
                .limit(32).toList();
        final var revocation = CompletableFuture.runAsync(() -> sessions.revoke(revokedIdentity));
        final var issued = issues.stream().map(CompletableFuture::join).toList();
        revocation.join();

        assertThat(sessions.authenticatedDigest(revoked.token())).isEmpty();
        assertThat(issued).allSatisfy(session -> assertThat(sessions.authenticatedDigest(session.token())).isPresent());
    }

    private static SecurityIdentity authenticate(ApiKeyIdentityProvider provider, String credential) {
        return provider.authenticate(new ApiKeyAuthenticationRequest(credential), CONTEXT).await().indefinitely();
    }

    private static final class AdjustableClock extends Clock {

        private final AtomicReference<Instant> now = new AtomicReference<>(NOW);

        void advance(Duration elapsed) {
            now.updateAndGet(instant -> instant.plus(elapsed));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
