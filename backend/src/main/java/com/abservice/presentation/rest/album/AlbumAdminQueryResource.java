package com.abservice.presentation.rest.album;

import com.abservice.application.query.Audience;
import com.abservice.application.query.album.GetAlbumQuery;
import com.abservice.application.query.album.GetAlbumResult;
import com.abservice.application.query.album.GetAlbumService;
import com.abservice.application.query.album.ListAlbumsQuery;
import com.abservice.application.query.album.ListAlbumsService;
import com.abservice.presentation.rest.security.SecurityRoles;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * アルバム集約の管理向け Query REST リソース
 *
 * <p>
 * 下書き（未公開）を含めた全アルバムの詳細照会（GET）と一覧照会（GET、ページネーション付き）を受け付ける。管理画面が公開前の
 * アルバムを編集・確認するための経路であり、管理者ロール（{@code Authorization: Bearer <APIキー>}）を要求する。
 * 応答表現は公開向け（{@link AlbumQueryResource}）と同一で、{@code publishedAt} が null のものが下書き。
 * 未存在は例外ではなく {@link GetAlbumResult.NotFound} として扱い、404 を RFC 9457 Problem
 * Details （{@code application/problem+json}）で返す。
 * </p>
 */
@Path("/api/v1/admin/albums")
@RolesAllowed(SecurityRoles.ADMIN)
public class AlbumAdminQueryResource {

    private final GetAlbumService getAlbumService;
    private final ListAlbumsService listAlbumsService;

    /**
     * @param getAlbumService
     *            アルバム詳細照会ユースケース
     * @param listAlbumsService
     *            アルバム一覧照会ユースケース
     */
    public AlbumAdminQueryResource(GetAlbumService getAlbumService, ListAlbumsService listAlbumsService) {
        this.getAlbumService = getAlbumService;
        this.listAlbumsService = listAlbumsService;
    }

    /**
     * 下書きを含むアルバム詳細を照会します。
     *
     * @param id
     *            アルバムのドメインID
     * @return 200 とアルバム詳細、未存在時は 404 の Problem Details
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> get(@PathParam("id") String id) {
        return getAlbumService.query(
                new GetAlbumQuery(
                        id,
                        Audience.ADMIN))
                .map(result -> AlbumQueryResponses.toResponse(result, id));
    }

    /**
     * 下書きを含むアルバム一覧を照会します（ページネーション付き）。
     *
     * @param page
     *            ページ番号（0始まり。デフォルト0）
     * @param size
     *            1ページの件数（デフォルト20、最大100）
     * @return 200 とアルバム一覧
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return listAlbumsService.query(
                new ListAlbumsQuery(
                        page,
                        size,
                        Audience.ADMIN))
                .map(AlbumQueryResponses::toListResponse);
    }
}
