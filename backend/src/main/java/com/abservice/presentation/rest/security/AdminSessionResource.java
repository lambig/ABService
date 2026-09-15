package com.abservice.presentation.rest.security;

import com.abservice.presentation.rest.openapi.Executes;
import com.abservice.presentation.rest.security.response.AdminSessionResponse;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;

/** 管理APIキーと期限付きBearerの交換、および呼出元セッションの破棄。 */
@Path("/api/v1/admin/sessions")
@RolesAllowed(SecurityRoles.ADMIN)
public class AdminSessionResource {

    private final AdminSessions sessions;
    private final SecurityIdentity identity;

    /**
     * @param sessions
     *            プロセス内セッション
     * @param identity
     *            認証機構が検証した要求元
     */
    public AdminSessionResource(AdminSessions sessions, SecurityIdentity identity) {
        this.sessions = sessions;
        this.identity = identity;
    }

    /**
     * 元のAPIキーを期限付きトークンへ交換する。
     *
     * @return 保存禁止のトークン応答。トークンからの再交換は403
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Executes(AdminSessions.class)
    public Uni<RestResponse<AdminSessionResponse>> exchange() {
        return Uni.createFrom().item(() -> sessions.exchange(identity))
                .map(issued -> RestResponse.ResponseBuilder.<AdminSessionResponse>create(RestResponse.StatusCode.OK)
                        .entity(issued)
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .header("Pragma", "no-cache")
                        .build());
    }

    /**
     * 自分のセッションをサーバ側で失効させる。
     *
     * @return 204。既に失効したトークンの再提示は認証段階で401
     */
    @DELETE
    @Path("/current")
    @Executes(AdminSessions.class)
    public Uni<RestResponse<Void>> revoke() {
        return Uni.createFrom().voidItem()
                .invoke(() -> sessions.revoke(identity))
                .replaceWith(() -> RestResponse.ResponseBuilder.<Void>create(RestResponse.StatusCode.NO_CONTENT)
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .build());
    }
}
