package com.abservice.presentation.rest.security;

import static io.github.lambig.funcifextension.predicate.Predicates.and;
import static io.github.lambig.funcifextension.predicate.Predicates.or;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * 自分の配信を経由していない要求を、到達の時点で拒む
 *
 * <p>
 * セキュリティグループが許すのは CloudFront 共通の送信元範囲で、そこには他の配信も含まれる。別の配信が 同じオリジンを指せば、こちらの WAF
 * も経路の振り分け（{@code /api/*} だけを backend へ流す）も
 * 経ずに届く。自分の配信だけが付ける値をヘッダで受け取り、一致しない要求を拒む。照合はタイミング攻撃を 避けるため
 * {@link MessageDigest#isEqual} による定数時間比較で行う。
 * </p>
 *
 * <p>
 * 値を設定しない環境（開発・テスト）では検査しない。本番は設定を必須にしており、値が届かなければ 起動そのものが失敗する。
 * </p>
 *
 * <p>
 * 例外は稼働確認の経路（{@code /q/*}）を自分自身から引く場合だけで、これはコンテナの healthcheck が 通る道（DECISIONS
 * 10）。この緩和は、同じコンテナの中から管理エンドポイントへ到達できることを意味する。
 * </p>
 *
 * <p>
 * 拒むときは本文を返さない。経由していない相手へ、何を期待しているかを教えないため。
 * </p>
 */
@ApplicationScoped
public class OriginVerificationFilter {

    /** 認証や CORS より前に判断する。経由していない要求は、何であれ先に断つ */
    private static final int PRIORITY = 1000;

    /** 配信が付ける識別ヘッダ。値そのものは本番の設定から来る */
    private static final String VERIFY_HEADER = "X-Origin-Verify";

    /** 稼働確認・メトリクスの経路。配信は通さないため、外から来ることはない */
    private static final String MANAGEMENT_PREFIX = "/q/";

    private static final Set<String> LOOPBACK_ADDRESSES = Set.of(
            "127.0.0.1",
            "::1",
            "0:0:0:0:0:0:0:1");

    private final Optional<String> expectedToken;

    /**
     * @param expectedToken
     *            自分の配信が付ける値（{@code abservice.origin.verify-token}）。空なら検査しない。本番で
     *            値が届かないことは compose が起動の手前で弾く（DECISIONS 34）
     */
    public OriginVerificationFilter(
            @ConfigProperty(name = "abservice.origin.verify-token") Optional<String> expectedToken) {
        this.expectedToken = expectedToken;
    }

    void register(@Observes Filters filters) {
        filters.register(this::verify, PRIORITY);
    }

    private void verify(RoutingContext context) {
        Optional.of(context)
                .filter(routed -> this.reaching().test(routed.request()))
                .ifPresentOrElse(RoutingContext::next, () -> refuse(context));
    }

    private static void refuse(RoutingContext context) {
        context.response().setStatusCode(Response.Status.FORBIDDEN.getStatusCode()).end();
    }

    /**
     * 検査を設けていない、自分自身の稼働確認である、期待する値を提示している。このいずれかを満たす要求が 先へ進む。
     */
    private Predicate<HttpServerRequest> reaching() {
        return or(
                request -> this.expectedToken.isEmpty(),
                and(OriginVerificationFilter::isManagementPath, OriginVerificationFilter::isFromLoopback),
                this::presentsExpectedToken);
    }

    private static boolean isManagementPath(HttpServerRequest request) {
        return Optional.ofNullable(request.path())
                .filter(path -> path.startsWith(MANAGEMENT_PREFIX))
                .isPresent();
    }

    private static boolean isFromLoopback(HttpServerRequest request) {
        return Optional.ofNullable(request.remoteAddress())
                .map(SocketAddress::hostAddress)
                .filter(LOOPBACK_ADDRESSES::contains)
                .isPresent();
    }

    private boolean presentsExpectedToken(HttpServerRequest request) {
        return Optional.ofNullable(request.getHeader(VERIFY_HEADER))
                .flatMap(presented -> this.expectedToken.filter(expected -> matches(expected, presented)))
                .isPresent();
    }

    private static boolean matches(String expected, String presented) {
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
