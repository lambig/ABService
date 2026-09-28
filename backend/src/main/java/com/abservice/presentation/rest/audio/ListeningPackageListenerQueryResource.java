package com.abservice.presentation.rest.audio;

import com.abservice.application.query.audio.GetListeningPackageService;
import com.abservice.presentation.rest.audio.response.ListeningPackageResponse;
import com.abservice.presentation.rest.openapi.Executes;
import com.abservice.presentation.rest.security.SecurityRoles;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;
import org.jboss.resteasy.reactive.RestResponse;

/**
 * 端末向けの配布パッケージ。端末ロールだけに開き、管理者は使えない。
 *
 * <p>
 * {@code ETag} に packageVersion を載せ、端末は保存済みの版と比べて更新の要否を判断する。応答は保存禁止にする
 * （中継やブラウザのキャッシュに残さず、準備のたびに最新の snapshot を読む）。
 * </p>
 */
@Path("/api/v1/listening/package")
@RolesAllowed(SecurityRoles.LISTENER)
@AllArgsConstructor
public class ListeningPackageListenerQueryResource {
    private final GetListeningPackageService packages;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Executes(GetListeningPackageService.class)
    public Uni<RestResponse<ListeningPackageResponse>> current() {
        return packages.query(new GetListeningPackageService.Query())
                .map(GetListeningPackageService.Result::manifest)
                .map(ListeningPackageResponse::of)
                .map(
                        body -> RestResponse.ResponseBuilder.<ListeningPackageResponse>create(
                                RestResponse.StatusCode.OK)
                                .entity(body)
                                .header(HttpHeaders.ETAG, "\"" + body.packageVersion() + "\"")
                                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                                .build());
    }
}
