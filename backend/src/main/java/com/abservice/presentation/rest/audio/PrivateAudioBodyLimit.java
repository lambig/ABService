package com.abservice.presentation.rest.audio;

import com.abservice.application.port.FlacInspectionLimits;
import io.quarkus.vertx.http.runtime.RouteConstants;
import io.quarkus.vertx.http.runtime.VertxHttpRecorder;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.Optional;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** 音源PUTだけに専用上限を適用し、それ以外は従来の10MiB以下に保つ。 */
@ApplicationScoped
public class PrivateAudioBodyLimit {
    private static final long STANDARD_LIMIT = 10L * 1024 * 1024;
    private static final Pattern UPLOAD = Pattern.compile(
            "/api/v1/admin/private-audio/registrations/[0-9a-fA-F-]{36}/content");
    private final String enabled;

    public PrivateAudioBodyLimit(
            @ConfigProperty(name = "abservice.private-audio.enabled", defaultValue = "false") String enabled) {
        this.enabled = enabled;
    }

    void register(@Observes Router router) {
        router.route().order(RouteConstants.ROUTE_ORDER_UPLOAD_LIMIT + 1)
                .handler(this::limit);
    }

    @SuppressWarnings("PMD.ForbiddenUnusedLambdaParameter") // ROUTE-GATE: 上限の選択には経路に加えて機能の有効化状態を使う。
    private void limit(RoutingContext context) {
        final long limit = Optional.of(context.request())
                .filter(request -> "true".equals(enabled))
                .filter(request -> request.method() == HttpMethod.PUT)
                .filter(request -> UPLOAD.matcher(request.path()).matches())
                .map(request -> FlacInspectionLimits.defaults().maxBytes())
                .orElse(STANDARD_LIMIT);
        final long effective = Math.min(
                limit,
                Optional.ofNullable((Long) context.get(VertxHttpRecorder.MAX_REQUEST_SIZE_KEY))
                        .orElse(limit));
        context.put(VertxHttpRecorder.MAX_REQUEST_SIZE_KEY, effective);
        Optional.ofNullable(context.request().getHeader("Content-Length"))
                .map(Long::parseLong)
                .filter(length -> length > effective)
                .ifPresentOrElse(length -> refuse(context), context::next);
    }

    @SuppressWarnings("PMD.ForbiddenUnusedLambdaParameter") // TRANSPORT-CLOSE: 応答の成否によらず過大入力の接続を閉じる。
    private static void refuse(RoutingContext context) {
        Optional.of(context.request().version())
                .filter(version -> version != HttpVersion.HTTP_2)
                .ifPresent(version -> context.response().putHeader("Connection", "close"));
        context.response().setStatusCode(413)
                .end().onComplete(result -> context.request().connection().close());
    }
}
