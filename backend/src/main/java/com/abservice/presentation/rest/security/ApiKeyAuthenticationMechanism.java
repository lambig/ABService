package com.abservice.presentation.rest.security;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * {@code Authorization: Bearer <APIキー>} からAPIキーを抽出する
 * HttpAuthenticationMechanism
 *
 * <p>
 * ヘッダが無い・スキームが Bearer でない・キーが空のリクエストは匿名として通し（認可は {@code @RolesAllowed}
 * が判定する）、キーがある場合のみ {@link ApiKeyIdentityProvider} で検証する。 将来 OIDC
 * へ移行してもクライアント契約（Bearer トークン）が変わらないようスキームを Bearer に揃える。
 * </p>
 */
@ApplicationScoped
public class ApiKeyAuthenticationMechanism implements HttpAuthenticationMechanism {

    private static final String BEARER_PREFIX = "Bearer ";

    /** 401 応答に付与する認証要求。realm はサービス名固定 */
    private static final String CHALLENGE = "Bearer realm=\"abservice\"";

    @Override
    public Uni<SecurityIdentity> authenticate(
            RoutingContext context,
            IdentityProviderManager identityProviderManager) {
        return presentedApiKey(context)
                .map(ApiKeyAuthenticationRequest::new)
                .map(identityProviderManager::authenticate)
                .orElseGet(() -> Uni.createFrom().nullItem());
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(
                new ChallengeData(
                        Response.Status.UNAUTHORIZED.getStatusCode(),
                        HttpHeaders.WWW_AUTHENTICATE,
                        CHALLENGE));
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of(ApiKeyAuthenticationRequest.class);
    }

    private static Optional<String> presentedApiKey(RoutingContext context) {
        return Optional.ofNullable(context.request().getHeader(HttpHeaders.AUTHORIZATION))
                .filter(header -> header.startsWith(BEARER_PREFIX))
                .map(header -> header.substring(BEARER_PREFIX.length()))
                .filter(StringUtils::isNotBlank);
    }
}
