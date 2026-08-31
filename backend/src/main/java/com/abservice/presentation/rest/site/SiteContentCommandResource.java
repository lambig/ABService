package com.abservice.presentation.rest.site;

import com.abservice.application.service.site.UpsertSiteContentInput;
import com.abservice.application.service.site.UpsertSiteContentOutput;
import com.abservice.application.service.site.UpsertSiteContentService;
import com.abservice.presentation.rest.security.SecurityRoles;
import com.abservice.presentation.rest.site.request.UpsertSiteContentRequest;
import com.abservice.presentation.rest.site.response.SiteContentResponse;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * サイト文言の Command REST リソース
 *
 * <p>
 * キー単位の登録・更新（PUT）を受け付ける。同じキーが既にあれば文言を差し替え、無ければ新しく作る（upsert）。
 * 文言は「そのキーの現在の内容」であって履歴を持たないため、登録と更新を別の操作に分けない。
 * </p>
 *
 * <p>
 * 検証失敗は {@code DomainException} 経由で {@code DomainExceptionMapper} が RFC 9457
 * Problem Details に変換する。管理者ロール（{@code Authorization: Bearer <APIキー>}）を要求する。
 * </p>
 */
@Path("/api/v1/site-contents")
@RolesAllowed(SecurityRoles.ADMIN)
public class SiteContentCommandResource {

    private final UpsertSiteContentService upsertSiteContentService;

    /**
     * @param upsertSiteContentService
     *            サイト文言の登録・更新ユースケース
     */
    public SiteContentCommandResource(UpsertSiteContentService upsertSiteContentService) {
        this.upsertSiteContentService = upsertSiteContentService;
    }

    /**
     * サイト文言を登録または更新します。
     *
     * @param key
     *            どの文言かを指すキー
     * @param request
     *            サイト文言の登録・更新リクエスト
     * @return 200 OK と登録・更新後の文言
     */
    @PUT
    @Path("/{key}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> upsert(@PathParam("key") String key, UpsertSiteContentRequest request) {
        return upsertSiteContentService.execute(
                new UpsertSiteContentInput(
                        key,
                        request.content(),
                        request.contentFormat()))
                .map(SiteContentCommandResource::toOk);
    }

    private static Response toOk(UpsertSiteContentOutput output) {
        return Response.ok(toResponse(output)).build();
    }

    private static SiteContentResponse toResponse(UpsertSiteContentOutput output) {
        return new SiteContentResponse(
                output.key(),
                output.content(),
                output.contentFormat());
    }
}
